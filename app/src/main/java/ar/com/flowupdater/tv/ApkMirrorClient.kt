package ar.com.flowupdater.tv

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Navega APKMirror por código (WebView real, con JS) para encontrar la
 * última versión de Flow y, después, el link de descarga real del bundle
 * .apkm. Confirmado con un smoke test en dispositivo real: esta cadena no
 * dispara ningún desafío de Cloudflare — si algún día empieza a aparecer
 * uno, onChallenge() es el único lugar que hay que revisar.
 */
class ApkMirrorClient(private val context: Context, private val container: FrameLayout) {

    companion object {
        private const val GROUP_URL =
            "https://www.apkmirror.com/apk/cablevision-fibertel/flow-android-tv-android-tv/"
        private val CHALLENGE_MARKERS =
            listOf("Just a moment", "cf-turnstile", "challenge-platform", "Verifying you are human")
    }

    private var webView: WebView? = null

    interface VersionCallback {
        fun onVersion(versionName: String, releaseUrl: String)
        fun onError(message: String)
    }

    interface DownloadCallback {
        fun onFileUrl(url: String, mimeType: String?)
        fun onChallenge(message: String)
        fun onError(message: String)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(): WebView {
        webView?.let { container.removeView(it); it.destroy() }
        val wv = WebView(context)
        wv.layoutParams = FrameLayout.LayoutParams(1, 1)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        container.addView(wv)
        webView = wv
        return wv
    }

    fun release() {
        webView?.let { container.removeView(it); it.destroy() }
        webView = null
    }

    fun checkLatestVersion(callback: VersionCallback) {
        val wv = newWebView()
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript("document.title") { rawTitle ->
                    val title = unescape(rawTitle)
                    if (isChallenge(title)) {
                        callback.onError("Apareció una verificación en APKMirror, probá de nuevo en un rato")
                        return@evaluateJavascript
                    }
                    if (url == GROUP_URL) {
                        view.evaluateJavascript("document.documentElement.outerHTML") { rawHtml ->
                            val html = unescape(rawHtml)
                            val releaseHref =
                                Regex("""href="(/apk/[^"]*flow-android-tv[^"]*-release/)"""")
                                    .find(html)?.groupValues?.get(1)
                            if (releaseHref == null) {
                                callback.onError("No encontré la página de la última versión (¿cambió el sitio?)")
                            } else {
                                view.loadUrl("https://www.apkmirror.com$releaseHref")
                            }
                        }
                    } else {
                        val version = Regex("""Flow Android TV ([0-9.]+)""").find(title)?.groupValues?.get(1)
                        if (version == null) {
                            callback.onError("No pude leer la versión desde: $title")
                        } else {
                            callback.onVersion(version, url)
                        }
                    }
                }
            }
        }
        wv.loadUrl(GROUP_URL)
    }

    fun downloadLatest(releaseUrl: String, callback: DownloadCallback) {
        val wv = newWebView()
        var step = 0
        wv.setDownloadListener { url, _, _, mimeType, _ ->
            callback.onFileUrl(url, mimeType)
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript("document.documentElement.outerHTML") { rawHtml ->
                    val html = unescape(rawHtml)
                    if (isChallenge(html)) {
                        callback.onChallenge("Verificación de APKMirror en pantalla, resolvela con el control remoto")
                        return@evaluateJavascript
                    }
                    val next = when (step) {
                        0 -> Regex("""href="([^"]*-android-apk-download/)"""").find(html)?.groupValues?.get(1)
                        1 -> Regex("""href="([^"]*/download/\?key=[^"]*)"""").find(html)?.groupValues?.get(1)
                        else -> null
                    }
                    if (next != null) {
                        step++
                        val nextUrl = if (next.startsWith("http")) next else "https://www.apkmirror.com$next"
                        view.loadUrl(nextUrl)
                    }
                    // si next da null en el paso 2 (página con el token), esperamos:
                    // el archivo real dispara setDownloadListener solo.
                }
            }
        }
        wv.loadUrl(releaseUrl)
    }

    private fun isChallenge(text: String): Boolean = CHALLENGE_MARKERS.any { text.contains(it, ignoreCase = true) }

    private fun unescape(raw: String?): String {
        if (raw == null) return ""
        var s = raw
        if (s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length - 1)
        return s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
    }
}
