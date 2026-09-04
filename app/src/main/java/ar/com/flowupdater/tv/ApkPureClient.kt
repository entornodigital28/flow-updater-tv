package ar.com.flowupdater.tv

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Último recurso, solo si APKMirror y APKCombo fallan los dos. A
 * diferencia de esas dos fuentes (que sirven el mismo build oficial, byte
 * a byte), el bundle de APKPure es OTRO archivo: mismo certificado de
 * firma que Flow (la instalación no se rompe), pero el .apk base tiene
 * otro hash y falta el split arm64-v8a — corre en modo 32 bits en vez de
 * 64. Por eso va última en la lista de fuentes, no primera.
 */
class ApkPureClient(private val context: Context, private val container: FrameLayout) : ApkSourceClient {

    override val name = "APKPure"

    companion object {
        private const val DOWNLOAD_PAGE = "https://apkpure.com/flow-android-tv/ar.com.flow.androidtv/download"
    }

    override fun checkLatestVersion(callback: ApkSourceClient.VersionCallback) {
        val wv = WebViewUtil.newWebView(context, container)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript("document.documentElement.outerHTML") { rawHtml ->
                    val html = WebViewUtil.unescapeJs(rawHtml)
                    if (WebViewUtil.isChallenge(html)) {
                        callback.onError("Apareció una verificación en APKPure")
                        return@evaluateJavascript
                    }
                    val version = Regex(""""versionName":"([0-9.]+)"""").find(html)?.groupValues?.get(1)
                    if (version == null) {
                        callback.onError("No encontré la versión en APKPure (¿cambió el sitio?)")
                    } else {
                        callback.onVersion(version)
                    }
                }
            }
        }
        wv.loadUrl(DOWNLOAD_PAGE)
    }

    override fun downloadLatest(callback: ApkSourceClient.DownloadCallback) {
        val wv = WebViewUtil.newWebView(context, container)
        var followedRedirect = false
        wv.setDownloadListener { url, _, _, mimeType, _ ->
            callback.onFileUrl(url, mimeType)
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript("document.documentElement.outerHTML") { rawHtml ->
                    val html = WebViewUtil.unescapeJs(rawHtml)
                    if (WebViewUtil.isChallenge(html)) {
                        callback.onChallenge("Verificación de APKPure en pantalla, resolvela con el control remoto")
                        return@evaluateJavascript
                    }
                    if (!followedRedirect) {
                        val link = Regex("""href="(https://d\.apkpure\.com/b/XAPK/[^"]+)"""")
                            .find(html)?.groupValues?.get(1)
                        if (link != null) {
                            followedRedirect = true
                            view.loadUrl(WebViewUtil.unescapeHtmlAmp(link))
                        }
                    }
                }
            }
        }
        wv.loadUrl(DOWNLOAD_PAGE)
    }
}
