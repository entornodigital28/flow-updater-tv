package ar.com.flowupdater.tv

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
class ApkMirrorClient(private val context: Context, private val container: FrameLayout) : ApkSourceClient {

    override val name = "APKMirror"

    companion object {
        private const val GROUP_URL =
            "https://www.apkmirror.com/apk/cablevision-fibertel/flow-android-tv-android-tv/"
    }

    private var releaseUrl: String? = null

    override fun checkLatestVersion(callback: ApkSourceClient.VersionCallback) {
        val wv = WebViewUtil.newWebView(context, container)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript("document.title") { rawTitle ->
                    val title = WebViewUtil.unescapeJs(rawTitle)
                    if (WebViewUtil.isChallenge(title)) {
                        callback.onError("Apareció una verificación en APKMirror")
                        return@evaluateJavascript
                    }
                    if (url == GROUP_URL) {
                        view.evaluateJavascript("document.documentElement.outerHTML") { rawHtml ->
                            val html = WebViewUtil.unescapeJs(rawHtml)
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
                            releaseUrl = url
                            callback.onVersion(version)
                        }
                    }
                }
            }
        }
        wv.loadUrl(GROUP_URL)
    }

    override fun downloadLatest(callback: ApkSourceClient.DownloadCallback) {
        val startUrl = releaseUrl
        if (startUrl == null) {
            callback.onError("Primero hay que buscar la versión")
            return
        }
        val wv = WebViewUtil.newWebView(context, container)
        var step = 0
        wv.setDownloadListener { url, _, _, mimeType, _ ->
            callback.onFileUrl(url, mimeType)
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript("document.documentElement.outerHTML") { rawHtml ->
                    val html = WebViewUtil.unescapeJs(rawHtml)
                    if (WebViewUtil.isChallenge(html)) {
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
        wv.loadUrl(startUrl)
    }
}
