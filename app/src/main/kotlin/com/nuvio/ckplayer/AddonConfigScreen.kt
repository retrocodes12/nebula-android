package com.nuvio.ckplayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.PersistableBundle
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The manifest address on the clipboard. Flagged sensitive: an address can carry the viewer's service keys, and Android
    13+ then keeps it out of the copy preview and the keyboard's suggestions (the recovery key's rule, Profile.kt). */
internal fun copyAddonLink(ctx: Context, a: Addon) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Add-on manifest URL", a.manifestUrl)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    runCatching { cm?.setPrimaryClip(clip) }
    // Android 13+ phones say so themselves; a TV does not
    if (Build.VERSION.SDK_INT < 33 || Account.isTv(ctx)) Toasts.show("Copied")
}

/**
 * An add-on's settings page, inside the app and not in a browser: the page ends in an install link only the Stremio app
 * can follow, so here the link is caught and the add-on keeps its place in the list with the new address. A page cannot
 * reach the app: no JavaScript interface, and only web addresses load. It is the add-on author's page, so the swap asks
 * first, as an install does, and names the host when the add-on would move to another.
 */
@Composable
internal fun AddonConfigScreen(addon: Addon, onBack: () -> Unit, onSave: (ManifestInfo) -> Unit) {
    val scope = rememberCoroutineScope()
    val remote = remoteMode()
    val page = remember(addon) { AddonConfig.pageUrl(addon.manifestUrl) }
    // a TV box may carry no web view at all, and building one there throws
    val usable = remember { runCatching { WebView.getCurrentWebViewPackage() != null }.getOrDefault(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(page == null || !usable) }
    var loading by remember { mutableStateOf(true) }
    var canBack by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<ManifestInfo?>(null) }

    fun take(link: String) {
        if (checking || pending != null) return
        if (link == addon.manifestUrl) { Toasts.show("These settings are already saved"); onBack(); return }
        checking = true; note = ""
        scope.launch {
            runCatching { Stremio.loadManifest(link) }
                .onSuccess { pending = it }
                .onFailure {
                    if (it is CancellationException) throw it
                    note = "Couldn’t read the new settings. " + addonTrouble(it)
                }
            checking = false
        }
    }

    BackHandler(enabled = canBack && !failed) { web?.goBack() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
            BackBar("Configure", addon.name, onBack)
            if (checking || note.isNotEmpty()) Text(
                if (checking) "Reading the new settings…" else note,
                color = if (checking) MutedC else Color(0xFFFF6B6B), fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (failed) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (usable) "Couldn’t open this add-on’s settings." else "This device has no way to show web pages.",
                        color = TextC, fontSize = 15.sp, textAlign = TextAlign.Center,
                    )
                    if (usable && page != null) Chip("Retry", false) { failed = false; loading = true; attempt++ }
                }
            } else key(attempt) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        WebView(c).apply {
                            settings.javaScriptEnabled = true    // every settings page is a script
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.setSupportMultipleWindows(false)
                            isFocusable = true
                            isFocusableInTouchMode = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val address = request.url.toString()
                                    val link = if (request.isForMainFrame) AddonConfig.installLink(address) else null
                                    if (link != null) { take(link); return true }
                                    return webUrl(address).isEmpty()   // intent:, market:, file: stay out
                                }
                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { loading = true }
                                override fun onPageFinished(view: WebView, url: String?) {
                                    loading = false
                                    if (remote) view.requestFocus()
                                }
                                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                    canBack = view.canGoBack()
                                }
                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    if (request.isForMainFrame) failed = true
                                }
                                // left unanswered, a renderer that dies takes the whole app down with it
                                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                    failed = true
                                    return true
                                }
                            }
                            web = this
                            page?.let { loadUrl(it) }
                        }
                    },
                    onRelease = { it.stopLoading(); it.destroy() },
                )
            }
            if (loading && !failed) LinearProgressIndicator(
                Modifier.fillMaxWidth().height(2.dp), color = TextC, trackColor = Color.Transparent,
            )
        }
    }

    pending?.let { info ->
        val from = AddonConfig.host(addon.manifestUrl)
        val to = AddonConfig.host(info.addon.manifestUrl)
        ConfirmSheet(
            title = "Save these settings?",
            text = if (to == from) "${addon.name} will use the settings you just chose."
                else "${addon.name} will be replaced by ${info.addon.name} from $to.",
            action = "Save",
            onConfirm = { onSave(info) },
            onDismiss = { pending = null },
        )
    }
}
