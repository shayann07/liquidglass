/** Apache-2.0. Record a navigation gesture once, then ask for the bar's state at any time, in any order. No CanvasKit. */
import {boundsFor, viewport} from './bounds.mjs';
import {createNavigationDriver} from './navigation.mjs';

const EVENTS = new Set(['down', 'move', 'up', 'cancel', 'select']);
// The stored state: numeric columns that interpolate, and decisions that snap to the earlier step.
const LERP = [
  'selector.centreX', 'selector.centreY', 'selector.formation', 'selector.transitAppearance', 'selector.touch',
  'selector.heldTravel', 'selector.protrusion', 'selector.itemFollow', 'selector.holdLook', 'selector.holdRim',
  'extents.left', 'extents.right', 'extents.top', 'extents.bottom', 'extents.width', 'extents.height',
  ...['a', 'c', 'd', 'e'].flatMap(k => [0, 1, 2, 3].map(i => `pose.${k}.${i}`)),
  'feedback.matrix.0', 'feedback.matrix.1', 'feedback.matrix.2', 'feedback.matrix.3',
  'feedback.offset.0', 'feedback.offset.1', 'feedback.press',
];
const SNAP = ['selector.tapFormed', 'selector.holdFormed', 'selector.holdReleasing', 'selector.held', 'feedback.held', 'feedback.active', 'active', 'selected'];
const FIELDS = [...LERP, ...SNAP];
const INDEX = new Map(FIELDS.map((name, i) => [name, i]));
const A = [0, 1, 2, 3].map(i => INDEX.get(`pose.a.${i}`));
// aInv is stored too, but never interpolated: it is recomputed from the interpolated a.
const STORED = [...FIELDS, ...[0, 1, 2, 3].map(i => `pose.aInv.${i}`)];
const COLUMN = new Map(STORED.map((name, i) => [name, i]));
const AINV = [0, 1, 2, 3].map(i => COLUMN.get(`pose.aInv.${i}`));
const isSnap = new Set(SNAP);

function encode(state) {
  const s = state.selector, f = state.feedback, p = s.pose, e = s.extents;
  const read = {
    'selector.centreX': s.centreX, 'selector.centreY': s.centreY, 'selector.formation': s.formation,
    'selector.transitAppearance': s.transitAppearance, 'selector.touch': s.touch ?? 0, 'selector.heldTravel': s.heldTravel,
    'selector.protrusion': s.protrusion, 'selector.itemFollow': s.itemFollow,
    'selector.holdLook': s.holdLook, 'selector.holdRim': s.holdRim,
    'selector.holdFormed': s.holdFormed ? 1 : 0, 'selector.holdReleasing': s.holdReleasing ? 1 : 0,
    'extents.left': e.left, 'extents.right': e.right, 'extents.top': e.top, 'extents.bottom': e.bottom,
    'extents.width': e.width, 'extents.height': e.height,
    'feedback.press': f.press, 'selector.tapFormed': s.tapFormed ? 1 : 0, 'selector.held': s.held ? 1 : 0,
    'feedback.held': f.held ? 1 : 0, 'feedback.active': f.active ? 1 : 0, active: state.active ? 1 : 0, selected: state.selected,
  };
  for (let i = 0; i < 4; i++) {
    for (const k of ['a', 'c', 'd', 'e', 'aInv']) read[`pose.${k}.${i}`] = p[k][i];
    read[`feedback.matrix.${i}`] = f.matrix[i];
  }
  read['feedback.offset.0'] = f.offset[0]; read['feedback.offset.1'] = f.offset[1];
  return STORED.map(name => read[name]);
}

function decode(row) {
  const v = name => row[COLUMN.get(name)], quad = k => [0, 1, 2, 3].map(i => v(`pose.${k}.${i}`));
  return {
    selector: {
      centreX: v('selector.centreX'), centreY: v('selector.centreY'),
      extents: {left: v('extents.left'), right: v('extents.right'), top: v('extents.top'), bottom: v('extents.bottom'),
        width: v('extents.width'), height: v('extents.height')},
      formation: v('selector.formation'), transitAppearance: v('selector.transitAppearance'), tapFormed: !!v('selector.tapFormed'),
      touch: v('selector.touch'), heldTravel: v('selector.heldTravel'), held: !!v('selector.held'), protrusion: v('selector.protrusion'),
      itemFollow: v('selector.itemFollow'),
      holdFormed: !!v('selector.holdFormed'), holdReleasing: !!v('selector.holdReleasing'),
      holdLook: v('selector.holdLook'), holdRim: v('selector.holdRim'),
      pose: {a: quad('a'), aInv: quad('aInv'), c: quad('c'), d: quad('d'), e: quad('e')},
    },
    feedback: {matrix: [0, 1, 2, 3].map(i => v(`feedback.matrix.${i}`)), offset: [v('feedback.offset.0'), v('feedback.offset.1')],
      press: v('feedback.press'), held: !!v('feedback.held'), active: !!v('feedback.active')},
    selected: v('selected'), active: !!v('active'),
  };
}

function invert([a, b, c, d]) {
  const det = a * d - b * c;
  return [d / det, -b / det, -c / det, a / det];
}

