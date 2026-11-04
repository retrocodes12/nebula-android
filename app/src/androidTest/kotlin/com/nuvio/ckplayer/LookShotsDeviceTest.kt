package com.nuvio.ckplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.compose.AsyncImage
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Pictures of the phone's look (11-04, Nuvio's layout): the Continue watching cards upright, the player's controls
 * with the Next Episode card on its side. Screenshots to /data/local/tmp/barshots (kept by device-tests.yml) — the
 * claim is in the pictures; the test only checks something was drawn.
 */
@RunWith(AndroidJUnit4::class)
class LookShotsDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = ins.targetContext
    private var act: Activity? = null

    private class Owner : LifecycleOwner, SavedStateRegistryOwner, androidx.lifecycle.ViewModelStoreOwner,
        androidx.activity.OnBackPressedDispatcherOwner {
        private val reg = LifecycleRegistry(this)
        override val viewModelStore = androidx.lifecycle.ViewModelStore()
        override val onBackPressedDispatcher = androidx.activity.OnBackPressedDispatcher()
        private val ss = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = reg
        override val savedStateRegistry: SavedStateRegistry get() = ss.savedStateRegistry
        init { ss.performRestore(null); reg.currentState = Lifecycle.State.RESUMED }
    }

    /** A still for the art: a cool gradient with a light patch, like a lit scene. */
    private fun still(name: String, a: Int, b: Int): String {
        val bmp = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, 1280f, 720f, Paint().apply { shader = LinearGradient(0f, 0f, 1280f, 720f, a, b, Shader.TileMode.CLAMP) })
        c.drawCircle(820f, 260f, 180f, Paint().apply { color = 0x55FFFFFF })
        c.drawRect(380f, 300f, 520f, 720f, Paint().apply { color = 0xFF101018.toInt() })
        val f = File(ctx.cacheDir, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "file://" + f.absolutePath
    }

    private fun show(content: @Composable () -> Unit) {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).allowHardware(false).build())
        act = ins.startActivitySync(Intent(Intent.ACTION_MAIN).setClassName(ctx.packageName, "android.app.Activity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ins.runOnMainSync {
            val owner = Owner()
            val v = ComposeView(act!!)
            v.setViewTreeLifecycleOwner(owner)
            v.setViewTreeSavedStateRegistryOwner(owner)
            act!!.window.decorView.setViewTreeLifecycleOwner(owner)
            act!!.window.decorView.setViewTreeSavedStateRegistryOwner(owner)
            listOf(v, act!!.window.decorView).forEach {
                it.setViewTreeViewModelStoreOwner(owner)
                it.setViewTreeOnBackPressedDispatcherOwner(owner)
            }
            v.setContent(content)
            act!!.setContentView(v)
        }
        ins.waitForIdleSync()
        SystemClock.sleep(2500)          // the pictures arrive (Coil) and the fades finish
    }

    private fun shot(name: String) {
        // drawn in software from the view itself (the emulator went down taking screenshots of a hardware surface)
        var b: Bitmap? = null
        ins.runOnMainSync {
            val v = act!!.window.decorView
            b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        assertTrue("nothing on screen", b != null && b!!.width > 0)
        val f = File(ctx.getExternalFilesDir("barshots")!!.also { it.mkdirs() }, "$name.png")
        f.outputStream().use { b!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shell("mkdir -p /data/local/tmp/barshots")
        shell("cp ${f.absolutePath} /data/local/tmp/barshots/")
    }

    private fun shell(cmd: String) {
        val fd = ins.uiAutomation.executeShellCommand(cmd)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    @After fun close() {
        act?.let { a -> ins.runOnMainSync { a.finish() } }
    }

    @Test fun continueCards() {
        val ep = still("ep", 0xFF16305C.toInt(), 0xFF5A7FC0.toInt())
        val film = still("film", 0xFF3A2414.toInt(), 0xFFB07040.toInt())
        show {
            Column(Modifier.fillMaxSize().background(Bg).padding(top = 80.dp)) {
                Text("Continue watching", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp))
                Row(Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ContinueCard(
                        ProgressRec("series", "tt1:1:8", "Carrie · S1E8 · AP Physics", ep, "landscape", "", 31 * 60_000L, 44 * 60_000L, at = 2),
                        Modifier.width(continueCardWidth()), onClick = {}, onLongClick = {},
                    )
                    ContinueCard(
                        ProgressRec("movie", "tt2", "Person of Interest", film, "landscape", "", 12 * 60_000L, 118 * 60_000L, at = 1),
                        Modifier.width(continueCardWidth()), onClick = {}, onLongClick = {},
                    )
                }
            }
        }
        shot("look-continue")
    }

    @Test fun playerControls() {
        val ep = still("ep2", 0xFF0F1830.toInt(), 0xFF40608F.toInt())
        val clock = PlayerClock().apply { pos = 50 * 60_000L + 52_000L; dur = 52 * 60_000L + 55_000L; buf = 51 * 60_000L + 40_000L }
        val tools = phoneTools(
            hasNext = true, showSubtitles = true, showAudio = true, qualityLabel = "1080p", speedLabel = "1.0×",
            fitLabel = "Fit", sleepLabel = null, partyActive = false, canInvite = true, infoOn = false, canPip = true,
            canFullscreen = true, subtitlesFocus = null, sleepFocus = null,
            onNext = {}, onSubtitles = {}, onAudio = {}, onQuality = {}, onStreams = {}, onSpeedCycle = {}, onFitCycle = {},
            onSleep = {}, onParty = {}, onInvite = {}, onReact = {}, onInfo = {}, onPip = {}, onFullscreen = {},
        )
        val next = Episode("tt1:1:8", 1, 8, "AP Physics", null, ep)
        // a phone on its side, drawn turned a quarter on the upright screen (turning the emulator itself took it down)
        show {
            Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
            Box(Modifier.requiredSize(860.dp, 396.dp).graphicsLayer { rotationZ = 90f }.background(Color.Black)) {
                AsyncImage(model = ep, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                PhoneChrome(
                    visible = true, title = "Carrie", episodeTag = "S1 E7 · Prom Night", sourceLine = "1080p · Torrentio",
                    isPlaying = false, isLive = false, clock = clock, partyBadge = null, showClock = false, dimTitle = false,
                    tools = tools, seekStepMs = 10_000L, scrubFrame = null, playFocus = null,
                    onBack = {}, onLock = {}, onPlayPause = {}, onSeekBy = {}, onSeekTo = {}, onScrub = {}, onGoLive = {},
                )
                NextEpisodeCard(
                    next, null, "Next Episode", null, null, FocusRequester(), {}, {},
                    Modifier.align(Alignment.BottomEnd).padding(start = 20.dp, end = 20.dp, bottom = 104.dp),
                )
            }
            }
        }
        shot("look-player")
    }
}
