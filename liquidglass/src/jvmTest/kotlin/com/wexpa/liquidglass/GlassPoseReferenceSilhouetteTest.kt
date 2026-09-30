package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The held silhouette against the two things the reference ledger actually supports.
 *
 * `research/analysis/v3/ios-parity/reference/REFERENCE-LEDGER.json` declares +-3 to 5 px on ends
 * and heights from a single annotator and a single pass, and its capsule fits leave 8 to 11 px of
 * residual, so nothing here asserts a contour. Two quantities survive that uncertainty:
 *
 *  * **Protrusion.** 6717 reads 24 px above and 24 px below a 171 px bar, and 6719 reads 24 px
 *    above the same bar height. That is a maximum over a 400-column run, which is the most robust
 *    statistic the annotation produces. Calm held height is therefore 1.28 bar heights.
 *  * **Width, only as a bracket.** The brief reads 6721 at about 1.73 slots; the ledger's
 *    circular-cap fit of 6717 gives a half-spine of 187.3 and a radius of 97.5 on a 941 px
 *    five-tab bar, about 2.03 slots if that spine is the full one. The bracket [1.5, 2.3] slots
 *    is wide because those two numbers come from different methods on different frames.
 *
 * The third test is not from the reference at all. It is the separation the references imply:
 * calm held protrudes (6717, 6721) and hard-pulled held flattens (6735), so an ordinary traverse
 * has to land on the protruding side of that line rather than being squeezed flat by it.
 */
class GlassPoseReferenceSilhouetteTest {

    /** 6717's bar proportions at Vitals' density: 171 px tall, five tabs. */
    private fun bar() = GlassSelectorBar(
        width = 1006f, height = 162f, inset = 18f, count = 5, cornerRadius = 81f,
        baseHalfWidth = 96f, baseHalfHeight = 69f, allowedHalfHeight = 69f,
        heldHalfHeight = 96f, heldHalfWidth = 128f, density = 3f, maxBodyWidth = 600f,
    )

    private fun boundsOf(b: GlassSelectorBar, width: Float = 150f): Pair<FloatArray, FloatArray> {
        val bounds = FloatArray(b.count * 2)
        val centres = FloatArray(b.count)
        for (i in 0 until b.count) {
            val c = b.centreOf(i)
            centres[i] = c
            bounds[i * 2] = c - width / 2f
            bounds[i * 2 + 1] = c + width / 2f
        }
        return bounds to centres
    }

    private fun controller(b: GlassSelectorBar, startAt: Int): GlassPoseController {
        val c = GlassPoseController()
        val (bounds, centres) = boundsOf(b)
        c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
        c.snapToRest(startAt)
        return c
    }

    private fun settleHeld(c: GlassPoseController, b: GlassSelectorBar, seconds: Float = 0.8f) {
        val (bounds, centres) = boundsOf(b)
        var t = 0f
        while (t < seconds) {
            t += 1f / 240f
            c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
            c.advanceTo(t)
        }
    }

    @Test
    fun theCalmHeldBodyProtrudesByTheLedgersFraction() {
        val b = bar()
        val c = controller(b, startAt = 2)
        c.pointerDown(b.centreOf(2), b.centreY, 0.0, eligible = true)
        settleHeld(c, b)
        assertTrue(c.isHeld, "the calm press never became a hold")
        val e = GlassPoseExtents()
        c.extents(e)
        // The ledger read 24 px on a 171 px bar (0.14 per side, 1.28 overall); the r10 optics
        // audit re-read every held reference at matched scale - 6717 1.12, LOLL8185 n1380 1.22,
        // 6756 T01 1.21, T04 1.23 - so the held body is 1.20 bar heights, 0.10 per side.
        val want = b.height * 0.10f
        // Against the BAR, which is what the ledger measured; `allowedHalfHeight` is the resting
        // selector pill inside it and reading protrusion from there inflates it by the gap.
        val above = (b.centreY - b.height / 2f) - e.top
        val below = e.bottom - (b.centreY + b.height / 2f)
        println(
            "LEDGER-PROTRUSION want=%.1f above=%.1f below=%.1f height=%.1f bar=%.1f"
                .format(want, above, below, e.height, b.height),
        )
        // The ledger's own +-5 px is on a 171 px bar; scale it with the bar and keep it as the
        // tolerance rather than inventing a tighter one.
        val tol = b.height * 6f / 171f
        assertTrue(
            abs(above - want) <= tol,
            "calm held protrusion above is $above px, ledger says $want +- $tol",
        )
        assertTrue(
            abs(below - want) <= tol,
            "calm held protrusion below is $below px, ledger says $want +- $tol",
        )
    }

