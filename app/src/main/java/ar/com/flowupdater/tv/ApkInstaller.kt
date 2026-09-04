package ar.com.flowupdater.tv

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.util.zip.ZipFile

/**
 * Instala APKs vía PackageInstaller, en una sola sesión — lo mismo que
 * hace `adb install` / `adb install-multiple`. Requiere que la firma del
 * archivo coincida con la de la app ya instalada; si no, el sistema
 * devuelve INSTALL_FAILED_UPDATE_INCOMPATIBLE en vez de instalar.
 */
object ApkInstaller {

    const val ACTION_INSTALL_RESULT = "ar.com.flowupdater.tv.INSTALL_RESULT"

    /**
     * Bundle de split-APKs (.apkm/.xapk de APKMirror, APKCombo o APKPure).
     *
     * Usa `ZipFile` (lectura por directorio central, con acceso aleatorio al
     * archivo ya descargado) en vez de `ZipInputStream` (lectura secuencial).
     * Con el bundle de APKMirror las dos formas andaban bien, pero el de
     * APKCombo — mismo contenido, comprimido distinto — hacía que
     * ZipInputStream copiara mal la entrada grande del base.apk y el
     * PackageInstaller la rechazaba con INSTALL_PARSE_FAILED_NOT_APK.
     * ZipFile lee el directorio central (autoritativo) en vez de ir
     * adivinando límites de cada entrada sobre la marcha, así que es la
     * forma robusta de abrir un zip real ya en disco.
     */
    fun installBundle(context: Context, bundleFile: File) {
        withSession(context) { s ->
            ZipFile(bundleFile).use { zip ->
                var index = 0
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                        val name = "split_${index++}_${entry.name.substringAfterLast('/')}"
                        val length = if (entry.size >= 0) entry.size else -1
                        s.openWrite(name, 0, length).use { out ->
                            zip.getInputStream(entry).use { it.copyTo(out) }
                            s.fsync(out)
                        }
                    }
                }
            }
        }
    }

    /** Un .apk suelto (se usa para la autoactualización de esta misma app). */
    fun installSingleApk(context: Context, apkFile: File) {
        withSession(context) { s ->
            s.openWrite("base.apk", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { it.copyTo(out) }
                s.fsync(out)
            }
        }
    }

    private inline fun withSession(context: Context, write: (PackageInstaller.Session) -> Unit) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        session.use { s ->
            write(s)
            val intent = Intent(ACTION_INSTALL_RESULT).setPackage(context.packageName)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            s.commit(pendingIntent.intentSender)
        }
    }
}
