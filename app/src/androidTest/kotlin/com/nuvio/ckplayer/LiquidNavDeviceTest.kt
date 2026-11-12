package com.nuvio.ckplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The phone's nav as liquid glass (LiquidNav.kt) and the lists that give at their ends (Rubber.kt), on a real window.
 *
 * A page of colour under the real pill, wired the way AppRoot wires it (the page recorded for the glass, the rubber
 * edge given to the tree). A real tap on a tab must arrive as that tab; a real finger along the bar must carry the
 * pill and set it down on the tab it was let go over; a real pull at the top of the list must draw the page out and
 * let it come home. The pictures (the barshots artifact, `liquid-*.png`) are for the eye: the frost, the bead in
 * flight, the page pulled out.
 */
@RunWith(AndroidJUnit4::class)
class LiquidNavDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = ins.targetContext
    private lateinit var act: Activity
    private val copier = HandlerThread("liquid-copy").apply { start() }
    private val owners = ArrayList<Owner>()
    private fun log(s: String) = Log.i("LIQUID-DEVICE", s)

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
        sh("settings put global animator_duration_scale 1")
        act = ins.startActivitySync(Intent(Intent.ACTION_MAIN).setClassName(ctx.packageName, "android.app.Activity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @After fun close() {
        ins.runOnMainSync { owners.forEach { it.stop() }; act.finish() }
        sh("settings put global animator_duration_scale 0")
        copier.quitSafely()
    }

    private class Stage {
        val tabs = CopyOnWriteArrayList<Int>()          // every tab the pill reported, in order
        @Volatile var bar = Rect.Zero
        @Volatile var page = Rect.Zero
        @Volatile var first: LayoutCoordinates? = null  // the list's first row
        @Volatile var fold: NavFoldState? = null
        val pageNo = mutableIntStateOf(0)               // 0 = the page of colour, 1 = a page of nothing
    }

    private val hues = listOf(0xFFE5484D, 0xFF3E63DD, 0xFF30A46C, 0xFFF5A524, 0xFF8E4EC6, 0xFF12A594, 0xFFE93D82)

    private fun stage(folds: Boolean = false, swaps: Boolean = false): Stage {
        val s = Stage()
        ins.runOnMainSync {
            val owner = Owner(); owners += owner
            act.window.decorView.setViewTreeLifecycleOwner(owner)
            act.window.decorView.setViewTreeSavedStateRegistryOwner(owner)
            owner.start()
            val view = ComposeView(act)
            view.setContent {
                var tab by androidx.compose.runtime.remember { mutableIntStateOf(0) }
                val backdrop = rememberPageBackdrop(on = true)
                // the way AppRoot wires the fold: every scroll under the nav passes through its connection
                val fold = rememberNavFold()
                s.fold = fold
                CompositionLocalProvider(LocalOverscrollFactory provides RubberOverscrollFactory) {
                    Box(
                        Modifier.fillMaxSize().background(Color(0xFF09090B)).onGloballyPositioned { s.page = it.boundsInWindow() }
                            .then(if (folds) Modifier.nestedScroll(fold.connection) else Modifier)
                    ) {
                        // the way AppRoot holds its pages: one at a time inside the box that is recorded for the glass
                        if (swaps) androidx.compose.animation.AnimatedContent(
                            targetState = s.pageNo.intValue, modifier = Modifier.fillMaxSize().pageBackdrop(backdrop, on = true), label = "page",
                        ) { n ->
                            if (n == 1) Box(Modifier.fillMaxSize().background(Color(0xFF09090B)))
                            else LazyColumn(Modifier.fillMaxSize()) {
                                items(40) { i ->
                                    LazyRow(Modifier.fillMaxWidth().height(132.dp)) {
                                        items(12) { j -> Box(Modifier.padding(5.dp).size(96.dp, 122.dp).background(Color(hues[(i * 3 + j) % hues.size]))) }
                                    }
                                }
                            }
                        }
                        else LazyColumn(Modifier.fillMaxSize().pageBackdrop(backdrop, on = true)) {
                            items(40) { i ->
                                LazyRow(
                                    Modifier.fillMaxWidth().height(132.dp)
                                        .then(if (i == 0) Modifier.onGloballyPositioned { s.first = it } else Modifier)
                                ) {
                                    items(12) { j ->
                                        Box(Modifier.padding(5.dp).size(96.dp, 122.dp).background(Color(hues[(i * 3 + j) % hues.size]))) {
                                            Text("Title ${i * 12 + j}", color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                        NavPillForTest(
                            folded = folds && fold.folded, tab = tab, backdrop = backdrop,
                            modifier = Modifier.align(Alignment.BottomCenter).onGloballyPositioned { s.bar = it.boundsInWindow() },
                            onTab = { tab = it; s.tabs += it },
                        )
                    }
                }
            }
            act.setContentView(view)
        }
        val end = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < end && (s.bar.width <= 0f || (!swaps && s.first == null))) SystemClock.sleep(50)
        // a cold emulator can leave a system dialog holding the screen: a tap sent then reaches nobody. Clear it and
        // wait until this window is the one being touched.
        var mine = false
        val focusBy = SystemClock.uptimeMillis() + 12000
        while (SystemClock.uptimeMillis() < focusBy) {
            ins.runOnMainSync { mine = act.hasWindowFocus() }
            if (mine) break
            sh("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
            SystemClock.sleep(500)
        }
        log("window focus: $mine")
        SystemClock.sleep(700)
        return s
    }

    private fun shot(name: String, top: Boolean = false) {
        val d = act.window.decorView
        val w = d.width; val h = d.height
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val l = CountDownLatch(1)
        var ok = false
        PixelCopy.request(act.window, bmp, { r -> ok = r == PixelCopy.SUCCESS; l.countDown() }, Handler(copier.looper))
        l.await(3, TimeUnit.SECONDS)
        if (!ok) { log("picture $name: the copy failed"); return }
        val cut = if (top) Bitmap.createBitmap(bmp, 0, 0, w, h / 2) else Bitmap.createBitmap(bmp, 0, h * 5 / 8, w, h - h * 5 / 8)
        val dir = ctx.getExternalFilesDir("barshots")!!.also { it.mkdirs() }
        val f = File(dir, "liquid-$name.png")
        f.outputStream().use { cut.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sh("mkdir -p /data/local/tmp/barshots")
        sh("cp ${f.absolutePath} /data/local/tmp/barshots/")
    }

    /** The bar without the gap under it, and the y of its tabs' middle. */
    private fun tabsY(s: Stage): Float = s.bar.top + (s.bar.height - 14 * ctx.resources.displayMetrics.density) / 2f

    private fun firstTop(s: Stage): Float {
        var y = 0f
        ins.runOnMainSync { y = s.first?.takeIf { it.isAttached }?.boundsInWindow()?.top ?: Float.NaN }
        return y
    }

    @Test fun aTapCarriesThePillAndAFingerCarriesItByHand() {
        val s = stage()
        log("bar ${s.bar}, page ${s.page}")
        assertTrue("the bar is on screen: ${s.bar}", s.bar.width > 0f && s.bar.height > 0f)
        shot("1-rest")

        // a tap on the middle of the bar is a tap on Library, the third of five; the pictures catch the bead in flight
        val y = tabsY(s)
        sh("input tap ${s.bar.center.x.toInt()} ${y.toInt()}")
        for ((i, wait) in listOf(40L, 50L, 60L, 80L, 120L, 200L).withIndex()) { SystemClock.sleep(wait); shot("2-tap-" + (i + 1)) }
        SystemClock.sleep(1800)
        assertEquals("the tap arrived as Library", listOf(2), s.tabs.toList())
        shot("3-landed")

        // a finger from Library along the bar to its last tab: the pill goes with it and stays where it was let go
        val d = ctx.resources.displayMetrics.density
        val swipe = Thread { sh("input swipe ${s.bar.center.x.toInt()} ${y.toInt()} ${(s.bar.right - 34 * d).toInt()} ${y.toInt()} 900") }
        swipe.start()
        for ((i, wait) in listOf(300L, 250L, 250L).withIndex()) { SystemClock.sleep(wait); shot("4-held-" + (i + 1)) }
        swipe.join(5000)
        SystemClock.sleep(1800)
        assertEquals("the pill was set down on the last tab", listOf(2, 4), s.tabs.toList())
        shot("5-set-down")
    }

    @Test fun aPullAtTheTopDrawsThePageOutAndItComesHome() {
        val s = stage()
        val home = firstTop(s)
        val x = s.page.center.x
        val swipe = Thread { sh("input swipe ${x.toInt()} ${(s.page.top + s.page.height * 0.25f).toInt()} ${x.toInt()} ${(s.page.top + s.page.height * 0.70f).toInt()} 900") }
        swipe.start()
        var most = home
        val until = SystemClock.uptimeMillis() + 850
        var took = false
        while (SystemClock.uptimeMillis() < until) {
            SystemClock.sleep(60)
            most = maxOf(most, firstTop(s))
            if (!took && SystemClock.uptimeMillis() > until - 300) { took = true; shot("6-pulled", top = true) }
        }
        swipe.join(5000)
        log("pull: home $home, furthest $most")
        val d = ctx.resources.displayMetrics.density
        assertTrue("the page came with the finger ($home -> $most)", most - home > 24 * d)
        assertTrue("but less far than the finger went", most - home < s.page.height * 0.45f * 0.7f)
        SystemClock.sleep(1500)
        assertEquals("and it is home again", home, firstTop(s), 0.5f)
        shot("7-home", top = true)
    }

    /** The page read on under the glass: the frost has to follow what moves behind it. */
    @Test fun thePageScrollsUnderTheGlass() {
        val s = stage()
        val home = firstTop(s)
        val x = s.page.center.x
        sh("input swipe ${x.toInt()} ${(s.page.top + s.page.height * 0.62f).toInt()} ${x.toInt()} ${(s.page.top + s.page.height * 0.30f).toInt()} 450")
        SystemClock.sleep(1500)
        val after = firstTop(s)
        log("scroll under glass: first row $home -> $after")
        // the first row has gone off the top (or out of the list altogether): anywhere but where it was
        assertTrue("the page moved under the bar ($home -> $after)", after.isNaN() || kotlin.math.abs(after - home) > 100f)
        shot("8-scrolled")
        assertTrue("the bar is still there: ${s.bar}", s.bar.width > 0f)
    }

    /** The bar folding while it is glass: its body changes size on every frame of the fold, over a moving page. */
    @Test fun theGlassBarFoldsAsThePageIsRead() {
        val s = stage(folds = true)
        val open = s.bar
        val x = s.page.center.x
        sh("input swipe ${x.toInt()} ${(s.page.top + s.page.height * 0.62f).toInt()} ${x.toInt()} ${(s.page.top + s.page.height * 0.30f).toInt()} 450")
        SystemClock.sleep(1500)
        log("fold under glass: ${open.width} x ${open.height} -> ${s.bar.width} x ${s.bar.height}, folded ${s.fold?.folded}")
        assertTrue("the fold says folded", s.fold!!.folded)
        assertTrue("the glass bar is narrower and shorter folded ($open -> ${s.bar})", s.bar.width < open.width && s.bar.height < open.height)
        shot("9-folded-glass")
        sh("input swipe ${x.toInt()} ${(s.page.top + s.page.height * 0.40f).toInt()} ${x.toInt()} ${(s.page.top + s.page.height * 0.56f).toInt()} 300")
        SystemClock.sleep(1500)
        assertEquals("and opens to the size it was", open.width, s.bar.width, 1f)
        shot("9-open-again-glass")
    }

    /** How much colour shows through the bar's glass: the spread between a pixel's channels, averaged along a line just under the bar's top edge (above its icons). */
    private fun colourInTheGlass(s: Stage): Float {
        val d = act.window.decorView
        val bmp = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888)
        val l = CountDownLatch(1)
        PixelCopy.request(act.window, bmp, { l.countDown() }, Handler(copier.looper))
        l.await(3, TimeUnit.SECONDS)
        val den = ctx.resources.displayMetrics.density
        val y = (s.bar.top + 5 * den).toInt()
        var sum = 0f; var n = 0
        var x = (s.bar.left + 44 * den).toInt()
        while (x < s.bar.right - 44 * den) {
            val c = bmp.getPixel(x, y)
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            sum += maxOf(r, g, b) - minOf(r, g, b); n++
            x += 6
        }
        return if (n == 0) 0f else sum / n
    }

    /**
     * The frost is the page that is behind the bar now, not one the viewer has left. Seen wrong on an Android 12
     * emulator (11-12): Library and Profile, both black, under a bar still glowing with Home's posters.
     */
    @Test fun theFrostFollowsThePageBehindIt() {
        val s = stage(swaps = true)
        val first = colourInTheGlass(s)
        shot("10-frost-colour")
        ins.runOnMainSync { s.pageNo.intValue = 1 }
        SystemClock.sleep(1500)
        val gone = colourInTheGlass(s)
        shot("11-frost-nothing")
        ins.runOnMainSync { s.pageNo.intValue = 0 }
        SystemClock.sleep(1500)
        val back = colourInTheGlass(s)
        log("frost: colour $first, over a page of nothing $gone, over colour again $back")
        assertTrue("the page of colour shows through the glass ($first)", first > 18f)
        assertTrue("a page of nothing leaves the glass with no colour of the page before ($gone)", gone < 8f)
        assertTrue("and the colour is back with its page ($back)", back > 18f)
    }
}