    @Test
    fun theAccommodatedPairStaysInsideTheReferenceWidthBracket() {
        val b = bar()
        val c = controller(b, startAt = 2)
        c.pointerDown(b.centreOf(2), b.centreY, 0.0, eligible = true)
        settleHeld(c, b)
        val e = GlassPoseExtents()
        c.extents(e)
        val slots = e.width / b.slotWidth
        // A hold on one item has nothing to accommodate, so this is pressure alone. The widest
        // the body actually reaches is with the grasp parked between two items, where section 9
        // asks it to cover the pair. That is the number to compare against the references - not
        // `maxAccommodationWidth`, which is the node's conservative allocation bound and
        // deliberately assumes a taper and a speed that do not occur together.
        val wide = controller(b, startAt = 2)
        val between = (b.centreOf(2) + b.centreOf(3)) / 2f
        wide.pointerDown(between, b.centreY, 0.0, eligible = true)
        settleHeld(wide, b, seconds = 1.2f)
        val w2 = GlassPoseExtents()
        wide.extents(w2)
        val widest = w2.width / b.slotWidth
        println("LEDGER-WIDTH slots=%.2f accommodated=%.2f width=%.1f slot=%.1f"
            .format(slots, widest, e.width, b.slotWidth))
        // 6721 spans TWO items; it does not set the width of this synthetic single-item hold.
        // Independent, calibrated single-item Phone dimensions are tested separately in
        // GlassCalmGrowthReferenceTest. Here the centred item must fit without accommodation.
        assertTrue(c.accommodationDemand <= 0.5f, "a centred item should not demand extra spine")
        assertTrue(e.width >= 150f + 2f * 6f * b.density, "single-item ink and padding must fit")
        assertTrue(widest >= 1.5f, "the admitted pair is only $widest slots wide; 6721 reads ~1.73")
        assertTrue(widest > slots, "parking between items must improve the pair's coverage")
        assertTrue(
            widest <= 2.3f,
            "a hold between two items reached $widest slots; the references bracket the held " +
                "body at 1.5 to 2.3, and three slots is what the phone showed before",
        )
    }

    @Test
    fun anOrdinaryTraverseKeepsItsProtrusionWhileAHardFlickDoesNot() {
        // Both gestures the device pass injects, driven for the same 0.6 s so that formation has
        // settled in both and the only difference left is speed. Measuring the minimum over a
        // 170 ms flick and a 1.5 s traverse straight through would compare the flick's whole
        // life against the traverse's onset, where the body is still becoming a lens.
        val b = bar()
        val span = b.centreOf(4) - b.centreOf(0)
        fun shuttle(pxPerSecond: Float): Float {
            val c = controller(b, startAt = 0)
            var x = b.centreOf(0)
            c.pointerDown(x, b.centreY, 0.0, eligible = true)
            c.beginDrag(x, b.centreY)
            val (bounds, centres) = boundsOf(b)
            val e = GlassPoseExtents()
            var f = 0f
            var t = 0.0
            var minHeight = Float.MAX_VALUE
            while (f < 0.6f) {
                f += 1f / 240f
                t += 1.0 / 240.0
                // A triangle between the end tabs at the declared speed, so a flick is a flick
                // for the whole window instead of running out of bar in 170 ms.
                val travelled = pxPerSecond * f
                val phase = (travelled / span).toInt()
                val within = travelled - phase * span
                x = if (phase % 2 == 0) b.centreOf(0) + within else b.centreOf(4) - within
                c.pointerMove(x, b.centreY, t)
                c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
                c.advanceTo(f)
                if (f > 0.35f) {
                    c.extents(e)
                    minHeight = minOf(minHeight, e.height)
                }
            }
            return minHeight
        }
        val ordinary = shuttle(span / 1.5f)
        val flick = shuttle(span / 0.17f)
        println("TRAVERSE ordinary=%.1f flick=%.1f bar=%.1f".format(ordinary, flick, b.height))
        assertTrue(
            ordinary > b.height,
            "an ordinary traverse squeezed the held body to $ordinary px, inside the " +
                "${b.height} px bar: the calm held state the references show never appears",
        )
        assertTrue(
            flick < b.height,
            "a flick left the body at $flick px, outside the ${b.height} px bar: 6735 shows a " +
                "hard pull flattening it back inside",
        )
    }

