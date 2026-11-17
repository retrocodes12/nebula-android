package com.nuvio.ckplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Settings in its new shape (SettingsRoot.kt, 2026-11-17): the root page drawn on a phone, from the top, scrolled, and a
 * page of switches and chips in the restyled rows. The pictures (the barshots artifact, `settings-*.png`) are for the
 * eye; the test only proves each was drawn (enough non-black pixels).
 */
@RunWith(AndroidJUnit4::class)
class SettingsShapeDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = ins.targetContext
    private lateinit var act: Activity
    private val copier = HandlerThread("settings-copy").apply { start() }
    private val owners = ArrayList<Owner>()
    private fun log(s: String) = Log.i("SETTINGS-DEVICE", s)

    private class Owner : LifecycleOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {
        private val reg = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = reg
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        override val onBackPressedDispatcher = OnBackPressedDispatcher()
        fun start() { saved.performRestore(null); reg.currentState = Lifecycle.State.RESUMED }
        fun stop() { reg.currentState = Lifecycle.State.DESTROYED }
    }

    private fun sh(cmd: String) {
        val fd = ins.uiAutomation.executeShellCommand(cmd)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    @Before fun open() {
        act = ins.startActivitySync(Intent(Intent.ACTION_MAIN).setClassName(ctx.packageName, "android.app.Activity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @After fun close() {
        ins.runOnMainSync { owners.forEach { it.stop() }; act.finish() }
        copier.quitSafely()
    }

    private fun stage(content: @Composable () -> Unit) {
        ins.runOnMainSync {
            val owner = Owner(); owners += owner
            act.window.decorView.setViewTreeLifecycleOwner(owner)
            act.window.decorView.setViewTreeSavedStateRegistryOwner(owner)
            owner.start()
            val view = ComposeView(act)
            view.setContent {
                CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) { content() }
                }
            }
            act.setContentView(view)
        }
        SystemClock.sleep(1500)
    }

    /** The window as it is, saved for the eye; returns how many pixels are not near-black. */
    private fun shot(name: String): Int {
        val d = act.window.decorView
        val bmp = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888)
        val l = CountDownLatch(1)
        var ok = false
        PixelCopy.request(act.window, bmp, { r -> ok = r == PixelCopy.SUCCESS; l.countDown() }, Handler(copier.looper))
        l.await(3, TimeUnit.SECONDS)
        assertTrue("the screen could be read", ok)
        val dir = ctx.getExternalFilesDir("barshots")!!.also { it.mkdirs() }
        val f = File(dir, "settings-$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sh("mkdir -p /data/local/tmp/barshots")
        sh("cp ${f.absolutePath} /data/local/tmp/barshots/")
        var lit = 0
        for (y in 0 until bmp.height step 8) for (x in 0 until bmp.width step 8) {
            val c = bmp.getPixel(x, y)
            if (((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255) > 90) lit++
        }
        log("$name: ${bmp.width}x${bmp.height}, $lit lit samples")
        return lit
    }

    private fun swipeUp() {
        val d = act.window.decorView
        sh("input swipe ${d.width / 2} ${d.height * 3 / 4} ${d.width / 2} ${d.height / 5} 400")
        SystemClock.sleep(900)
    }

    @Test fun theRootPage() {
        stage {
            SettingsScreen(
                onAddons = {}, onLayout = {}, onSupport = {}, onHome = {}, onPlayback = {}, onStreams = {},
                onProfile = {}, onParty = {}, onFriends = {}, onAdvanced = {}, onTracking = {},
            )
        }
        assertTrue(shot("root-top") > 200)
        swipeUp()
        assertTrue(shot("root-middle") > 200)
        swipeUp()
        assertTrue(shot("root-bottom") > 200)
    }

    @Test fun aPageOfRows() {
        stage {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                BackBar("Playback", null) {}
                SettingsHeader("NEXT EPISODE", "Rolling on, skipping ahead and coming back")
                SettingsGroup {
                    SettingsToggle("Auto-play next episode", "Count down and roll into the next episode when one ends", true) {}
                    SettingsToggle("Skip intro", "Show a Skip button over intros and recaps", false) {}
                    SettingsChips("Up next card", "When the card shows", listOf("credits" to "At the credits", "end" to "At the end"), "credits", divider = false) {}
                }
                SettingsHeader("PLAYER")
                SettingsGroup {
                    SettingsToggle("Pause board", "What is playing and what is next, while paused", true, divider = false) {}
                }
            }
        }
        assertTrue(shot("page") > 200)
    }
}
