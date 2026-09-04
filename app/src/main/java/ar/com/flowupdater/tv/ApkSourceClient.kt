package ar.com.flowupdater.tv

/**
 * Una fuente de la que se puede leer la última versión de Flow y descargarla.
 * Cada implementación guarda internamente el estado que necesite entre
 * checkLatestVersion() y downloadLatest() (por ejemplo, la URL de la página
 * de esa versión) — quien llama no tiene que saber nada de la navegación
 * interna de cada sitio.
 */
interface ApkSourceClient {

    val name: String

    interface VersionCallback {
        fun onVersion(versionName: String)
        fun onError(message: String)
    }

    interface DownloadCallback {
        fun onFileUrl(url: String, mimeType: String?)
        fun onChallenge(message: String)
        fun onError(message: String)
    }

    fun checkLatestVersion(callback: VersionCallback)
    fun downloadLatest(callback: DownloadCallback)
}