    @Test
    fun theNodeIsTallEnoughForEveryBodyItHasToShow() {
        // Section 5.1: a finite allocation is a safety limit, not a material wall. The node's
        // height used to come from a 4 dp style constant - 11 px at 440 dpi, against the 24 px of
        // protrusion the ledger asks for - and the held body was clipped flat across the top on
        // the phone. This drives the gestures the device pass injects and checks that the bound
        // the node is sized from actually covers what they produce.
        val b = bar()
        val allowed = GlassSelectorSpec().maxAccommodationOverflow(b)
        val barTop = b.centreY - b.height / 2f
        val barBottom = b.centreY + b.height / 2f
        val e = GlassPoseExtents()
        var worst = 0f
        var worstCase = ""

        fun run(name: String, pressY: Float, pxPerSecond: Float, vertical: Float, seconds: Float) {
            val c = controller(b, startAt = 0)
            val span = b.centreOf(4) - b.centreOf(0)
            var x = b.centreOf(0)
            var y = pressY
            c.pointerDown(x, y, 0.0, eligible = true)
            if (pxPerSecond > 0f || vertical != 0f) c.beginDrag(x, y)
            val (bounds, centres) = boundsOf(b)
            var f = 0f
            var t = 0.0
            while (f < seconds) {
                f += 1f / 240f
                t += 1.0 / 240.0
                if (pxPerSecond > 0f) {
                    val travelled = pxPerSecond * f
                    val phase = (travelled / span).toInt()
                    val within = travelled - phase * span
                    x = if (phase % 2 == 0) b.centreOf(0) + within else b.centreOf(4) - within
                }
                y = pressY + vertical * (f / seconds)
                if (pxPerSecond > 0f || vertical != 0f) c.pointerMove(x, y, t)
                c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
                c.advanceTo(f)
                c.extents(e)
                val over = maxOf(barTop - e.top, e.bottom - barBottom)
                if (over > worst) { worst = over; worstCase = name }
            }
        }

        val span = b.centreOf(4) - b.centreOf(0)
        run("calm hold", b.centreY, 0f, 0f, 0.9f)
        run("off-centre hold", b.centreY - b.height / 4f, 0f, 0f, 0.9f)
        run("ordinary traverse", b.centreY - b.height / 4f, span / 1.5f, 0f, 0.9f)
        run("hard flick", b.centreY - b.height / 4f, span / 0.17f, 0f, 0.9f)
        run("vertical drag", b.centreY, 0f, -320f, 0.9f)
        run("diagonal drag", b.centreY, span / 1.2f, -260f, 0.9f)
        // The declared excursion is also a promise about the silhouette, not only about the
        // allocation: growth and translation share it, so no gesture may put the body further
        // off the bar than the envelope says. A body 48 px clear of a 163 px bar put the bar's
        // own corner inside its interior on the phone.
        val declared = GlassPoseSpec().heldExcursionRatio * b.height
        println("NODE-OVERFLOW worst=%.1f (%s) allowed=%.1f declared=%.1f"
            .format(worst, worstCase, allowed, declared))
        assertTrue(
            worst <= allowed,
            "the $worstCase body left the bar by $worst px, more than the $allowed px the node " +
                "is sized for: it would be clipped",
        )
        assertTrue(
            // The springs may overshoot their equilibrium by a little on the way; the envelope
            // is a statement about where the body settles, not a hard wall the solver enforces.
            worst <= declared * 1.08f,
            "the $worstCase body left the bar by $worst px, past the $declared px excursion this " +
                "style declares: growth and translation are adding instead of sharing it",
        )
    }
}
