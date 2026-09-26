<div align="center">
  <img src="docs/micutre-icon.svg" width="320" alt="Micutre microphone icon with a white outline on a transparent background" />
  <h1>Micutre — Android Bluetooth Microphone</h1>
  <p>Copyright 2026 PollNull</p>
  <p><strong>Open-source Android app for using your phone as a microphone with a Bluetooth speaker, voice effects, and a soundboard.</strong></p>
  <p>Android · Kotlin · C++ · Oboe</p>
</div>

<p align="center">
  <a href="https://github.com/pollnull/Micutre/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/pollnull/Micutre" alt="License" />
  </a>
  <a href="https://github.com/pollnull/Micutre/releases/latest">
    <img src="https://img.shields.io/github/v/release/pollnull/Micutre" alt="Latest release" />
  </a>
</p>

---

Micutre is a free and open-source Android app that streams live microphone audio from your phone to a connected Bluetooth speaker. It includes voice effects, custom effect presets imported through JSON, and a soundboard for audio files you import.

> **Status:** learning project · v1.0.0 available.

## Download

Download the [Micutre v1.0.0 APK](https://github.com/pollnull/Micutre/releases/tag/v1.0.0), or browse [all releases](https://github.com/pollnull/Micutre/releases).

## Features

- Use an Android phone as a live microphone for a connected Bluetooth speaker, with volume control and connection status.
- Voice effects including higher or lower pitch, echo, and robot voice.
- Import custom voice-effect presets in JSON format; see [`examples/voz-espacial.json`](examples/voz-espacial.json).
- Soundboard for playing audio files imported from your phone.
- Imports MP3, WAV, M4A/AAC, OGG/Vorbis, Opus, and FLAC, depending on the decoders available on Android.
- Interface in Spanish, English, and Simplified Chinese, with reduced motion and bold text options.

## Build

You need JDK 17 and the Android SDK with Platform 35, Build Tools 35.0.0, NDK 27.2.12479018, and CMake 3.22.1. To install with `install-debug.ps1`, you also need Android SDK Platform-Tools (ADB).

Configure your Android SDK path locally with `ANDROID_HOME` or `ANDROID_SDK_ROOT`. Alternatively, create `local.properties` in the project root with this line:

```properties
sdk.dir=/path/to/your/Android/Sdk
```

Do not commit `local.properties` or machine-specific paths. The project includes the Gradle Wrapper, so you do not need to install Gradle separately. From the project root, run a clean build:

```shell
# Windows
.\gradlew.bat clean assembleDebug

# macOS / Linux
./gradlew clean assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`. To build and install it on a phone connected over USB, enable USB debugging and run:

```powershell
.\install-debug.ps1
```

The script uses ADB from the Android SDK installed on your computer; you do not need to add ADB binaries to the repository.

## Privacy and limitations

The app needs microphone permission while streaming. Latency depends on the phone, Android version, and speaker; Bluetooth adds latency that the app cannot eliminate. Keep the phone away from the speaker to reduce feedback.

The current version does not stream while the screen is off, let you choose a speaker in the app, or measure latency for each device model.

## License

Micutre's original source code is distributed under the Apache License 2.0. See [`LICENSE`](LICENSE). The Micutre resources listed below are also distributed under Apache-2.0. Third-party resources retain their own licenses.

### Resources created with AI assistance

The following resources were generated with assistance from OpenAI Codex and are distributed under Apache-2.0:

- README icon: [`docs/micutre-icon.svg`](docs/micutre-icon.svg).
- App icons: `app/src/main/res/drawable/ic_micutre_foreground.xml`, `app/src/main/res/drawable/ic_micutre_monochrome.xml`, `app/src/main/res/mipmap/ic_launcher.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, and `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`.
- Playback icons: `app/src/main/res/drawable/ic_play.xml` and `app/src/main/res/drawable/ic_pause.xml`.
- Preset example: [`examples/voz-espacial.json`](examples/voz-espacial.json).

This attribution documents AI assistance and does not present these resources as created exclusively by a human.

### Third-party resource under its own license

The Space Grotesk font is licensed under the SIL Open Font License 1.1. The [`app/src/main/assets/fonts/OFL.txt`](app/src/main/assets/fonts/OFL.txt) file contains its license and attribution: Copyright 2020 The Space Grotesk Project Authors.

## Contributing

Open an [issue](https://github.com/pollnull/Micutre/issues) to report a bug or suggest an improvement. Contribution guidelines will be added once the project workflow is established.

## A note

Micutre is a learning project, so fixes may take time. Bug reports and suggestions are appreciated.
