package ar.com.flowupdater.tv

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Chequea si hay una versión nueva de esta misma app (Actualizador de
 * Flow) publicada como GitHub Release. Es la API pública de GitHub (JSON
 * plano, sin scraping): no hace falta WebView para esto.
 */
object SelfUpdateChecker {

    private const val API_URL = "https://api.github.com/repos/entornodigital28/flow-updater-tv/releases/latest"
    private const val ASSET_NAME = "ActualizadorDeFlow.apk"

    data class Result(val versionName: String, val downloadUrl: String)

    fun interface Callback {
        fun onResult(result: Result?)
    }

    fun checkLatest(callback: Callback) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            val result = try {
                val connection = URL(API_URL).openConnection() as HttpURLConnection
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.getString("tag_name").removePrefix("v")
                val assets = json.getJSONArray("assets")
                var downloadUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name") == ASSET_NAME) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                downloadUrl?.let { Result(tag, it) }
            } catch (e: Exception) {
                null
            }
            handler.post { callback.onResult(result) }
        }.start()
    }
}
