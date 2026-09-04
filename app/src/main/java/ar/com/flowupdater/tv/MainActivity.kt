package ar.com.flowupdater.tv

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

const val FLOW_PACKAGE = "ar.com.flow.androidtv"
private const val DOWNLOAD_FILE_NAME = "flow_update.apkm"
private const val SELF_UPDATE_FILE_NAME = "flowupdater_self_update.apk"

class MainActivity : Activity() {

    private lateinit var webViewContainer: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var updateButton: Button
    private lateinit var selfUpdateText: TextView
    private lateinit var selfUpdateButton: Button

    // Orden de prioridad: si una fuente falla (al buscar la versión o al
    // descargar), se prueba automáticamente con la siguiente.
    private lateinit var sources: List<ApkSourceClient>
    private var activeSourceIndex = 0

    private var pendingSelfUpdateUrl: String? = null
    private var downloadId: Long = -1
    private var downloadIsSelfUpdate = false
    private var installingSelfUpdate = false

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressPoller = object : Runnable {
        override fun run() {
            if (pollDownloadProgress()) {
                progressHandler.postDelayed(this, 500)
            }
        }
    }

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_SUCCESS -> {
                    if (installingSelfUpdate) {
                        setStatus("Actualizador de Flow actualizado. Reiniciando...")
                    } else {
                        setStatus("Flow se actualizó correctamente.")
                        showOpenFlowButton()
                    }
                }
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    confirmIntent?.let { startActivity(it) }
                }
                else -> {
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    val target = if (installingSelfUpdate) "el Actualizador" else "Flow"
                    setStatus("Error al instalar $target (código $status): $msg")
                }
            }
        }
    }

    // El broadcast ACTION_DOWNLOAD_COMPLETE es solo un empujón para revisar antes:
    // en algunos builds de Fire OS el propio DownloadProvider del sistema tira una
    // excepción al indexar el archivo en el MediaProvider y el broadcast nunca llega,
    // aunque la descarga haya terminado bien. El sondeo de progreso es quien de verdad
    // decide cuándo terminó, mirando el estado real en vez de esperar ese aviso.
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                checkDownloadStatus()
            }
        }
    }

    /** Devuelve true si hay que seguir sondeando (la descarga sigue en curso). */
    private fun pollDownloadProgress(): Boolean {
        if (downloadId == -1L) return false
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
        cursor.use {
            if (!it.moveToFirst()) return false
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_RUNNING && status != DownloadManager.STATUS_PENDING) {
                checkDownloadStatus()
                return false
            }
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val label = if (downloadIsSelfUpdate) "el Actualizador" else "Flow"
            if (total > 0) {
                val percent = (downloaded * 100 / total).toInt()
                setStatus("Descargando $label... $percent%")
            } else {
                setStatus("Descargando $label...")
            }
            return true
        }
    }

    /** Revisa el estado final de la descarga actual. Segura de llamar más de una vez. */
    private fun checkDownloadStatus() {
        val id = downloadId
        if (id == -1L) return
        progressHandler.removeCallbacks(progressPoller)
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(id))
        cursor.use {
            if (!it.moveToFirst()) return
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    downloadId = -1
                    if (downloadIsSelfUpdate) {
                        setStatus("Descarga completa, instalando el Actualizador...")
                        installSelfUpdateFile()
                    } else {
                        setStatus("Descarga completa, instalando...")
                        installDownloadedFile()
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    downloadId = -1
                    val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    if (downloadIsSelfUpdate) {
                        File(getExternalFilesDir(null), SELF_UPDATE_FILE_NAME).delete()
                        setStatus("Error al bajar la actualización del Actualizador (${downloadErrorText(reason)})")
                    } else {
                        File(getExternalFilesDir(null), DOWNLOAD_FILE_NAME).delete()
                        downloadFrom(activeSourceIndex + 1, isRetryAfterFailure = true)
                    }
                }
                else -> {
                    // sigue en curso (running/pending): dejamos que el sondeo de progreso siga solo
                }
            }
        }
    }

    private fun downloadErrorText(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "se cortó la conexión"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "no encuentra el almacenamiento"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "no hay espacio suficiente"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "error de red"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "demasiadas redirecciones"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "el servidor respondió con un error"
        DownloadManager.ERROR_FILE_ERROR -> "error al guardar el archivo"
        else -> "código $reason"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#1A1A2E"))

        webViewContainer = FrameLayout(this)
        root.addView(webViewContainer, FrameLayout.LayoutParams(0, 0))

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(60, 60, 60, 60)
        panel.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        )

        val title = TextView(this)
        title.text = "Actualizador de Flow"
        title.textSize = 28f
        title.setTextColor(Color.WHITE)
        panel.addView(title)

        statusText = TextView(this)
        statusText.textSize = 18f
        statusText.setTextColor(Color.LTGRAY)
        statusText.setPadding(0, 40, 0, 40)
        panel.addView(statusText)

        updateButton = Button(this)
        updateButton.text = "Buscar actualización"
        updateButton.isFocusable = true
        updateButton.isFocusableInTouchMode = true
        updateButton.setOnClickListener { onCheckClicked() }
        panel.addView(updateButton)

        selfUpdateText = TextView(this)
        selfUpdateText.textSize = 14f
        selfUpdateText.setTextColor(Color.parseColor("#8AA0FF"))
        selfUpdateText.setPadding(0, 50, 0, 16)
        selfUpdateText.visibility = View.GONE
        panel.addView(selfUpdateText)

        selfUpdateButton = Button(this)
        selfUpdateButton.text = "Actualizar el Actualizador"
        selfUpdateButton.isFocusable = true
        selfUpdateButton.isFocusableInTouchMode = true
        selfUpdateButton.visibility = View.GONE
        panel.addView(selfUpdateButton)

        root.addView(panel)
        setContentView(root)
        updateButton.requestFocus()

        sources = listOf(
            ApkMirrorClient(this, webViewContainer),
            ApkComboClient(this, webViewContainer),
            ApkPureClient(this, webViewContainer),
        )

        showInstalledVersion()
        checkSelfUpdate()
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(installReceiver, IntentFilter(ApkInstaller.ACTION_INSTALL_RESULT), Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(installReceiver, IntentFilter(ApkInstaller.ACTION_INSTALL_RESULT))
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    override fun onStop() {
        super.onStop()
        progressHandler.removeCallbacks(progressPoller)
        unregisterReceiver(installReceiver)
        unregisterReceiver(downloadReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        webViewContainer.removeAllViews()
    }

    private fun showInstalledVersion() {
        val installed = installedFlowVersion()
        statusText.text = if (installed != null) "Instalada: $installed" else "Flow no está instalado"
    }

    private fun installedFlowVersion(): String? = try {
        packageManager.getPackageInfo(FLOW_PACKAGE, 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun setStatus(text: String) {
        runOnUiThread { statusText.text = text }
    }

    private fun showOpenFlowButton() {
        runOnUiThread {
            updateButton.text = "Abrir aplicación"
            updateButton.setOnClickListener { launchFlow() }
        }
    }

    private fun launchFlow() {
        // Flow es una app de Android TV: declara LEANBACK_LAUNCHER, no LAUNCHER,
        // así que getLaunchIntentForPackage (que busca LAUNCHER) no la encuentra.
        val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(FLOW_PACKAGE)
            ?: packageManager.getLaunchIntentForPackage(FLOW_PACKAGE)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            setStatus("No se pudo abrir Flow")
        }
    }

    private fun onCheckClicked() {
        checkVersionFrom(0)
    }

    /** Busca la versión probando las fuentes en orden; si una falla, sigue con la próxima. */
    private fun checkVersionFrom(index: Int) {
        if (index >= sources.size) {
            setStatus("No se pudo conectar con ninguna fuente (${sources.joinToString(", ") { it.name }}). Probá de nuevo más tarde.")
            return
        }
        val source = sources[index]
        setStatus("Buscando última versión en ${source.name}...")
        source.checkLatestVersion(object : ApkSourceClient.VersionCallback {
            override fun onVersion(versionName: String) {
                activeSourceIndex = index
                val installed = installedFlowVersion()
                runOnUiThread {
                    if (installed == versionName) {
                        setStatus("Ya tenés la última versión ($versionName)")
                        showOpenFlowButton()
                    } else {
                        setStatus("Versión $versionName disponible (instalada: ${installed ?: "ninguna"})")
                        updateButton.text = "Descargar e instalar"
                        updateButton.setOnClickListener { onDownloadClicked() }
                    }
                }
            }

            override fun onError(message: String) {
                checkVersionFrom(index + 1)
            }
        })
    }

    private fun onDownloadClicked() {
        if (!packageManager.canRequestPackageInstalls()) {
            setStatus("Habilitá 'Instalar apps desconocidas' para esta app y volvé a tocar el botón")
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        downloadFrom(activeSourceIndex, isRetryAfterFailure = false)
    }

    /**
     * Descarga desde la fuente `index`. Si esa fuente no es la que ya tenía la
     * versión resuelta (por ej. porque venimos de un fallback), primero hay
     * que volver a navegarla con checkLatestVersion — cada cliente guarda su
     * propio estado de navegación (a qué página descargar), y ese estado no
     * se comparte entre fuentes distintas.
     */
    private fun downloadFrom(index: Int, isRetryAfterFailure: Boolean) {
        if (index >= sources.size) {
            setStatus("No se pudo descargar desde ninguna fuente (${sources.joinToString(", ") { it.name }}). Probá de nuevo más tarde.")
            return
        }
        val source = sources[index]

        val downloadCallback = object : ApkSourceClient.DownloadCallback {
            override fun onFileUrl(url: String, mimeType: String?) {
                activeSourceIndex = index
                setStatus("Descargando Flow...")
                enqueueDownload(url)
            }

            override fun onChallenge(message: String) {
                downloadFrom(index + 1, isRetryAfterFailure = true)
            }

            override fun onError(message: String) {
                downloadFrom(index + 1, isRetryAfterFailure = true)
            }
        }

        if (!isRetryAfterFailure && index == activeSourceIndex) {
            setStatus("Buscando el link de descarga en ${source.name}...")
            source.downloadLatest(downloadCallback)
        } else {
            setStatus("Probando con ${source.name}...")
            source.checkLatestVersion(object : ApkSourceClient.VersionCallback {
                override fun onVersion(versionName: String) {
                    setStatus("Buscando el link de descarga en ${source.name}...")
                    source.downloadLatest(downloadCallback)
                }

                override fun onError(message: String) {
                    downloadFrom(index + 1, isRetryAfterFailure = true)
                }
            })
        }
    }

    private fun enqueueDownload(url: String) {
        downloadIsSelfUpdate = false
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
        request.setDestinationInExternalFilesDir(this, null, DOWNLOAD_FILE_NAME)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val file = File(getExternalFilesDir(null), DOWNLOAD_FILE_NAME)
        if (file.exists()) file.delete()
        downloadId = dm.enqueue(request)
        progressHandler.post(progressPoller)
    }

    private fun installDownloadedFile() {
        val file = File(getExternalFilesDir(null), DOWNLOAD_FILE_NAME)
        try {
            installingSelfUpdate = false
            ApkInstaller.installBundle(this, file)
        } catch (e: Exception) {
            setStatus("Error al preparar la instalación: ${e.message}")
        } finally {
            // Para acá, el contenido ya se copió a la sesión de PackageInstaller
            // (o falló al hacerlo): el archivo descargado no hace más falta.
            file.delete()
        }
    }

    // --- Autoactualización de esta misma app ---

    private fun checkSelfUpdate() {
        SelfUpdateChecker.checkLatest(SelfUpdateChecker.Callback { result ->
            if (result == null) return@Callback
            val current = try {
                packageManager.getPackageInfo(packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
            if (isNewerVersion(result.versionName, current ?: "0")) {
                pendingSelfUpdateUrl = result.downloadUrl
                selfUpdateText.text = "Hay una versión nueva del Actualizador (v${result.versionName})"
                selfUpdateText.visibility = View.VISIBLE
                selfUpdateButton.visibility = View.VISIBLE
                selfUpdateButton.setOnClickListener { onSelfUpdateClicked() }
            }
        })
    }

    /** Compara versiones tipo "1.10.2" por partes numéricas — "1.9" no puede ganarle a "1.10" comparando como texto. */
    private fun isNewerVersion(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun onSelfUpdateClicked() {
        val url = pendingSelfUpdateUrl ?: return
        if (!packageManager.canRequestPackageInstalls()) {
            selfUpdateText.text = "Habilitá 'Instalar apps desconocidas' para esta app y volvé a tocar el botón"
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        downloadIsSelfUpdate = true
        selfUpdateText.text = "Descargando actualización del Actualizador..."
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
        request.setDestinationInExternalFilesDir(this, null, SELF_UPDATE_FILE_NAME)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val file = File(getExternalFilesDir(null), SELF_UPDATE_FILE_NAME)
        if (file.exists()) file.delete()
        downloadId = dm.enqueue(request)
        progressHandler.post(progressPoller)
    }

    private fun installSelfUpdateFile() {
        val file = File(getExternalFilesDir(null), SELF_UPDATE_FILE_NAME)
        try {
            installingSelfUpdate = true
            ApkInstaller.installSingleApk(this, file)
        } catch (e: Exception) {
            setStatus("Error al preparar la actualización del Actualizador: ${e.message}")
        } finally {
            file.delete()
        }
    }
}
