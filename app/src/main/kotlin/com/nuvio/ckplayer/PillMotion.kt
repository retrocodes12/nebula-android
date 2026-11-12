/*
 * The nav pill's physics.
 *
 * The motion model — the travel spring, the held lift, the acceleration squash and the glass hand-off, with their
 * constants — is a Kotlin port of liquid_glass_easy 4.3.4 (LiquidGlassAnimatedNavBar, LiquidGlassLensMotion and
 * liquidGlassSpringStep; pub.dev/packages/liquid_glass_easy).
 *
 * MIT License
 *
 * Copyright (c) 2025 Ahmed Gamil
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 *
 * (The same notice ships in the app: assets/licenses/liquid_glass_easy.txt.)
 */
package com.nuvio.ckplayer

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign

/** One value on a spring, stepped at 240 Hz inside each frame so a long frame cannot blow it up. */
internal class Spring1(var x: Float = 0f) {
    var v = 0f

    fun step(target: Float, dt: Float, stiffness: Float, damping: Float) {
        var t = dt
        while (t > 0f) {
            val h = if (t > SUB) SUB else t
            val a = -stiffness * (x - target) - damping * v
            v += a * h
            x += v * h
            t -= h
        }
    }

    /** Stops on [target] once there is nothing left to see; true when it has. */
    fun rest(target: Float, near: Float, slow: Float): Boolean {
        if (abs(x - target) < near && abs(v) < slow) { x = target; v = 0f; return true }
        return false
    }

    private companion object { const val SUB = 1f / 240f }
}

/**
 * What a moving body's own acceleration does to its shape: width scales by `1 + deviation`, height by
 * `1 - deviation`. Force deforms it, not speed — a launch stretches it, braking squashes it, a steady glide leaves
 * it alone. Fed the body's position (in dp) once a frame.
 */
internal class Squash(
    private val window: Float = 0.3f,
    private val sensitivity: Float = 0.00007f,
    private val max: Float = 0.12f,
    private val response: Float = 0.18f,
) {
    private val ps = FloatArray(CAP)
    private val ts = FloatArray(CAP)
    private var n = 0

    var tracking = false
        private set
    var deviation = 0f
        private set

    fun start() { n = 0; deviation = 0f; tracking = true }
    fun stop() { n = 0; deviation = 0f; tracking = false }

    fun track(pos: Float, now: Float, dt: Float): Float {
        if (!tracking) return deviation
        // drop what has left the window (and the oldest, should a very fast screen ever fill the buffer)
        var drop = 0
        while (drop < n && ts[drop] < now - window) drop++
        if (n - drop >= CAP) drop = n - CAP + 1
        if (drop > 0) {
            System.arraycopy(ps, drop, ps, 0, n - drop)
            System.arraycopy(ts, drop, ts, 0, n - drop)
            n -= drop
        }
        ps[n] = pos; ts[n] = now; n++
        val raw = (acceleration() * sensitivity).coerceIn(-max, max)
        val ease = if (response <= 0f) 1f else (dt / response).coerceIn(0f, 1f)
        deviation += (raw - deviation) * ease
        return deviation
    }

    /** Speeds between neighbouring samples, then the change between neighbouring speeds, averaged. */
    private fun acceleration(): Float {
        if (n < 3) return 0f
        var total = 0f
        var count = 0
        var haveV = false
        var lastV = 0f
        var lastMid = 0f
        for (i in 1 until n) {
            val d = ts[i] - ts[i - 1]
            if (d <= 0f) continue
            val vel = (ps[i] - ps[i - 1]) / d
            val mid = (ts[i] + ts[i - 1]) / 2f
            if (haveV) {
                val dm = mid - lastMid
                if (dm > 0f) { total += (vel - lastV) / dm; count++ }
            }
            haveV = true; lastV = vel; lastMid = mid
        }
        return if (count == 0) 0f else total / count
    }

    private companion object { const val CAP = 128 }
}

/**
 * The selection pill of the phone's nav, as a thing with weight.
 *
 * A tap lifts it at once — it swells past the bar on two springs, one per axis, loose enough to overshoot and come
 * back — and it stays up for the whole journey. The journey is its own spring. Only once it has all but arrived is
 * the lift let go, so it comes down where it stands, dipping a little under its resting size on the way. A finger
 * dragging it is the same lift held open.
 *
 * On the way its position is read every frame and its acceleration bends it: wide and flat as it launches, narrow
 * and tall as it brakes. A journey keeps one sense of that for its whole length, keyed to where it is going; a
 * finger gets the raw force.
 *
 * The glass (the rim, the shadow, the lighter body) is a third, much faster spring, so the pill reads as glass
 * almost as soon as it is touched and sheds it from the landing while its size is still settling.
 *
 * Positions are in tab steps: 0 is the first tab, 1 the second. No clock and no drawing in here — [step] is called
 * once a frame by whoever draws it (LiquidNav.kt), and a test can call it just the same.
 */
