import type {CalmSelectorState} from './selector.mjs';
import type {GlassFeedback, GlassRect} from './index.mjs';

/** A scripted pointer or selection event: logical page px and seconds, as the scene's navigation layer takes them. */
export type NavigationEvent =
  | {type: 'down' | 'move' | 'up'; x: number; y: number; time: number}
  | {type: 'cancel'; time: number}
  /** The host's own selection change; a committed gesture selects by itself, as `onSelect` + `select` would. */
  | {type: 'select'; index: number; time: number};
export interface NavigationBar {
  bounds: GlassRect; count: number; selected?: number; inset?: number; pillWidth?: number; pillInset?: number;
  reducedMotion?: boolean;
  /** The scene's logical viewport (the bar's press feedback is clamped to it). Default pixelRatio 1. */
  viewport: {width: number; height: number; pixelRatio?: number};
}
/** The bar's state at one moment: what `drawNavigation` draws. */
export interface NavigationSample {
  selector: CalmSelectorState; feedback: GlassFeedback; selected: number;
  /** True while the bar was still asking for frames at this step. */
  active: boolean;
}
export interface NavigationTrackData {
  version: 2; step: number; duration: number; columns: number[][];
}
export interface NavigationTrack {
  readonly step: number; readonly duration: number;
  /** The number of stored steps. */
  readonly length: number;
  /** The state at `time` seconds, any time, in any order. Exact at a step; in between, numbers interpolate linearly
   * (`pose.aInv` is recomputed from the interpolated `pose.a`) and decisions (`held`, `tapFormed`, `selected`, `active`)
   * keep the earlier step's value. Before 0 it is the first step, after the last the last. */
  sample(time: number): NavigationSample;
  /** Plain data (numbers and arrays), safe to send to a worker or store. */
  toJSON(): NavigationTrackData;
}
/** Run a scripted gesture through the scene's own navigation motion at a fixed `step` (default 1/240 s) and keep every step. */
export function bakeNavigation(bar: NavigationBar, events: readonly NavigationEvent[], options: {step?: number; duration: number}): NavigationTrack;
/** Rebuild a track from `track.toJSON()`. */
export function navigationTrack(data: NavigationTrackData): NavigationTrack;
