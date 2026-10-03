package com.nuvio.ckplayer

import android.graphics.SurfaceTexture
import android.media.MediaCodecList
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.AssetDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderManager
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * AV1 with no AV1 chip ("so many dropped frames in android": AV1 1080p, 1316 of 1590 dropped, 2026-10-02). The emulator
 * has no AV1 chip either, so this plays the same 1080p AV1 clip (Sintel trailer, CC BY — encoded by device-tests.yml,
 * never committed) through Android's own decoder (what AUTO picked) and through nextlib's FFmpeg (dav1d, what 1.85.2
 * switches to), the player built as PlayerScreen builds it, into a Surface that takes every frame. At 1× and at 4× (the
 * processor's headroom: a phone has far less than this runner). Rendered and dropped frames are logged per run.
 */
@RunWith(AndroidJUnit4::class)
class Av1DecodeDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private fun log(s: String) = Log.i("AV1-DEVICE", s)

    /** A Surface that consumes every frame: a SurfaceTexture on its own GL context, drained on its own thread. */
    private class Sink : AutoCloseable {
        private val thread = HandlerThread("av1-sink").apply { start() }
        private val handler = Handler(thread.looper)
        private var dpy = EGL14.EGL_NO_DISPLAY
        private var ctx = EGL14.EGL_NO_CONTEXT
        private var pb = EGL14.EGL_NO_SURFACE
        lateinit var st: SurfaceTexture
        lateinit var surface: Surface
        init {
            val ready = CountDownLatch(1)
            handler.post {
                dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                val ver = IntArray(2); EGL14.eglInitialize(dpy, ver, 0, ver, 1)
                val cfgs = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
                EGL14.eglChooseConfig(dpy, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE), 0, cfgs, 0, 1, n, 0)
                ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
                pb = EGL14.eglCreatePbufferSurface(dpy, cfgs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
                EGL14.eglMakeCurrent(dpy, pb, pb, ctx)
                val tex = IntArray(1); GLES20.glGenTextures(1, tex, 0)
                st = SurfaceTexture(tex[0]); st.setDefaultBufferSize(1920, 1080)
                st.setOnFrameAvailableListener({ runCatching { it.updateTexImage() } }, handler)
                surface = Surface(st)
                ready.countDown()
            }
            check(ready.await(10, TimeUnit.SECONDS)) { "no GL sink" }
        }
        override fun close() {
            val done = CountDownLatch(1)
            handler.post {
                runCatching { surface.release(); st.release() }
                EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(dpy, pb); EGL14.eglDestroyContext(dpy, ctx); EGL14.eglTerminate(dpy)
                done.countDown()
            }
            done.await(5, TimeUnit.SECONDS); thread.quitSafely()
        }
    }

    private class Run(val decoder: String?, val dropped: Int, val rendered: Int) {
        override fun toString() = "$decoder · rendered $rendered · dropped $dropped (${if (rendered + dropped > 0) 100 * dropped / (rendered + dropped) else 0} %)"
    }

    private fun play(mode: DecoderMode, speed: Float, secs: Long): Run = Sink().use { sink ->
        lateinit var exo: ExoPlayer
        val dm = DecoderManager()
        var name: String? = null
        ins.runOnMainSync {
            exo = ExoPlayer.Builder(ins.targetContext)
                .setRenderersFactory(NextRenderersFactory(ins.targetContext).setDecoderManager(dm))
                .setMediaSourceFactory(DefaultMediaSourceFactory(DataSource.Factory { AssetDataSource(ins.context) }))
                .build().apply { dm.attach(this) }
            dm.selectVideoDecoder(mode)
            exo.addAnalyticsListener(object : AnalyticsListener {
                override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { name = decoderName }
            })
            exo.setVideoSurface(sink.surface); exo.volume = 0f
            exo.setMediaItem(MediaItem.fromUri("asset:///av1.mkv"))
            exo.setPlaybackSpeed(speed)
            exo.prepare(); exo.play()
        }
        Thread.sleep(secs * 1000)
        var dropped = 0; var rendered = 0
        ins.runOnMainSync {
            exo.videoDecoderCounters?.let { it.ensureUpdated(); dropped = it.droppedBufferCount; rendered = it.renderedOutputBufferCount }
            dm.detach(); exo.release()
        }
        Run(name, dropped, rendered)
    }

    @Test fun dav1dKeepsUpWhereAndroidsOwnDecoderDoesNot() {
        val av1 = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { ci -> !ci.isEncoder && ci.supportedTypes.any { it.equals(MimeTypes.VIDEO_AV1, true) } }
        log("AV1 decoders on this device: " + av1.joinToString { it.name + (if (it.isHardwareAccelerated) " (hw)" else " (sw)") } + " · hasHardwareAv1 = $hasHardwareAv1")
        val auto1 = play(DecoderMode.AUTO, 1f, 10); log("Android's own, 1×: $auto1")
        val ff1 = play(DecoderMode.FFMPEG, 1f, 10); log("FFmpeg (dav1d), 1×: $ff1")
        val auto4 = play(DecoderMode.AUTO, 4f, 4); log("Android's own, 4×: $auto4")
        val ff4 = play(DecoderMode.FFMPEG, 4f, 4); log("FFmpeg (dav1d), 4×: $ff4")
        assertNotNull("the FFmpeg path initialised a decoder", ff1.decoder)
        assertTrue("the FFmpeg path drew frames: $ff1", ff1.rendered > 60)
        // the claim the fix rests on: under load dav1d shows at least as many frames as Android's own decoder
        assertTrue("dav1d under load: $ff4 vs $auto4", ff4.rendered >= auto4.rendered * 9 / 10)
    }
}
