# Actualizar Flow en el Amazon Fire TV Stick (sin Downloader ni instalador de APKs aparte)

Si buscaste **"cómo actualizar Flow en Fire TV Stick"**, **"Flow Fibertel
no actualiza en Amazon Fire TV"** o **"instalar Flow APK en Fire Stick"**,
llegaste al lugar correcto.

Flow (Fibertel/Telecom Argentina) casi nunca aparece en la Amazon Appstore
del Fire TV, así que la única forma de tenerlo actualizado es bajar el APK
a mano de algún sitio, después usar una segunda app para instalarlo, y
repetir todo eso cada vez que sale una versión nueva. **Actualizador de
Flow** hace todo eso con un solo botón: busca la última versión, la baja y
la instala, sin salir de la app.

![Actualizador de Flow](docs/screenshot.png)

## Instalar (para cualquiera, no hace falta compu ni saber programar)

1. En el Fire TV Stick, abrí la app **Downloader** (el ícono naranja con
   una flecha, de AFTVnews). Si no la tenés, buscala como "Downloader" en
   la Amazon Appstore del propio Fire TV — es gratis y la usa mucha gente
   para bajar APKs.
2. Al abrir Downloader por primera vez aparece una pantalla que pide un
   **código**. Escribí este:

   ```
   3723480
   ```

   Es un código corto de [aftv.news](https://aftv.news) que apunta directo
   a la última versión de Actualizador de Flow — mucho más rápido que
   tipear una URL larga con el control remoto. Si en vez del código de
   bienvenida ya estás en la pantalla principal de Downloader, tocá el
   símbolo **+** o el campo de URL y escribí `aftv.news/3723480` en lugar
   del código.

   <details>
   <summary>¿Preferís la URL completa de GitHub en vez del código corto?</summary>

   ```
   github.com/entornodigital28/flow-updater-tv/releases/latest/download/ActualizadorDeFlow.apk
   ```

   Hace exactamente lo mismo: el código de aftv.news solo redirige a esa
   URL.
   </details>

3. Cuando termine de bajar, tocá **Instalar**. Si es la primera vez que
   instalás algo con Downloader, Fire TV te va a pedir permiso para
   "Instalar aplicaciones de fuentes desconocidas" — aceptalo, es normal y
   solo se pide una vez.
4. Listo. Abrí **Actualizador de Flow** desde tus apps.

## Cómo se usa

1. Abrí la app.
2. Tocá **Buscar actualización**.
3. Si hay una versión nueva, aparece **Descargar e instalar** — tocalo y
   esperá (se ve el porcentaje de la descarga). No hay que ir a ninguna
   otra app ni buscar ningún archivo a mano.
4. Cuando termina, el botón pasa a **Abrir aplicación** y entrás directo a
   Flow. Si ya estabas al día, pasa lo mismo: te lleva directo a Flow.

## Preguntas frecuentes

**¿Es gratis?** Sí, y el código es público en este mismo repositorio.

**¿De dónde saca la actualización de Flow?** De
[APKMirror](https://www.apkmirror.com), un sitio conocido de mirrors de
APKs de Android. La app instala la actualización con `PackageInstaller`,
el mismo mecanismo que usa `adb install`: si la firma no coincidiera con
tu Flow instalado, Android directamente rechaza la instalación en vez de
romper nada.

**¿Reemplaza a Downloader?** No del todo: Downloader (o algo parecido)
sigue haciendo falta **una sola vez**, para instalar el propio
Actualizador de Flow. De ahí en adelante, para actualizar Flow ya no hace
falta ni Downloader ni buscar nada a mano.

**¿Funciona en cualquier Fire TV?** Se probó en un Fire TV Stick con Fire
OS actual. Debería andar en cualquier dispositivo Fire TV (Stick, Stick
4K/4K Max, Cube, Lite) con Flow instalado, porque no usa nada específico
de un modelo puntual.

**¿Y si Flow no está instalado todavía?** También sirve: detecta que no
está, y "Descargar e instalar" hace una instalación nueva en vez de una
actualización.

## Para quien quiera compilarla desde el código

Requiere JDK 17 y el Android SDK (`platform-tools`, `platforms;android-33`,
`build-tools;33.0.2`) con `local.properties` apuntando a `sdk.dir`.

```
gradlew.bat assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

### Instalar por ADB (para desarrollo)

1. Activar depuración ADB: Ajustes → Mi Fire TV/Dispositivo → tocar el
   nombre del build 7 veces → Opciones de desarrollador → Depuración ADB.
2. Anotar la IP en Ajustes → Mi Fire TV → Acerca de → Red.
3. `adb connect <ip>:5555` y aceptar el cartel de autorización en el TV.
4. `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
5. La primera vez, la app va a pedir habilitar "Instalar apps desconocidas"
   para sí misma (Ajustes → Mi Fire TV → Opciones de desarrollador →
   Instalar apps desconocidas → Actualizador de Flow → Activado). Después
   de activarlo, volver a tocar "Descargar e instalar".

## Publicar una versión nueva

El código de Downloader (`3723480` / `aftv.news/3723480`) y el link del
README apuntan a `.../releases/latest/download/ActualizadorDeFlow.apk` —
un link "rolling" de GitHub que siempre resuelve a la release marcada como
**latest**, no a la v1.0.0 en particular. Por eso, al publicar una versión
nueva **no hace falta tocar el README ni generar un código nuevo**, pero
sí hay que respetar dos cosas o el link se rompe:

- El asset adjunto a la release tiene que llamarse **exactamente**
  `ActualizadorDeFlow.apk` (mismo nombre siempre).
- La release no puede quedar marcada como "pre-release" — si no, GitHub no
  la considera "latest" y el link sigue apuntando a la anterior.

## Si una fuente falla, prueba con la siguiente sola

La app no depende de un solo sitio. Prueba en este orden:

1. **[APKMirror](https://www.apkmirror.com)** — la fuente principal.
2. **[APKCombo](https://apkcombo.com)** — se comparó a mano, byte a byte
   (SHA-256), contra el bundle de APKMirror: **son exactamente el mismo
   archivo**, el mismo build oficial de Telecom, solo empaquetado distinto.
3. **[APKPure](https://apkpure.com)** — último recurso. A diferencia de las
   otras dos, su bundle **no es** el mismo archivo (hash distinto) y le
   falta el split de 64&nbsp;bits (`arm64-v8a`) — corre en modo 32 bits en
   vez de 64. Eso sí, tiene la misma firma que Flow, así que la instalación
   no se rompe por eso.

**[Uptodown](https://uptodown.com) se descartó**: no tiene publicada la
versión Android TV de Flow, solo la de celular.

Si la fuente activa falla —al buscar la versión o al descargar— se prueba
la siguiente sola, sin que haya que tocar nada. El mensaje en pantalla
siempre dice en qué fuente está probando.

## Cómo funciona por dentro

- `ApkSourceClient.kt`: la interfaz que cumple cada fuente (buscar versión,
  descargar). `ApkMirrorClient.kt`, `ApkComboClient.kt` y `ApkPureClient.kt`
  la implementan cada una a su manera — navegan su sitio con un `WebView`
  real (con JS) hasta capturar la URL firmada del archivo final. Ninguna
  usa una API no oficial: todo es la misma navegación que haría una
  persona. `MainActivity.kt` las prueba en orden y pasa a la siguiente si
  una falla.
- `ApkInstaller.kt`: el archivo de estas fuentes es un bundle (`.apkm`/
  `.xapk`, zip con varios `.apk` — arquitecturas/idiomas). Se abre con
  `ZipFile` (lectura por directorio central, no secuencial — hizo falta
  para que el bundle de APKCombo, comprimido distinto al de APKMirror,
  no se corrompiera al copiarlo) y se instalan todas las entradas `.apk`
  en una sola sesión de `PackageInstaller`, igual que hace
  `adb install-multiple`.
- `SelfUpdateChecker.kt`: chequea si esta misma app tiene una versión
  nueva publicada en este repositorio (API de GitHub, sin scraping) y, si
  hay, ofrece bajarla e instalarla — con el mismo `PackageInstaller` que
  usa para Flow, pero como un `.apk` suelto en vez de un bundle.
- `MainActivity.kt`: la pantalla, la comparación de versión instalada vs.
  disponible, la descarga (con porcentaje), el manejo del permiso de
  "orígenes desconocidos" y el botón "Abrir aplicación" al terminar.

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

## Aviso legal

Este es un proyecto personal, independiente y sin fines de lucro. Se
publica **"tal cual"** ("as is"), sin ninguna garantía — ver la licencia
MIT en [`LICENSE`](LICENSE), que además deslinda de responsabilidad a
quien lo escribió por cualquier daño derivado de su uso.

- **Sin afiliación.** Este proyecto no está afiliado, asociado, autorizado,
  patrocinado ni respaldado de ninguna forma por Telecom Argentina,
  Cablevisión Fibertel, Flow, Amazon/Amazon Fire TV, APKMirror, APKCombo ni
  APKPure. "Flow", "Fibertel" y "Telecom" son marcas de sus respectivos
  dueños, nombradas acá únicamente para describir con qué es compatible la
  app — no para reclamar ninguna relación con ellos.
- **Qué hace, en criollo.** La app no aloja, redistribuye ni modifica el
  APK de Flow: automatiza los mismos pasos que haría una persona a mano
  (abrir una página pública, tocar un botón de descarga, instalar con el
  instalador del propio Android) usando un `WebView`. No evita ningún
  control de acceso ni omite ningún pago.
- **Sideloading, bajo tu responsabilidad.** Instalar aplicaciones fuera de
  una tienda oficial ("orígenes desconocidos") tiene los riesgos que
  Android ya avisa al activar ese permiso. Quien usa esta app decide
  hacerlo por su cuenta; ni el autor ni este repositorio se hacen
  responsables por el contenido de sitios de terceros, por cambios que
  hagan a sus condiciones de uso, ni por cualquier problema que surja de
  usar el APK de Flow que esos sitios distribuyen.
- **Marcas y contenido de terceros.** Cualquier logo, nombre o marca
  mencionados pertenecen a sus dueños y se usan únicamente a título
  descriptivo/informativo (uso nominativo), sin implicar patrocinio.

Nada de esto es asesoramiento legal — es simplemente cómo se entiende y se
usa este proyecto. Si representás a alguna de las marcas mencionadas y
querés hacer un reclamo, abrí un issue en este repositorio.
