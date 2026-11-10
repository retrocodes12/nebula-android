package com.nuvio.ckplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntSize
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The phone's nav pill folds to its icons while a page is read and opens on the way back up (NavFold.kt, BottomBar).
 *
 * A real window, a real list and the real pill, wired the way AppRoot wires them (rememberNavFold's connection on the
 * box that holds the page, its answer handed to the pill), driven by real swipes from the shell — so what is proven is
 * the whole path: a finger, the list, the scroll passing out through the connection, the decision, the pill's size.
 * The pill is measured after every layout, so the sizes on the way are known too: a fold that cut from one size to
 * the other would show as two sizes and nothing between.
 *
 * Pictures of the bottom of the screen go to /data/local/tmp/barshots (device-tests.yml keeps them as an artifact).
 */
@RunWith(AndroidJUnit4::class)
class NavFoldDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = ins.targetContext
    private lateinit var act: Activity
    private val copier = HandlerThread("navfold-copy").apply { start() }
    private val owners = ArrayList<Owner>()
    private fun log(s: String) = Log.i("NAVFOLD-DEVICE", s)

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

    /** What a staging hands back: the pill's size after every layout, the fold's own answer, and where to swipe. */
    private class Stage {
        val sizes = CopyOnWriteArrayList<IntSize>()
        @Volatile var fold: NavFoldState? = null
        @Volatile var page = Rect.Zero
        @Volatile var row = Rect.Zero
        val forced = mutableStateOf<Boolean?>(null)      // null = the scroll decides; else the test does
    }

    private fun stage(): Stage {
        val s = Stage()
        ins.runOnMainSync {
            val owner = Owner(); owners += owner
            act.window.decorView.setViewTreeLifecycleOwner(owner)
            act.window.decorView.setViewTreeSavedStateRegistryOwner(owner)
            owner.start()
            val view = ComposeView(act)
            view.setContent {
                val fold = rememberNavFold()
                s.fold = fold
                Box(
                    Modifier.fillMaxSize().background(Color.Black)
                        .onGloballyPositioned { s.page = it.boundsInWindow() }
                        .nestedScroll(fold.connection)
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        // a row that slides sideways, as Home's are
                        item {
                            LazyRow(Modifier.fillMaxWidth().height(160.dp).onGloballyPositioned { s.row = it.boundsInWindow() }) {
                                items(30) { i ->
                                    Box(Modifier.padding(6.dp).size(220.dp, 148.dp).background(if (i % 2 == 0) Color(0xFF2A3A5A) else Color(0xFF3A2A5A)))
                                }
                            }
                        }
                        items(80) { i ->
                            Box(Modifier.fillMaxWidth().height(96.dp).padding(6.dp).background(if (i % 2 == 0) Color(0xFF202028) else Color(0xFF34343E)))
                        }
                    }
                    NavPillForTest(
                        folded = s.forced.value ?: fold.folded,
                        modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { s.sizes += it },
                    )
                }
            }
            act.setContentView(view)
        }
        return s
    }

    /** The pill's size once it has stopped changing. */
    private fun settled(s: Stage): IntSize {
        var last = s.sizes.lastOrNull()
        var since = SystemClock.uptimeMillis()
        val end = since + 8000
        while (SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(50)
            val now = s.sizes.lastOrNull()
            if (now != last) { last = now; since = SystemClock.uptimeMillis() }
            else if (now != null && SystemClock.uptimeMillis() - since > 800) break
        }
        return last ?: throw AssertionError("the pill never measured")
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Int) =
        sh("input swipe ${x1.toInt()} ${y1.toInt()} ${x2.toInt()} ${y2.toInt()} $ms")

    /** The bottom third of the window as it was last drawn: the pill and what is under it. */
    private fun shot(name: String) {
        val d = act.window.decorView
        val w = d.width; val h = d.height
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val l = CountDownLatch(1)
        var ok = false
        PixelCopy.request(act.window, bmp, { r -> ok = r == PixelCopy.SUCCESS; l.countDown() }, Handler(copier.looper))
        l.await(3, TimeUnit.SECONDS)
        if (!ok) { log("picture $name: the copy failed"); return }
        val top = h * 2 / 3
        val cut = Bitmap.createBitmap(bmp, 0, top, w, h - top)
        val dir = ctx.getExternalFilesDir("barshots")!!.also { it.mkdirs() }
        val f = File(dir, "navfold-$name.png")
        f.outputStream().use { cut.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sh("mkdir -p /data/local/tmp/barshots")
        sh("cp ${f.absolutePath} /data/local/tmp/barshots/")
    }

    @Test fun aSwipeUpThePageFoldsThePillAndASwipeBackOpensIt() {
        val s = stage()
        val open = settled(s)
        val d = ctx.resources.displayMetrics.density
        log("open: $open, page ${s.page}, row ${s.row}, density $d")
        assertTrue("the pill is on screen: $open", open.width > 0 && open.height > 0)
        assertFalse("the pill starts whole", s.fold!!.folded)
        shot("1-open")

        // a row sliding sideways is not the page being read
        val ry = s.row.center.y
        swipe(s.page.right * 0.8f, ry, s.page.right * 0.2f, ry, 300)
        assertEquals("a row sliding sideways leaves the pill alone", open, settled(s))
        assertFalse("a row sliding sideways does not fold", s.fold!!.folded)

        // reading on: a finger going up the page
        val x = s.page.center.x
        val before = s.sizes.size
        swipe(x, s.page.top + s.page.height * 0.62f, x, s.page.top + s.page.height * 0.30f, 450)
        val shut = settled(s)
        val onTheWay = s.sizes.subList(before, s.sizes.size).toList()
        log("folded: $shut, on the way: " + onTheWay.joinToString(" ") { it.width.toString() + "x" + it.height })
        assertTrue("the fold says folded after a swipe up the page", s.fold!!.folded)
        assertTrue("the folded pill is shorter by its names ($open -> $shut)", shut.height <= open.height - (10 * d).toInt())
        assertTrue("the folded pill is narrower ($open -> $shut)", shut.width <= open.width - (20 * d).toInt())
        assertTrue("the folded pill still holds its five icons ($shut)", shut.width >= (5 * 32 * d).toInt() && shut.height >= (32 * d).toInt())
        // a move, not a cut: sizes between the two were laid out on the way
        val between = onTheWay.map { it.height }.distinct().count { it < open.height && it > shut.height }
        assertTrue("the pill passes through sizes between open and folded ($onTheWay)", between >= 2)
        shot("2-folded")

        // a short move back: a finger going down the page
        swipe(x, s.page.top + s.page.height * 0.40f, x, s.page.top + s.page.height * 0.56f, 300)
        val again = settled(s)
        log("open again: $again")
        assertFalse("the fold says open after a move back", s.fold!!.folded)
        assertEquals("the pill is its old size again", open, again)
        shot("3-open-again")
    }

    /** For the eye, not the assert: the fold caught part-way, both directions (the pictures are the result). */
    @Test fun theFoldInPictures() {
        val s = stage()
        val open = settled(s)
        ins.runOnMainSync { s.forced.value = true }
        for ((i, wait) in listOf(50L, 60L, 70L, 90L).withIndex()) { SystemClock.sleep(wait); shot("fold-" + (i + 1)) }
        val shut = settled(s)
        ins.runOnMainSync { s.forced.value = false }
        for ((i, wait) in listOf(50L, 60L, 70L, 90L).withIndex()) { SystemClock.sleep(wait); shot("unfold-" + (i + 1)) }
        val again = settled(s)
        log("pictures: open $open, folded $shut, open again $again")
        assertTrue("folded is smaller both ways ($open -> $shut)", shut.width < open.width && shut.height < open.height)
        assertEquals("and it comes back to the size it was", open, again)
    }
}