/** A baked navigation: `sample(t)` is the bar's state at any time t (seconds), whatever order it is asked in. */
function makeTrack({step, duration, columns}) {
  const length = columns[0].length;
  const row = k => columns.map(column => column[k]);
  const at = t => {
    if (!Number.isFinite(t)) throw new RangeError('Sample time must be finite');
    const u = Math.min(Math.max(t / step, 0), length - 1), nearest = Math.round(u);
    // An exact step returns the stored state unchanged; k / step may land a rounding error off the step.
    if (Math.abs(u - nearest) < 1e-9) return decode(row(nearest));
    const i = Math.floor(u), w = u - i, a = row(i), b = row(i + 1);
    const out = a.map((x, c) => isSnap.has(STORED[c]) || STORED[c].startsWith('pose.aInv') ? x : x + (b[c] - x) * w);
    const inverse = invert(A.map(c => out[c]));
    AINV.forEach((c, k) => { out[c] = inverse[k]; });
    return decode(out);
  };
  return {
    step, duration, length,
    sample: at,
    /** Plain data (numbers and arrays): hand it to a worker, or store it, and rebuild with `navigationTrack`. */
    toJSON: () => ({version: 2, step, duration, columns: columns.map(column => Array.from(column))}),
  };
}

// Version 1 tracks (before #103) lack the four hold columns. What the old code drew is no hold at all: the fill followed the
// formation (holdLook = formation, as Compose's default), the lens was never hold-formed or releasing, so the rim scale never applied.
const V1_ADDED = new Set(['selector.holdLook', 'selector.holdRim', 'selector.holdFormed', 'selector.holdReleasing']);
const V1_STORED = STORED.filter(name => !V1_ADDED.has(name));
function upgradeV1(columns) {
  const old = new Map(V1_STORED.map((name, i) => [name, columns[i]]));
  const formation = old.get('selector.formation');
  return STORED.map(name => V1_ADDED.has(name)
    ? (name === 'selector.holdLook' || name === 'selector.holdRim' ? formation : formation.map(() => 0))
    : old.get(name));
}

/** The stored column names in order (for tests; not exported by the package). */
export const TRACK_COLUMNS = STORED;

/** Rebuild a track from `track.toJSON()`; tracks saved at version 1 (before the hold terms) still load and play as they did. */
export function navigationTrack(json) {
  let {version, step, duration, columns} = json ?? {};
  if (version === 1 && Array.isArray(columns) && columns.length === V1_STORED.length && columns.every(c => Array.isArray(c))) {
    columns = upgradeV1(columns); version = 2;
  }
  if (version !== 2 || !(step > 0) || !Number.isFinite(duration) || !Array.isArray(columns) || columns.length !== STORED.length ||
      !columns.every(c => Array.isArray(c) && c.length === columns[0].length && c.length >= 1 && c.every(Number.isFinite)))
    throw new TypeError('Not a navigation track');
  return makeTrack({step, duration, columns: columns.map(c => Float64Array.from(c))});
}

/**
 * Run a scripted gesture through the scene's own navigation motion at a fixed step and keep every step's state.
 * `bar` is `addNavigation`'s bounds and options, plus `viewport`; `events` are `{type: 'down' | 'move' | 'up' | 'cancel' | 'select',
 * x, y, time, index}` in logical page px and seconds. The motion steps, never jumps: a live run at another frame cadence
 * differs slightly, as any two live runs do.
 */
export function bakeNavigation(bar, events, {step = 1 / 240, duration} = {}) {
  if (!(step > 0) || !Number.isFinite(step) || !(duration >= 0) || !Number.isFinite(duration))
    throw new RangeError('Positive step and nonnegative duration required');
  const length = Math.ceil(duration / step - 1e-9) + 1;
  if (length > 5e6) throw new RangeError('Too many steps: raise the step or shorten the duration');
  const {bounds: input, count, selected = 0, inset = 6, pillWidth, pillInset = 4, reducedMotion = false} = bar ?? {};
  const view = viewport({pixelRatio: 1, ...bar?.viewport});
  const bounds = boundsFor('surface', {...input, radius: input?.height / 2});
  if (!Number.isInteger(count) || count < 1) throw new RangeError('Count must be a positive integer');
  let last = 0;
  const script = (events ?? []).map(e => {
    if (!e || !EVENTS.has(e.type)) throw new TypeError(`Unknown event type: ${e?.type}`);
    if (!Number.isFinite(e.time) || e.time < 0 || e.time < last) throw new RangeError('Event times must be finite, nonnegative and nondecreasing');
    last = e.time;
    const pointer = e.type === 'down' || e.type === 'move' || e.type === 'up';
    if (pointer && !(Number.isFinite(e.x) && Number.isFinite(e.y))) throw new RangeError(`${e.type} needs finite x and y`);
    if (e.type === 'select' && !Number.isInteger(e.index)) throw new RangeError('select needs an integer index');
    return {...e};
  });
  // A bake has no host: the selection a gesture commits is applied as the scene's onSelect handler would.
  let driver;
  driver = createNavigationDriver({bounds, view, count, selected, inset, pillWidth, pillInset, reducedMotion, onSelect: i => driver.nav.select(i)});
  const columns = STORED.map(() => new Float64Array(length));
  let feedback = driver.initial, next = 0;
  for (let k = 0; k < length; k++) {
    const t = k * step;
    for (; next < script.length && script[next].time <= t; next++) {
      const e = script[next];
      if (e.type === 'down') feedback = driver.down(e.x, e.y, e.time);
      else if (e.type === 'move') feedback = driver.move(e.x, e.y, e.time) ?? feedback;
      else if (e.type === 'up') feedback = driver.up(e.x, e.y, e.time);
      else if (e.type === 'cancel') feedback = driver.cancel(e.time);
      else driver.nav.select(e.index);
    }
    const frame = driver.advance(t);
    const row = encode({selector: driver.nav.state(), feedback: frame.feedback, selected: driver.nav.selected, active: frame.active});
    row.forEach((value, c) => { columns[c][k] = value; });
  }
  return makeTrack({step, duration, columns});
}
