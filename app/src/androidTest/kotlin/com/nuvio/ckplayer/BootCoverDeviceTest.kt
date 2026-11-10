package com.nuvio.ckplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.PixelCopy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * No frame of Home between the who's-watching picker and the boot screen (his 11-10 "after i select the profile the
 * home screen flashes for like a millisec before the loading screen appears").
 *
 * A real window, three flat colours: Home is red, the picker green, the boot screen blue. From the pick on, the screen
 * is read back after EVERY frame (PixelCopy of the window's last drawn buffer, asked for at the top of the next frame),
 * so a single red frame cannot slip by. Two stagings: the cover as 1.109.0–1.110.0 had it, which must show the red frame
 * (it proves this test can see the fault), and BootCover, which must not.
 */
@RunWith(AndroidJUnit4::class)
class BootCoverDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = ins.targetContext
    private lateinit var act: Activity
    private val copier = HandlerThread("boot-copy").apply { start() }
    private val owners = ArrayList<Owner>()
    private fun log(s: String) = Log.i("BOOT-DEVICE", s)

    /** A plain Activity is no LifecycleOwner; Compose needs one (and a saved-state owner) on the view tree. */
    private class Owner : LifecycleOwner, SavedStateRegistryOwner {
        private val reg = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = reg
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        fun start() { saved.performRestore(null); reg.currentState = Lifecycle.State.RESUMED }
        fun stop() { reg.currentState = Lifecycle.State.DESTROYED }
    }

    private fun sh(cmd: String) {
        val fd = ins.uiAutomation.executeShellCommand(cmd)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    @Before fun open() {
        // CI's emulator runs with animations off; a phone does not. Compose reads this when the window's composer is made.
        sh("settings put global animator_duration_scale 1")
        act = ins.startActivitySync(Intent(Intent.ACTION_MAIN).setClassName(ctx.packageName, "android.app.Activity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @After fun close() {
        ins.runOnMainSync { owners.forEach { it.stop() }; act.finish() }
        sh("settings put global animator_duration_scale 0")
        copier.quitSafely()
    }

    private fun name(px: Int): String {
        val r = (px shr 16) and 255; val g = (px shr 8) and 255; val b = px and 255
        return when {
            r > 180 && g < 90 && b < 90 -> "home"
            g > 180 && r < 90 && b < 90 -> "picker"
            b > 180 && r < 90 && g < 90 -> "boot"
            else -> "mix(" + r + "," + g + "," + b + ")"
        }
    }

    /** One read of the middle of the window, as it was last drawn. */
    private fun copy(done: (String) -> Unit) {
        val d = act.window.decorView
        val cx = d.width / 2; val cy = d.height / 2
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        PixelCopy.request(act.window, Rect(cx - 2, cy - 2, cx + 2, cy + 2), bmp,
            { r -> done(if (r == PixelCopy.SUCCESS) name(bmp.getPixel(1, 1)) else "copy-failed-$r") }, Handler(copier.looper))
    }

    private fun now(): String {
        var out = "?"; val l = CountDownLatch(1)
        ins.runOnMainSync { copy { out = it; l.countDown() } }
        l.await(3, TimeUnit.SECONDS)
        return out
    }

    /** The picker up, then a pick, then Home ready: what the screen showed after each frame from just before the pick. */
    private fun play(old: Boolean): List<String> {
        val asking = mutableStateOf(true); val booting = mutableStateOf(true)
        ins.runOnMainSync {
            val owner = Owner(); owners += owner
            act.window.decorView.setViewTreeLifecycleOwner(owner)
            act.window.decorView.setViewTreeSavedStateRegistryOwner(owner)
            owner.start()
            val view = ComposeView(act)
            view.setContent {
                Box(Modifier.fillMaxSize().background(Color.Red)) {                                   // Home
                    if (asking.value) Box(Modifier.fillMaxSize().background(Color.Green))            // the picker
                    if (old) {
                        AnimatedVisibility(visible = booting.value && !asking.value, enter = fadeIn(tween(0)), exit = fadeOut(tween(320))) {
                            Box(Modifier.fillMaxSize().background(Color.Blue))
                        }
                    } else {
                        BootCover(booting.value, asking.value) { Box(Modifier.fillMaxSize().background(Color.Blue)) }
                    }
                }
            }
            act.setContentView(view)
        }
        val end = SystemClock.uptimeMillis() + 8000
        while (now() != "picker") { if (SystemClock.uptimeMillis() > end) throw AssertionError("the picker never drew: " + now()); SystemClock.sleep(50) }
        SystemClock.sleep(300)

        val frames = 45; val pickAt = 3; val readyAt = 30
        val seen = arrayOfNulls<String>(frames); val got = CountDownLatch(frames)
        ins.runOnMainSync {
            val ch = Choreographer.getInstance()
            var n = 0
            ch.postFrameCallback(object : Choreographer.FrameCallback {
                override fun doFrame(t: Long) {
                    val i = n
                    copy { seen[i] = it; got.countDown() }       // the frame before this one, as the screen has it
                    if (i == pickAt) asking.value = false        // the pick
                    if (i == readyAt) booting.value = false      // Home is ready
                    n++
                    if (n < frames) ch.postFrameCallback(this)
                }
            })
        }
        assertTrue("every frame was read", got.await(20, TimeUnit.SECONDS))
        SystemClock.sleep(900)                                   // the fade out (320 ms) ends
        val out = seen.map { it ?: "?" } + now()
        log((if (old) "old cover: " else "BootCover: ") + out.joinToString(" "))
        return out
    }

    /** From the last frame with the picker to the first with the boot screen: what came between. */
    private fun between(s: List<String>): List<String> {
        val lastPicker = s.lastIndexOf("picker"); val firstBoot = s.indexOf("boot")
        assertTrue("the picker was seen, then the boot screen: $s", lastPicker >= 0 && firstBoot > lastPicker)
        return s.subList(lastPicker + 1, firstBoot)
    }

    @Test fun theOldCoverLetsHomeThroughForAFrame() {
        val s = play(old = true)
        val gap = between(s)
        log("old cover, between picker and boot: $gap")
        // the fault itself — if this stops failing-to-cover (a Compose that enters at once), the test below proves nothing new, so say so
        assertTrue("the old cover should show Home between the picker and the boot screen (else this test cannot see the fault): $s", gap.contains("home"))
    }

    @Test fun bootCoverIsUpOnTheFrameThePickerLeaves() {
        val s = play(old = false)
        val gap = between(s)
        log("BootCover, between picker and boot: $gap")
        assertEquals("nothing between the picker and the boot screen: $s", emptyList<String>(), gap)
        val firstBoot = s.indexOf("boot")
        assertFalse("no Home before the boot screen: $s", s.subList(0, firstBoot).contains("home"))
        assertEquals("Home in the end, once the boot screen has faded: $s", "home", s.last())
    }
}
