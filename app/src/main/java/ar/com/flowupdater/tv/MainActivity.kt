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
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

const val FLOW_PACKAGE = "ar.com.flow.androidtv"
private const val DOWNLOAD_FILE_NAME = "flow_update.apkm"

class MainActivity : Activity() {

    private lateinit var webViewContainer: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var updateButton: Button
    private lateinit var client: ApkMirrorClient

    private var pendingReleaseUrl: String? = null
    private var downloadId: Long = -1
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
                    setStatus("Flow se actualizó correctamente.")
                    showOpenFlowButton()
                }
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    confirmIntent?.let { startActivity(it) }
                }
                else -> {
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    setStatus("Error al instalar (código $status): $msg")
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
            if (total > 0) {
                val percent = (downloaded * 100 / total).toInt()
                setStatus("Descargando Flow... $percent%")
            } else {
                setStatus("Descargando Flow...")
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
                    setStatus("Descarga completa, instalando...")
                    installDownloadedFile()
                }
                DownloadManager.STATUS_FAILED -> {
                    downloadId = -1
                    val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    File(getExternalFilesDir(null), DOWNLOAD_FILE_NAME).delete()
                    setStatus("Error en la descarga (${downloadErrorText(reason)}). Volvé a tocar 'Descargar e instalar' para reintentar.")
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

        root.addView(panel)
        setContentView(root)
        updateButton.requestFocus()

        client = ApkMirrorClient(this, webViewContainer)
        showInstalledVersion()
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
        client.release()
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
        setStatus("Buscando última versión en APKMirror...")
        client.checkLatestVersion(object : ApkMirrorClient.VersionCallback {
            override fun onVersion(versionName: String, releaseUrl: String) {
                pendingReleaseUrl = releaseUrl
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
                setStatus("Error: $message")
            }
        })
    }

    private fun onDownloadClicked() {
        val releaseUrl = pendingReleaseUrl ?: return
        if (!packageManager.canRequestPackageInstalls()) {
            setStatus("Habilitá 'Instalar apps desconocidas' para esta app y volvé a tocar el botón")
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        setStatus("Buscando el link de descarga en APKMirror...")
        client.downloadLatest(releaseUrl, object : ApkMirrorClient.DownloadCallback {
            override fun onFileUrl(url: String, mimeType: String?) {
                setStatus("Descargando Flow...")
                enqueueDownload(url)
            }

            override fun onChallenge(message: String) {
                setStatus(message)
            }

            override fun onError(message: String) {
                setStatus("Error: $message")
            }
        })
    }

    private fun enqueueDownload(url: String) {
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
            ApkInstaller.installBundle(this, file)
        } catch (e: Exception) {
            setStatus("Error al preparar la instalación: ${e.message}")
        } finally {
            // Para acá, el contenido ya se copió a la sesión de PackageInstaller
            // (o falló al hacerlo): el .apkm descargado no hace más falta.
            file.delete()
        }
    }
}