internal class PillMotion(private val count: Int, start: Int = 0) {
    private val travel = Spring1(start.toFloat())
    private val liftXs = Spring1()
    private val liftYs = Spring1()
    private val glassS = Spring1()
    private val squash = Squash()

    private var travelActive = false
    private var travelFrom = start.toFloat()
    private var travelTarget = start.toFloat()
    private var lifted = false
    private var dragging = false
    private var dragTarget = 0f           // where the finger is, ends resisted
    private var dragWant = 0f             // where the finger is, held inside the tabs
    private var dragFollow = 0f           // where the pill is drawn while held: a smoothed chase of the finger
    private var pressAt = 0f
    private var movedForReal = false
    private var travelSign = 0f           // -1 left, 1 right, 0 while a finger drives
    private var travelSignEased = 0f
    private var handover = 1f             // 0 = all glass, 1 = the plain pill

    /** The tab the pill is headed for (or resting on). */
    var target = start
        private set

    /** Where the pill is drawn, in tab steps. */
    val pos: Float get() = if (dragging) dragFollow else travel.x
    /** The width's lift: 0 at rest, 1 raised; overshoots both ways. */
    val liftX: Float get() = liftXs.x
    /** The height's lift; settles a shade sooner than the width's. */
    val liftY: Float get() = liftYs.x
    /** How much of the glass is on, 0..1. */
    val glass: Float get() = glassS.x.coerceIn(0f, 1f)
    /** The squash: width scales by 1 + this, height by 1 - this. */
    var deviation = 0f
        private set
    /** How much of the pill still reads as glass: 1 lifted, 0 once it has handed over to the plain pill. */
    val presence: Float get() = 1f - handover
    val held: Boolean get() = dragging
    /** True when nothing is left to move: the plain pill at rest. */
    var idle = true
        private set

    /** No journey: the pill is simply there (Motion: Reduced, or a tab nobody watched change). */
    fun snapTo(index: Int) {
        target = index.coerceIn(0, count - 1)
        travel.x = target.toFloat(); travel.v = 0f
        travelFrom = travel.x; travelTarget = travel.x
        travelActive = false; lifted = false; dragging = false
        liftXs.x = 0f; liftXs.v = 0f; liftYs.x = 0f; liftYs.v = 0f; glassS.x = 0f; glassS.v = 0f
        handover = 1f; deviation = 0f; travelSign = 0f; travelSignEased = 0f
        squash.stop(); idle = true
    }

    /** Send the pill to a tab from wherever it is; it keeps the speed it has. */
    fun animateTo(index: Int) {
        val next = index.coerceIn(0, count - 1)
        if (next == target && !dragging) return
        if (dragging) { travel.x = dragFollow; travel.v = 0f; dragging = false }
        target = next
        travelActive = true
        lifted = true
        travelFrom = travel.x
        travelTarget = next.toFloat()
        travelSign = signOf(travelTarget - travelFrom)
        idle = false
    }

    /** A finger takes the pill. [frac] is where the finger is, in tab steps (it may be past either end). */
    fun grab(frac: Float) {
        dragFollow = pos                    // starts where the pill is, so a grab away from it eases over
        dragging = true
        travelActive = false
        lifted = true
        travelSign = 0f
        travel.v = 0f
        pressAt = held(frac)
        movedForReal = false
        dragWant = pressAt
        dragTarget = resisted(frac)
        idle = false
    }

    fun dragTo(frac: Float) {
        if (!dragging) return
        dragWant = held(frac)
        if (abs(dragWant - pressAt) > 0.2f) movedForReal = true
        dragTarget = resisted(frac)
    }

    /** The finger lets go: the pill settles on the nearest tab. Returns that tab. */
    fun release(): Int {
        if (!dragging) return target
        val next = (if (movedForReal) dragWant else pressAt).roundToInt().coerceIn(0, count - 1)
        dragging = false
        target = next
        travelActive = true
        travel.x = dragFollow; travel.v = 0f
        travelFrom = dragFollow
        travelTarget = next.toFloat()
        travelSign = signOf(travelTarget - travelFrom)
        idle = false
        return next
    }

