package ar.com.flowupdater.tv

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.widget.FrameLayout

/** Utilidades chicas compartidas por los clientes de cada fuente (APKMirror, APKCombo, APKPure). */
object WebViewUtil {

    private val CHALLENGE_MARKERS =
        listOf("Just a moment", "cf-turnstile", "challenge-platform", "Verifying you are human", "g-recaptcha")

    @SuppressLint("SetJavaScriptEnabled")
    fun newWebView(context: Context, container: FrameLayout): WebView {
        // El container se comparte entre los distintos clientes de fuente (uno a la vez
        // activo), así que limpiamos cualquier WebView anterior sin importar quién lo creó.
        container.removeAllViews()
        val wv = WebView(context)
        wv.layoutParams = FrameLayout.LayoutParams(1, 1)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        container.addView(wv)
        return wv
    }

    fun isChallenge(text: String): Boolean = CHALLENGE_MARKERS.any { text.contains(it, ignoreCase = true) }

    fun unescapeJs(raw: String?): String {
        if (raw == null) return ""
        var s = raw
        if (s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length - 1)
        return s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
    }

    fun unescapeHtmlAmp(s: String): String = s.replace("&amp;", "&")
}
