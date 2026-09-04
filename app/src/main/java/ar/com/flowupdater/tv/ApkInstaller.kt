package ar.com.flowupdater.tv

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Instala un .apkm (bundle de split-APKs de APKMirror) en una sola sesión
 * de PackageInstaller — lo mismo que hace `adb install-multiple`. Requiere
 * que la firma del bundle coincida con la de Flow ya instalado; si no,
 * el sistema devuelve INSTALL_FAILED_UPDATE_INCOMPATIBLE en vez de instalar.
 */
object ApkInstaller {

    const val ACTION_INSTALL_RESULT = "ar.com.flowupdater.tv.INSTALL_RESULT"

    fun installBundle(context: Context, bundleFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        session.use { s ->
            ZipInputStream(bundleFile.inputStream()).use { zip ->
                var entry = zip.nextEntry
                var index = 0
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                        val name = "split_${index++}_${entry.name.substringAfterLast('/')}"
                        s.openWrite(name, 0, -1).use { out ->
                            zip.copyTo(out)
                            s.fsync(out)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val intent = Intent(ACTION_INSTALL_RESULT).setPackage(context.packageName)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            s.commit(pendingIntent.intentSender)
        }
    }
}