    /**
     * One frame. [dt] seconds since the last one, [now] a clock in seconds, [stepDp] the distance between two tabs
     * in dp (the squash is tuned in dp). Returns true while anything is still moving.
     */
    fun step(dt: Float, now: Float, stepDp: Float): Boolean {
        if (idle) return false
        // 1) the journey
        var arrived = true
        if (travelActive) {
            travel.step(travelTarget, dt, TRAVEL_K, TRAVEL_C)
            arrived = travel.rest(travelTarget, 0.003f, 0.05f)
        }
        // 2) held: chase the finger, so a grab away from the pill glides over to it
        if (dragging) dragFollow += (dragTarget - dragFollow) * (1f - exp(-dt / FOLLOW_TAU))
        // 3) the lift: up for the whole journey, let go at the landing
        if (!dragging && (arrived || progress() >= HANDOVER_START)) lifted = false
        val up = if (lifted) 1f else 0f
        liftXs.step(up, dt, LIFT_K, LIFT_CX); liftXs.rest(up, 0.0008f, 0.01f)
        liftYs.step(up, dt, LIFT_K, LIFT_CY); liftYs.rest(up, 0.0008f, 0.01f)
        glassS.step(up, dt, GLASS_K, GLASS_C); glassS.rest(up, 0.0008f, 0.01f)
        val down = !lifted && liftXs.x == 0f && liftYs.x == 0f && glassS.x == 0f
        // 4) the journey is over only once the pill has also come down: that outlives the spring that carried it
        if (travelActive && arrived && down && !dragging) travelActive = false
        // 5) the squash, read off where the pill is drawn this frame
        if (!squash.tracking) squash.start()
        var dev = squash.track(pos * stepDp, now, dt)
        if (travelSignEased == 0f) travelSignEased = travelSign
        else if (travelSignEased != travelSign) {
            // a journey turned round mid-flight crosses through undeformed rather than turning inside out
            travelSignEased += (travelSign - travelSignEased) * (1f - exp(-dt / SIGN_TAU))
            if (abs(travelSign - travelSignEased) < 0.01f) travelSignEased = travelSign
        }
        // keep the force's size — the launch, the lull, the braking — and take its sense from where it is going
        val key = travelSignEased
        if (key != 0f) dev = dev * (1f - abs(key)) - key * abs(dev)
        deviation = dev
        // 6) the hand-off: the glass leaves as the pill starts coming down, and turns straight back on a new tap
        val to = if (lifted) 0f else 1f
        val tau = if (to > handover) HANDOVER_TAU else GLASS_RETURN_TAU
        handover += (to - handover) * (1f - exp(-dt / tau))
        if (abs(to - handover) < 0.002f) handover = to
        // everything must be finished, not only the journey: the coming-down, the squash draining, the hand-off
        if (!travelActive && !dragging && down && abs(deviation) < 0.0005f && handover >= 1f) {
            squash.stop()
            deviation = 0f
            travelSign = 0f; travelSignEased = 0f
            idle = true
        }
        return !idle
    }

    /** How far through the current journey the pill is, 0..1. */
    private fun progress(): Float {
        val span = abs(travelTarget - travelFrom)
        if (span < 1e-6f) return 1f
        return (1f - abs(travelTarget - travel.x) / span).coerceIn(0f, 1f)
    }

    private fun held(frac: Float) = frac.coerceIn(0f, (count - 1).toFloat())

    /** Past either end the pill follows less and less: it can be pulled a little way out, never off. */
    private fun resisted(frac: Float): Float {
        val inside = held(frac)
        val over = frac - inside
        if (over == 0f) return inside
        return inside + sign(over) * (1f - 1f / (abs(over) * 0.55f + 1f)) * END_GIVE
    }

    private fun signOf(span: Float) = if (abs(span) < 1e-6f) 0f else sign(span)

    companion object {
        const val TRAVEL_K = 280f
        const val TRAVEL_C = 31.4f
        const val LIFT_K = 250f
        const val LIFT_CX = 19.0f           // damping ratio 0.6 across
        const val LIFT_CY = 22.1f           // 0.7 down: the height settles a shade sooner than the width
        const val GLASS_K = 1000f
        const val GLASS_C = 63.3f           // critically damped, four times as stiff as the size
        const val HANDOVER_START = 0.92f
        const val HANDOVER_TAU = 0.09f
        const val GLASS_RETURN_TAU = 0.05f
        const val FOLLOW_TAU = 0.05f
        const val SIGN_TAU = 0.25f
        /** How far past an end, in tab steps, the hardest pull can take the pill. */
        const val END_GIVE = 0.6f
    }
}
