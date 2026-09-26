<div align="center">
  <img src="docs/micutre-icon.svg" width="128" alt="Icono de Micutre: micrófono azul con cabeza negra sobre fondo blanco" />
  <h1>Micutre</h1>
  <p>Copyright 2026 PollNull</p>
  <p><strong>Tu celular como micrófono inalámbrico para un parlante Bluetooth.</strong></p>
  <p>Android · Kotlin · C++ · Oboe</p>
</div>

---

Micutre transmite en vivo el audio del micrófono del teléfono al parlante Bluetooth que Android tenga conectado. También incluye efectos de voz personalizados mediante presets JSON y un soundboard al que puedes importar tus propios audios.

> **Estado:** proyecto personal en desarrollo — versión 0.1.0.

## Funciones

- Micrófono en vivo, control de volumen y detección del parlante conectado.
- Efectos de voz: tono más agudo o grave, eco y efecto robótico.
- Soundboard para sonidos importados desde el teléfono.
- Importación de audio MP3, WAV, M4A/AAC, OGG/Vorbis, Opus y FLAC, según los decodificadores disponibles en Android.
- Interfaz en español, inglés y chino simplificado, con opciones de movimiento reducido y texto en negrita.
- Importación de presets JSON para crear efectos de voz personalizados; hay un ejemplo en [`examples/voz-espacial.json`](examples/voz-espacial.json).

## Compilar

Necesitas JDK 17 y Android SDK con Platform 35, Build Tools 35.0.0, NDK 27.2.12479018 y CMake 3.22.1. Para instalar con `install-debug.ps1`, también necesitas Android SDK Platform-Tools (ADB).

Configura localmente la ruta de tu Android SDK con `ANDROID_HOME` o `ANDROID_SDK_ROOT`. Como alternativa, crea `local.properties` en la raíz del proyecto con esta línea:

```properties
sdk.dir=/ruta/a/tu/Android/Sdk
```

No subas `local.properties` ni rutas locales al repositorio. El proyecto incluye Gradle Wrapper, así que no necesitas instalar Gradle por separado. Desde la raíz, ejecuta una compilación limpia:

```shell
# Windows
.\gradlew.bat clean assembleDebug

# macOS / Linux
./gradlew clean assembleDebug
```

El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`. Para compilar e instalarlo en un teléfono conectado por USB, habilita la depuración USB y ejecuta:

```powershell
.\install-debug.ps1
```

El script usa ADB incluido en el Android SDK instalado en tu equipo; no hace falta versionar las herramientas binarias de ADB dentro del repositorio.

## Privacidad y limitaciones

La app necesita permiso de micrófono mientras transmite. El retardo depende del teléfono, la versión de Android y el parlante: Bluetooth añade latencia que la app no puede eliminar. Mantén el teléfono apartado del parlante para reducir el acople.

La versión actual no transmite con la pantalla apagada, no permite elegir el parlante desde la app y no mide la latencia por modelo.

## Licencia

El código fuente propio de Micutre se distribuye bajo Apache License 2.0. Consulta el archivo [`LICENSE`](LICENSE). Los recursos de Micutre enumerados a continuación también se distribuyen bajo Apache-2.0. Los recursos de terceros conservan sus propias licencias.

### Recursos generados con asistencia de IA

Los siguientes recursos se generaron con asistencia de OpenAI Codex y se distribuyen bajo Apache-2.0:

- Icono del README: [`docs/micutre-icon.svg`](docs/micutre-icon.svg).
- Iconos de la app: `app/src/main/res/drawable/ic_micutre_foreground.xml`, `app/src/main/res/drawable/ic_micutre_monochrome.xml`, `app/src/main/res/mipmap/ic_launcher.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` y `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`.
- Iconos de reproducción: `app/src/main/res/drawable/ic_play.xml` y `app/src/main/res/drawable/ic_pause.xml`.
- Ejemplo de preset: [`examples/voz-espacial.json`](examples/voz-espacial.json).

Esta atribución documenta la asistencia de IA y no presenta estos recursos como creados exclusivamente por una persona.

### Recurso de terceros con licencia propia

La fuente Space Grotesk está bajo SIL Open Font License 1.1. El archivo [`app/src/main/assets/fonts/OFL.txt`](app/src/main/assets/fonts/OFL.txt) contiene su licencia y la atribución: Copyright 2020 The Space Grotesk Project Authors.

## Contribuir

Por ahora, puedes abrir un *issue* para informar un error o proponer una mejora. Se agregarán instrucciones para contribuciones cuando el flujo del proyecto esté definido.
