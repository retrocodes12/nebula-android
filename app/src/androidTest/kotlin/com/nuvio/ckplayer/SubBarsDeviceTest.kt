@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.ckplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.FrameLayout
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Black bars on a device (issue #2, nebula-player#8): a real PlayerView in a real window, wired by the same
 * SubStyle.mount the player uses, the subtitle views drawn to a bitmap and their white pixels read back. Sizes need not
 * fit the emulator's screen: a view laid out past the window still draws. The last test plays a letterboxed clip through
 * ExoPlayer and reads the screen itself, so the moved views are proven over a real video surface too.
 * PNGs go to /data/local/tmp/barshots (.github/workflows/device-tests.yml keeps them as an artifact).
 */
@RunWith(AndroidJUnit4::class)
class SubBarsDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = ins.targetContext
    private lateinit var act: Activity
    private fun log(s: String) = Log.i("BARS-DEVICE", s)

    private data class Box(val top: Int, val bottom: Int, val left: Int, val right: Int) {
        val h get() = bottom - top + 1
        val w get() = right - left + 1
    }
    private class Stage(val pv: PlayerView, val frame: AspectRatioFrameLayout, val own: SubtitleView, val over: SubtitleView, val chrome: BooleanArray)

    private val lines = listOf(Cue.Builder().setText("Hgjpqy subtitle line one\nand the second line here").build())

    @Before fun open() {
        ctx.getSharedPreferences("ckplayer", 0).edit().clear().commit()
        act = ins.startActivitySync(Intent(Intent.ACTION_MAIN).setClassName(ctx.packageName, "android.app.Activity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SubStyle.cycle(ctx, "size", 0)                       // chosen: the app's own look, not the system's caption style
    }

    @After fun close() {
        ins.runOnMainSync { act.finish() }
        ctx.getSharedPreferences("ckplayer", 0).edit().clear().commit()
    }

    private fun stage(w: Int, h: Int, aspect: Float, mode: Int, chrome: BooleanArray = booleanArrayOf(false)): Stage {
        lateinit var st: Stage
        ins.runOnMainSync {
            val pv = PlayerView(act)
            pv.useController = false
            pv.setShutterBackgroundColor(Color.TRANSPARENT)
            pv.setBackgroundColor(Color.BLACK)
            pv.resizeMode = mode
            val frame = pv.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
            frame.setAspectRatio(aspect)
            val over = SubStyle.mount(act, pv) { chrome[0] }
            val root = FrameLayout(act)
            root.addView(pv, FrameLayout.LayoutParams(w, h))
            act.setContentView(root)
            st = Stage(pv, frame, pv.subtitleView!!, over, chrome)
        }
        settle(st, w, h)
        return st
    }

    private fun settle(st: Stage, w: Int, h: Int) {
        val end = SystemClock.uptimeMillis() + 6000
        while (SystemClock.uptimeMillis() < end) {
            ins.waitForIdleSync()
            var ready = false
            ins.runOnMainSync {
                val oy = maxOf(0, (st.frame.height - h) / 2)
                ready = st.pv.width == w && st.pv.height == h && st.frame.height > 0 && st.own.paddingTop == oy
            }
            if (ready) return
            SystemClock.sleep(30)
        }
        throw AssertionError("the stage never settled")
    }

    /** What the player's restyle effect does whenever the style or the chrome changes. */
    private fun restyle(st: Stage) = ins.runOnMainSync {
        val views = listOf(st.own, st.over)
        views.forEach { SubStyle.apply(act, it) }
        SubStyle.place(act, views, st.pv, st.chrome[0])
    }

    private fun toBars() {
        while (SubStyle.get(ctx).getValue("pos") != SubStyle.BARS) SubStyle.cycle(ctx, "pos")
    }

    private fun whiteBox(b: Bitmap, x0: Int = 0, y0: Int = 0, x1: Int = b.width, y1: Int = b.height): Box? {
        var t = -1; var bo = -1; var l = Int.MAX_VALUE; var r = -1
        val row = IntArray(x1 - x0)
        for (y in y0 until y1) {
            b.getPixels(row, 0, row.size, x0, y, row.size, 1)
            for (i in row.indices) {
                val p = row[i]
                if (Color.red(p) > 205 && Color.green(p) > 205 && Color.blue(p) > 205 && Color.alpha(p) > 200) {
                    if (t < 0) t = y
                    bo = y
                    if (i + x0 < l) l = i + x0
                    if (i + x0 > r) r = i + x0
                }
            }
        }
        return if (t < 0) null else Box(t, bo, l, r)
    }

    private fun keep(b: Bitmap, name: String) {
        val dir = ctx.getExternalFilesDir("barshots")!!.also { it.mkdirs() }
        val f = File(dir, "$name.png")
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shell("mkdir -p /data/local/tmp/barshots")
        shell("cp ${f.absolutePath} /data/local/tmp/barshots/")
    }

    private fun shell(cmd: String) {
        val fd = ins.uiAutomation.executeShellCommand(cmd)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    /** Lines on one view, the other empty, the whole player drawn: where the white went. */
    private fun drawn(st: Stage, over: Boolean, name: String? = null): Box {
        lateinit var bmp: Bitmap
        ins.runOnMainSync {
            st.own.setCues(if (over) null else lines)
            st.over.setCues(if (over) lines else null)
            bmp = Bitmap.createBitmap(st.pv.width, st.pv.height, Bitmap.Config.ARGB_8888)
            st.pv.draw(Canvas(bmp))
        }
        name?.let { keep(bmp, it) }
        val box = whiteBox(bmp) ?: throw AssertionError("no line drawn" + (name?.let { " ($it)" } ?: ""))
        bmp.recycle()
        return box
    }

    private fun near(what: String, a: Int, b: Int, by: Int = 2) = assertTrue("$what: $a vs $b", abs(a - b) <= by)

    private fun sameSize(a: Box, b: Box) { near("line height", a.h, b.h); near("line width", a.w, b.w) }

    /** The film's frame and its bar, off and on, for the views in turn. */
    private fun onTheBar(w: Int, h: Int, name: String) {
        val st = stage(w, h, 2.39f, AspectRatioFrameLayout.RESIZE_MODE_FIT)
        val bottom = drawn(st, false, "$name-bottom")
        val bottomOver = drawn(st, true)
        toBars(); restyle(st)
        SystemClock.sleep(50)
        val own = drawn(st, false, "$name-bars-own")
        val over = drawn(st, true, "$name-bars-addon")
        var frameBottom = 0; var viewH = 0; var frameH = 0
        ins.runOnMainSync { frameBottom = st.frame.bottom; viewH = st.own.height; frameH = st.frame.height }
        log("$name frame ${st.frame.top}-$frameBottom · Bottom $bottom · Black bars own $own add-on $over")
        assertEquals("the view stays the picture's size", frameH, viewH)
        for (b in listOf(own, over)) {
            assertTrue("the whole block is on the bar: $b, frame ends $frameBottom", b.top >= frameBottom)
            assertTrue("nothing is cut off: $b of $h", b.bottom < h)
            near("the line sits at the true bottom", h - 1 - b.bottom, (h * 0.02f).toInt(), 14)
        }
        sameSize(bottom, own); sameSize(bottomOver, over)
    }

    @Test fun aWideFilmOnATvShapedScreen() = onTheBar(1920, 1080, "tv")

    @Test fun aWideFilmOnAnUprightPhoneKeepsItsSize() {
        onTheBar(1080, 2400, "upright")
        // the 3.6× trap: the size follows the picture's frame (452 px tall), not the 2400 px screen
        val st = stage(1080, 2400, 2.39f, AspectRatioFrameLayout.RESIZE_MODE_FIT)
        restyle(st)
        assertTrue("two lines stay small", drawn(st, false).h < 2400 * 0.04f)
    }

    /** With Black bars chosen and nothing to use, the lines rest exactly where Bottom does. */
    private fun sameAsBottom(w: Int, h: Int, aspect: Float, mode: Int, name: String) {
        val st = stage(w, h, aspect, mode)
        val bottom = drawn(st, false, "$name-bottom")
        val bottomOver = drawn(st, true)
        toBars(); restyle(st)
        SystemClock.sleep(50)
        val own = drawn(st, false, "$name-bars")
        val over = drawn(st, true)
        log("$name Bottom $bottom · Black bars $own add-on $over")
        assertEquals(bottom, own); assertEquals(bottomOver, over)
        assertTrue("inside the screen: $own", own.top >= 0 && own.bottom < h)
        var a = 1f; var b = 1f
        ins.runOnMainSync { a = st.own.translationY; b = st.over.translationY }
        assertEquals(0f, a); assertEquals(0f, b)
    }

    @Test fun aFilmWithNoBarsStaysWhereBottomIs() = sameAsBottom(1920, 1080, 16f / 9f, AspectRatioFrameLayout.RESIZE_MODE_FIT, "nobars")
    @Test fun sideBarsAreNotBlackBarsUnderThePicture() = sameAsBottom(1920, 1080, 4f / 3f, AspectRatioFrameLayout.RESIZE_MODE_FIT, "pillar")
    @Test fun fillKeepsItsLinesInsideTheScreen() = sameAsBottom(1920, 1080, 4f / 3f, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "fill")
    @Test fun stretchHasNoBars() = sameAsBottom(1920, 1080, 2.39f, AspectRatioFrameLayout.RESIZE_MODE_FILL, "stretch")

    @Test fun theChromeLiftsTheLinesAsEveryPositionDoes() {
        val chrome = booleanArrayOf(true)
        val st = stage(1920, 1080, 2.39f, AspectRatioFrameLayout.RESIZE_MODE_FIT, chrome)
        restyle(st)
        val bottom = drawn(st, false, "chrome-bottom")
        toBars(); restyle(st)
        assertEquals(bottom, drawn(st, false, "chrome-bars"))
        var moved = 1f
        ins.runOnMainSync { moved = st.own.translationY }
        assertEquals(0f, moved)
        chrome[0] = false; restyle(st)                       // the chrome fades: back on the bar
        var frameBottom = 0
        ins.runOnMainSync { frameBottom = st.frame.bottom }
        assertTrue(drawn(st, false).top >= frameBottom)
    }

    @Test fun theFilmsShapeArrivingLaterIsFollowed() {
        // a player starts with no picture size: the frame fills the screen, then the film's shape lays it out again
        toBars()
        val st = stage(1920, 1080, 16f / 9f, AspectRatioFrameLayout.RESIZE_MODE_FIT)
        restyle(st)
        ins.runOnMainSync { st.frame.setAspectRatio(2.39f) }
        val end = SystemClock.uptimeMillis() + 3000
        var moved = 0f
        while (SystemClock.uptimeMillis() < end && moved == 0f) { ins.runOnMainSync { moved = st.own.translationY }; SystemClock.sleep(20) }
        var frameBottom = 0
        ins.runOnMainSync { frameBottom = st.frame.bottom }
        assertEquals((1080 - frameBottom).toFloat(), moved)
        assertTrue(drawn(st, false).top >= frameBottom)
    }

    // ---- the synced document never hears of it ----

    @Test fun blackBarsStaysOutOfTheSyncedDocument() {
        val at = SubStyle.at(ctx)
        toBars()
        assertEquals(SubStyle.BARS, SubStyle.get(ctx).getValue("pos"))
        assertTrue("every position in the document is one every app knows", SubStyle.synced(ctx).getValue("pos") in listOf("bottom", "raised", "high", "centre"))
        assertEquals("choosing it dates nothing", at, SubStyle.at(ctx))
        val doc = ctx.getSharedPreferences("ckplayer", 0).getString("sub_style", "")!!
        assertFalse(doc, doc.contains("bars"))
    }

    @Test fun aStyleSyncedInFromAnotherDeviceLeavesItAlone() {
        toBars()
        SubStyle.applyRemote(ctx, JSONObject().put("pos", "raised").put("size", "large"), SubStyle.at(ctx) + 1000)
        assertEquals(SubStyle.BARS, SubStyle.get(ctx).getValue("pos"))
        assertEquals("large", SubStyle.get(ctx).getValue("size"))
        assertEquals("raised", SubStyle.synced(ctx).getValue("pos"))
    }

    @Test fun aStrayBarsInADocumentIsReadAsBottom() {
        SubStyle.applyRemote(ctx, JSONObject().put("pos", "bars"), SubStyle.at(ctx) + 1000)
        assertEquals("bottom", SubStyle.get(ctx).getValue("pos"))
        assertFalse(SubStyle.barsOn(ctx))
    }

    @Test fun thePositionRowWalksIntoAndOutOfIt() {
        assertEquals("bottom", SubStyle.get(ctx).getValue("pos"))
        assertEquals("from Bottom, one step back", SubStyle.BARS, SubStyle.cycle(ctx, "pos", -1))
        val at = SubStyle.at(ctx)
        SystemClock.sleep(5)
        assertEquals("and on round to Bottom, which syncs like any choice", "bottom", SubStyle.cycle(ctx, "pos"))
        assertFalse(SubStyle.barsOn(ctx))
        assertNotEquals(at, SubStyle.at(ctx))
        toBars()
        SubStyle.reset(ctx)
        assertFalse("Reset takes it too", SubStyle.barsOn(ctx))
        assertEquals("Black bars", SubStyle.VALUE_LABELS[SubStyle.BARS])
    }

    @Test fun itWorksBeforeAnyStyleWasEverChosen() {
        ctx.getSharedPreferences("ckplayer", 0).edit().clear().commit()
        assertEquals(0L, SubStyle.at(ctx))
        val st = stage(1920, 1080, 2.39f, AspectRatioFrameLayout.RESIZE_MODE_FIT)
        toBars(); restyle(st)
        assertEquals("still the system's own look", 0L, SubStyle.at(ctx))
        var frameBottom = 0
        ins.runOnMainSync { frameBottom = st.frame.bottom }
        assertTrue(drawn(st, false).top >= frameBottom)
    }

    @Test fun aPickedLanguageTurnsAutoloadOnAtOnce() {
        ctx.getSharedPreferences("ckplayer", 0).edit().clear().commit()
        Prefs.load(ctx)
        assertEquals("off", Prefs.subStart)
        Prefs.setSubLang(ctx, "hr")
        assertEquals("the rule the next launch applies, now", "preferred", Prefs.subStart)
        Prefs.load(ctx)
        assertEquals("preferred", Prefs.subStart)
        Prefs.setSubStart(ctx, "off")
        Prefs.setSubLang(ctx, "de")
        assertEquals("an Off chosen by hand stands", "off", Prefs.subStart)
    }

    // ---- a real film on a real surface ----

    @Test fun overARealVideoSurface() {
        val dir = ctx.cacheDir
        fun asset(n: String) = File(dir, n).also { f -> ins.context.assets.open(n).use { i -> f.outputStream().use { i.copyTo(it) } } }
        val film = asset("letter.mp4"); val vtt = asset("letter.vtt")
        var w = 0
        ins.runOnMainSync { w = act.window.decorView.width }
        val h = w * 9 / 16
        val st = stage(w, h, 2.39f, AspectRatioFrameLayout.RESIZE_MODE_FIT)
        lateinit var exo: ExoPlayer
        ins.runOnMainSync {
            exo = ExoPlayer.Builder(act).build()
            st.pv.player = exo
            exo.setMediaItem(
                MediaItem.Builder().setUri(Uri.fromFile(film)).setSubtitleConfigurations(listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(vtt)).setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("en").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
                )).build()
            )
            exo.prepare(); exo.playWhenReady = true
        }
        var pos = 0L
        val end = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < end && pos < 1000) { ins.runOnMainSync { pos = exo.currentPosition }; SystemClock.sleep(100) }
        assertTrue("the clip played: $pos ms", pos >= 1000)
        fun screen(name: String): Pair<Box?, Box?> {
            SystemClock.sleep(600)
            val shot = ins.uiAutomation.takeScreenshot()!!
            keep(shot, name)
            val at = IntArray(2); var top = 0; var bottom = 0
            ins.runOnMainSync { st.pv.getLocationOnScreen(at); top = st.frame.top; bottom = st.frame.bottom }
            val picture = whiteBox(shot, 0, at[1] + top, shot.width, at[1] + bottom)
            val bar = whiteBox(shot, 0, at[1] + bottom, shot.width, minOf(shot.height, at[1] + h))
            log("$name on the picture $picture · on the bar $bar · player at y=${at[1]} frame $top-$bottom")
            return picture to bar
        }
        val (p0, b0) = screen("real-bottom")
        assertTrue("Bottom: the line is on the picture", p0 != null && b0 == null)
        toBars(); restyle(st)
        val (p1, b1) = screen("real-bars-own")
        assertTrue("Black bars: the line left the picture for the bar", p1 == null && b1 != null)
        ins.runOnMainSync {
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            st.over.setCues(lines)
        }
        val (p2, b2) = screen("real-bars-addon")
        assertTrue("an add-on file too", p2 == null && b2 != null)
        ins.runOnMainSync { st.pv.player = null; exo.release() }
    }
}
