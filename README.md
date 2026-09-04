# Actualizador de Flow (Fire TV)

App para Amazon Fire TV que busca la última versión de Flow (Fibertel) en
APKMirror y la instala, sin pasar por Downloader + un instalador de APKs
aparte.

Probado de punta a punta en un Fire TV real: detecta la versión instalada,
encuentra la última en APKMirror, descarga el bundle `.apkm` (mostrando el
porcentaje) y lo instala como actualización (misma firma que el Flow
oficial, sin perder datos). Ya sea porque ya estaba al día o porque recién
terminó de actualizar, el botón pasa a "Abrir aplicación" y abre Flow
directamente (`getLeanbackLaunchIntentForPackage` — Flow declara
`LEANBACK_LAUNCHER`, no `LAUNCHER`, así que la versión "de escritorio" de
esa API no la encuentra).

## Cómo compilar

Requiere JDK 17 y el Android SDK (`platform-tools`, `platforms;android-33`,
`build-tools;33.0.2`) con `local.properties` apuntando a `sdk.dir`.

```
gradlew.bat assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Cómo instalar en el Fire TV

1. Activar depuración ADB: Ajustes → Mi Fire TV/Dispositivo → tocar el
   nombre del build 7 veces → Opciones de desarrollador → Depuración ADB.
2. Anotar la IP en Ajustes → Mi Fire TV → Acerca de → Red.
3. `adb connect <ip>:5555` y aceptar el cartel de autorización en el TV.
4. `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
5. La primera vez, la app va a pedir habilitar "Instalar apps desconocidas"
   para sí misma (Ajustes → Mi Fire TV → Opciones de desarrollador →
   Instalar apps desconocidas → Actualizador de Flow → Activado). Después
   de activarlo, volver a tocar "Descargar e instalar".

## Cómo funciona

- `ApkMirrorClient.kt`: navega APKMirror con un `WebView` real (con JS) —
  busca la ficha de Flow, lee la versión, y sigue la cadena de descarga
  hasta capturar la URL firmada del archivo final. No usa ninguna API no
  oficial: todo es la misma navegación que haría una persona.
- `ApkInstaller.kt`: el archivo de APKMirror es un `.apkm` (zip con varios
  `.apk` — arquitecturas/idiomas). Se abre el zip y se instalan todas las
  entradas `.apk` en una sola sesión de `PackageInstaller`, igual que hace
  `adb install-multiple`.
- `MainActivity.kt`: la pantalla, la comparación de versión instalada vs.
  disponible, la descarga (con porcentaje) y el manejo del permiso de
  "orígenes desconocidos".

## Descarga: por qué no confía solo en el aviso del sistema

`DownloadManager` avisa que una descarga terminó con el broadcast
`ACTION_DOWNLOAD_COMPLETE`. En pruebas reales, ese aviso **a veces no
llega**: el propio proceso del sistema (`DownloadProvider`) tira una
excepción al intentar indexar el `.apkm` en el `MediaProvider` justo
después de terminar la descarga — se ve en logcat como un crash de
`DownloadProvider.updateMediaProvider` — y esa excepción corta el aviso
antes de que salga. La descarga en sí termina bien; lo que falla es
únicamente la notificación.

Por eso `MainActivity.kt` no depende de ese broadcast para decidir cuándo
instalar: el sondeo que actualiza el porcentaje (`pollDownloadProgress`,
cada 500&nbsp;ms) es el que de verdad detecta el final, consultando el
estado real en `DownloadManager` en vez de esperar a que alguien avise. El
broadcast solo actúa como un empujón extra por si llega antes que el
sondeo — `checkDownloadStatus()` está escrita para poder llamarse desde
cualquiera de los dos caminos sin duplicar la instalación.

## Limitaciones conocidas

- **Scraping frágil por diseño:** APKMirror no tiene API pública. Si cambia
  el HTML de sus páginas, `ApkMirrorClient.kt` es el único lugar que hay
  que revisar. La app nunca falla en silencio: si no puede leer la versión
  o encontrar el siguiente link, lo dice en pantalla.
- **Cloudflare:** en las pruebas no apareció ningún desafío interactivo
  (`ApkMirrorClient` navega con un WebView oculto). Si algún día aparece uno
  para la etapa de descarga, `onChallenge()` es el gancho para mostrar el
  WebView en pantalla y que se resuelva con el control remoto — hoy no hizo
  falta.
- **La firma tiene que coincidir:** si algún día Flow deja de estar en
  APKMirror con la misma firma que la versión instalada (por ejemplo, si
  Telecom empieza a firmar distinto), la instalación va a fallar con un
  error de Android (no de esta app) en vez de actualizar.
