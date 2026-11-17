/** The Calm navigation selector's motion and geometry, ported from the Compose library. */
export interface CalmSelectorBar {
  readonly width: number; readonly height: number; readonly inset: number; readonly count: number;
  readonly cornerRadius: number; readonly baseHalfWidth: number; readonly baseHalfHeight: number;
  readonly allowedHalfHeight: number; readonly density: number; readonly slotWidth: number;
  /** The set slot width (GlassTabBarStyle.slotWidth), NaN when the bar is shared out. */
  readonly fixedSlot: number;
  /** With a set slot, the nearest a tab's centre comes to a bar end. */
  readonly endReach: number;
}
export interface CalmSelectorBarOptions {
  width: number; height: number; count: number; inset?: number; pillWidth?: number;
  pillInset?: number; density?: number;
  /** Equal slots of this width centred in the bar, the ends closed up to fit the pill. */
  slotWidth?: number;
}
/** Shader pose uniforms: A, A^-1, (cx, cy, bx, by) bar-local, (spine, radius, scaleX, band). */
export interface CalmSelectorPose {
  a: [number, number, number, number]; aInv: [number, number, number, number];
  c: [number, number, number, number]; d: [number, number, number, number];
}
export interface CalmSelectorExtents { left: number; right: number; top: number; bottom: number; width: number; height: number }
export interface CalmSelectorState {
  centreX: number; centreY: number; extents: CalmSelectorExtents; formation: number;
  /** The bar's touch lift amount, 0 to 1 (GlassTabBarStyle.touchLift). */
  touch: number;
  transitAppearance: number; heldTravel: number; held: boolean; protrusion: number; pose: CalmSelectorPose;
  /** The tap in transit formed the selector past half and has not settled (Compose's tapFormed, #70): pass it to the
   * selector material as `formedTap`. */
  tapFormed: boolean;
  /** How much of a press transform the host applies to the bar its items should share, 0 to 1: 0 on a
   * tap, rising to 1 as a hold forms the lens (Compose's GlassTabBarStyle.itemsFollowBarPress = false). */
  itemFollow: number;
}
export type CalmSelectorSpec = Readonly<Record<string, number | boolean>>;
export const CALM_SELECTOR_SPEC: CalmSelectorSpec;
export function calmSelectorBar(options: CalmSelectorBarOptions): CalmSelectorBar;
/** A tab's centre on the bar, in bar-local pixels. */
export function centreOf(bar: CalmSelectorBar, index: number): number;
/** The tab a bar-local x falls on. */
export function nearestTab(bar: CalmSelectorBar, x: number): number;
export class CalmSelectorController {
  constructor(spec?: Partial<CalmSelectorSpec>);
  motionEnabled: boolean;
  readonly mode: 'rest' | 'tap' | 'pending' | 'held' | 'released';
  readonly formation: number; readonly transitAppearance: number; readonly heldTravel: number; readonly tapFormed: boolean;
  readonly isHeld: boolean; readonly isIdle: boolean; readonly centreX: number; readonly centreY: number;
  attach(bar: CalmSelectorBar, itemBounds?: ArrayLike<number> | null, itemCentres?: ArrayLike<number> | null, inkHalfHeight?: number): void;
  snapToRest(index: number): void;
  retarget(index: number): void;
  pointerDown(x: number, y: number, seconds: number, eligible: boolean): void;
  pointerMove(x: number, y: number, seconds: number): void;
  beginDrag(x: number, y: number): void;
  pointerUp(restIndex: number): void;
  cancel(restIndex: number): void;
  advanceFrameTo(seconds: number): void;
  extents(): CalmSelectorExtents;
  protrusion(): number;
  render(originX?: number, originY?: number): CalmSelectorPose;
}
export interface CalmNavigationOptions {
  bar: CalmSelectorBar; selected?: number; onSelect?: (index: number) => void;
  spec?: Partial<CalmSelectorSpec>; touchSlop?: number; flingVelocity?: number; motion?: boolean;
}
export interface CalmNavigation {
  readonly controller: CalmSelectorController;
  readonly selected: number;
  select(index: number): void;
  setMotion(enabled: boolean): void;
  /** Bar-local px and monotonic seconds. */
  down(x: number, y: number, seconds: number): void;
  move(x: number, y: number, seconds: number): void;
  up(x: number, y: number, seconds: number): void;
  cancel(seconds: number): void;
  /** True while another frame is needed. */
  frame(seconds: number): boolean;
  state(originX?: number, originY?: number): CalmSelectorState;
}
export function createCalmNavigation(options: CalmNavigationOptions): CalmNavigation;
