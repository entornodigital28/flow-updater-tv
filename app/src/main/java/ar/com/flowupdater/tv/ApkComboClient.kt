package ar.com.flowupdater.tv

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Fuente alternativa si APKMirror falla. Confirmado a mano (comparando
 * SHA-256) que el .apkm/.xapk que sirve APKCombo es BIT A BIT IDÉNTICO al
 * de APKMirror — es el mismo build oficial de Telecom, solo que empaquetado
 * distinto. A diferencia de APKMirror, la URL de descarga no cambia de
 * versión en versión (no hace falta buscar un link "última versión"), lo
 * que la hace más simple y más resistente a cambios del sitio.
 */
class ApkComboClient(private val context: Context, private val container: FrameLayout) : ApkSourceClient {

    override val name = "APKCombo"

    companion object {
        private const val DOWNLOAD_PAGE = "https://apkcombo.com/flow-android-tv/ar.com.flow.androidtv/download/apk"
    }

    override fun checkLatestVersion(callback: ApkSourceClient.VersionCallback) {
        val wv = WebViewUtil.newWebView(context, container)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript("document.documentElement.outerHTML") { rawHtml ->
                    val html = WebViewUtil.unescapeJs(rawHtml)
                    if (WebViewUtil.isChallenge(html)) {
                        callback.onError("Apareció una verificación en APKCombo")
                        return@evaluateJavascript
                    }
                    val version = Regex(""""softwareVersion":"([0-9.]+)"""").find(html)?.groupValues?.get(1)
                    if (version == null) {
                        callback.onError("No encontré la versión en APKCombo (¿cambió el sitio?)")
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
                        callback.onChallenge("Verificación de APKCombo en pantalla, resolvela con el control remoto")
                        return@evaluateJavascript
                    }
                    if (!followedRedirect) {
                        val link = Regex("""href="(https://apkcombo\.com/r2\?u=[^"]+)"""")
                            .find(html)?.groupValues?.get(1)
                        if (link != null) {
                            followedRedirect = true
                            view.loadUrl(WebViewUtil.unescapeHtmlAmp(link))
                        }
                        // si no aparece el link, esperamos: el archivo puede llegar solo
                        // por setDownloadListener si la página ya lo dispara sin link visible.
                    }
                }
            }
        }
        wv.loadUrl(DOWNLOAD_PAGE)
    }
}
