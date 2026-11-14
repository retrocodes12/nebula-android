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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A person's films and series on a real Android, against the LIVE cloud: the answer for a cast member of a known
 * title is fetched and parsed, then the screen is composed in a real window and read back (the grid of posters must
 * be drawn: enough non-black pixels). The pictures (the barshots artifact, `person-*.png`) are for the eye.
 */
@RunWith(AndroidJUnit4::class)
class PersonDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = ins.targetContext
    private lateinit var act: Activity
    private val copier = HandlerThread("person-copy").apply { start() }
    private val owners = ArrayList<Owner>()
    private fun log(s: String) = Log.i("PERSON-DEVICE", s)

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
            view.setContent { CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) { content() } }
            act.setContentView(view)
        }
    }

    private fun screen(name: String): Bitmap {
        val d = act.window.decorView
        val bmp = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888)
        val l = CountDownLatch(1)
        var ok = false
        PixelCopy.request(act.window, bmp, { r -> ok = r == PixelCopy.SUCCESS; l.countDown() }, Handler(copier.looper))
        l.await(3, TimeUnit.SECONDS)
        assertTrue("the screen could be read", ok)
        val dir = ctx.getExternalFilesDir("barshots")!!.also { it.mkdirs() }
        val f = File(dir, "person-$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sh("mkdir -p /data/local/tmp/barshots")
        sh("cp ${f.absolutePath} /data/local/tmp/barshots/")
        return bmp
    }

    /** The share of the lower part of the screen (where the grid is) that is not near-black. */
    private fun lit(bmp: Bitmap): Float {
        val y0 = bmp.height / 4
        val px = IntArray(bmp.width * (bmp.height - y0))
        bmp.getPixels(px, 0, bmp.width, 0, y0, bmp.width, bmp.height - y0)
        return px.count { p -> android.graphics.Color.red(p) + android.graphics.Color.green(p) + android.graphics.Color.blue(p) > 90 }.toFloat() / px.size
    }

    @Test fun aCastMembersFilmsAndSeriesComeFromTheCloudAndAreDrawn() {
        val a = runBlocking { People.load("Tim Robbins", "tt0111161") }
        log("answer: " + when (a) { is People.Answer.Found -> "${a.person.id} ${a.person.items.size} items, first ${a.person.items.firstOrNull()?.meta?.name}"; else -> a.toString() })
        assertTrue("the cloud knows him through The Shawshank Redemption ($a)", a is People.Answer.Found && a.person.items.size >= 10)
        val p = (a as People.Answer.Found).person
        assertTrue("The Shawshank Redemption is among them", p.items.any { it.meta.id == "tt0111161" })
        assertTrue("there are films and a series to choose between", People.kinds(p.items).map { it.first }.containsAll(listOf("all", "movie", "series")))
        var back = false
        stage { PersonScreen("Tim Robbins", "tt0111161", onBack = { back = true }, onOpen = {}) }
        // the posters come from the image host: give them time to arrive
        var share = 0f
        val until = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < until) { SystemClock.sleep(1500); share = lit(screen("grid")); if (share > 0.25f) break }
        log("the grid: ${"%.2f".format(share)} of its area drawn")
        assertTrue("the grid of posters is drawn (${"%.2f".format(share)} of the lower screen is not black)", share > 0.25f)
        assertTrue("nothing pressed Back", !back)
    }

    @Test fun aNameNobodyHasSaysSo() {
        val a = runBlocking { People.load("Zzyzx Nobody Here", null) }
        log("nobody: $a")
        assertTrue("an unknown name is None, not a failure ($a)", a is People.Answer.None)
        stage { PersonScreen("Zzyzx Nobody Here", null, onBack = {}, onOpen = {}) }
        SystemClock.sleep(2500)
        screen("none")
    }
}
