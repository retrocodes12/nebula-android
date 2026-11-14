package com.nuvio.ckplayer

import android.app.Activity
import android.content.Context
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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Six profiles on a real Android: the picker with six people in it and the editor of one of them are composed in a
 * real window from stored lists (`viewers` + `people`, the wire's shape) and read back from the screen. Each profile's
 * circle must show in its own colour (the picture or the letter is drawn in exactly that colour), a profile on a
 * picture must draw more of it than a thin letter does not, and nothing may throw. The pictures (the barshots
 * artifact, `who-six-*.png`) are for the eye.
 */
@RunWith(AndroidJUnit4::class)
class WhoSixDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = ins.targetContext
    private lateinit var act: Activity
    private val copier = HandlerThread("who-copy").apply { start() }
    private val owners = ArrayList<Owner>()
    private fun log(s: String) = Log.i("WHO-DEVICE", s)

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

    // name, colour, picture ("" = the letter)
    private val people = listOf(
        listOf("a", "Sohil", "#E50914", "planet"), listOf("b", "Kids", "#30D158", "rocket"), listOf("c", "Mum", "#FF375F", "heart"),
        listOf("d", "Dad", "#FF9F0A", ""), listOf("e", "Guest", "#0A84FF", "ghost"), listOf("f", "Movie night", "#BF5AF2", "popcorn"),
    )

    @Before fun open() {
        val p = ctx.getSharedPreferences("ckplayer", Context.MODE_PRIVATE)
        fun entry(r: List<String>) = JSONObject().put("id", r[0]).put("name", r[1]).put("av", r[2])
        val x = JSONObject()
        people.forEach { r -> val e = JSONObject().put("born", 1L); if (r[3].isNotEmpty()) e.put("pic", r[3]); x.put(r[0], e) }
        p.edit().clear()
            .putString("viewers_v1", JSONObject().put("at", 5L).put("list", JSONArray().put(entry(people[0])).put(entry(people[1]))).toString())
            .putString("people_v1", JSONObject().put("at", 5L).put("list", JSONArray().apply { people.drop(2).forEach { put(entry(it)) } }).put("x", x).toString())
            .putString("viewer", "c")
            .commit()
        ins.runOnMainSync { Viewers.load(ctx) }
        act = ins.startActivitySync(Intent(Intent.ACTION_MAIN).setClassName(ctx.packageName, "android.app.Activity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @After fun close() {
        ins.runOnMainSync { owners.forEach { it.stop() }; act.finish() }
        ctx.getSharedPreferences("ckplayer", Context.MODE_PRIVATE).edit().clear().commit()
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
        var mine = false
        val focusBy = SystemClock.uptimeMillis() + 12000
        while (SystemClock.uptimeMillis() < focusBy) {
            ins.runOnMainSync { mine = act.hasWindowFocus() }
            if (mine) break
            sh("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
            SystemClock.sleep(500)
        }
        SystemClock.sleep(1500)
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
        val f = File(dir, "who-six-$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sh("mkdir -p /data/local/tmp/barshots")
        sh("cp ${f.absolutePath} /data/local/tmp/barshots/")
        return bmp
    }

    /** How many pixels are (nearly) this colour. */
    private fun count(bmp: Bitmap, hex: String): Int {
        val c = android.graphics.Color.parseColor(hex)
        val r = android.graphics.Color.red(c); val g = android.graphics.Color.green(c); val b = android.graphics.Color.blue(c)
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        var n = 0
        for (p in px) if (abs(android.graphics.Color.red(p) - r) <= 10 && abs(android.graphics.Color.green(p) - g) <= 10 && abs(android.graphics.Color.blue(p) - b) <= 10) n++
        return n
    }

    @Test fun thePickerDrawsSixPeopleEachInTheirOwnColour() {
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), Viewers.list(ctx).map { it.id })
        assertEquals("c", Viewers.current)
        stage { WhoPicker() }
        val bmp = screen("pick")
        val seen = people.associate { it[1] to count(bmp, it[2]) }
        log("colours on the picker: $seen")
        for (r in people) assertTrue("${r[1]} is drawn in ${r[2]} (${seen[r[1]]} pixels)", (seen[r[1]] ?: 0) > 400)
        // a picture fills more of its circle than a letter's strokes do: the heart against the letter D
        assertTrue("Mum's heart is a solid shape (${seen["Mum"]} against Dad's letter ${seen["Dad"]})", (seen["Mum"] ?: 0) > (seen["Dad"] ?: 0))
    }

    @Test fun theEditorDrawsThePersonThePicturesAndTheChoices() {
        var done = false
        stage { WhoEditor("c", WhoDraft(), onPin = {}, onDone = { done = true }) }
        val bmp = screen("edit")
        val tint = count(bmp, "#FF375F"); val white = count(bmp, "#F2F2F7")
        log("the editor: tint $tint, white $white")
        assertTrue("Mum's circle and picture are drawn in her colour ($tint pixels)", tint > 1500)
        // the lit picture and Save are the white things on this page
        assertTrue("the chosen picture and Save are lit white ($white pixels)", white > 3000)
        assertTrue("the editor stays open until something is pressed", !done)
        // a profile that does not exist closes it
        var gone = false
        stage { WhoEditor("z", WhoDraft(), onPin = {}, onDone = { gone = true }) }
        assertTrue("an editor for nobody closes itself", gone)
    }
}
