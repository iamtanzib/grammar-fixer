# Grammar Fixer

**Website: https://grammar-fixer.netlify.app**

Turns messy text (English, Bangla or Banglish) into clean, simple, human English.
One hotkey on Windows, one menu item on Android. Powered by the free [Groq](https://console.groq.com) API.

- **Windows** tray app: select text anywhere, press `Ctrl + Alt + G`, text is fixed in place.
- **Android** app: select text in any app, tap the 3 dots, choose **Fix grammar**.
- Bring your own API key. The key stays on your device (Windows: encrypted with DPAPI).

## Get a Groq key

1. Sign in at <https://console.groq.com/keys>.
2. Create an API key (starts with `gsk_`). Keep it private.

## Install

Download `GrammarFixer.exe` or `GrammarFixer.apk` from the [Releases](../../releases) page.

**Windows:** run the exe. If SmartScreen warns, click *More info → Run anyway* (the app is unsigned). Paste your key, press *Test*, then *Start*. Right-click the tray icon for settings (shortcut, writing style), pause, or uninstall.

**Android:** open the APK, allow "Install unknown apps" if asked, paste your key, tap *Save and test key*. You can also share text to the app or use the *Try it here* box.

## Build from source

### Windows exe
Needs only the C# compiler that ships with Windows (.NET Framework 4.x):

```powershell
./build.ps1
```

### Android apk
No Gradle. Uses aapt2, javac, d8, zipalign, apksigner. One-time setup: JDK 17 and Android SDK (build-tools 34.0.0, platform android-34) in `%USERPROFILE%\AndroidBuild`, plus Python.

```powershell
cd android
./build.ps1
```

The script creates a **new signing keystore** on first run (`grammarfixer.keystore` + `keystore-password.txt`, git-ignored). Keep it safe: updates must be signed with the same key. Your APK will not be update-compatible with the official release.

## Customize

| What | Where |
|------|-------|
| Windows app logic, hotkey, UI, prompt | `GrammarFixer.cs` |
| Android screens | `android/res/layout/` |
| Android logic, Groq calls, prompt | `android/src/com/grammarfixer/app/` |
| Default model | `DefaultModel` in `GrammarFixer.cs` and `MODEL` in the Android `Groq.java` |
| App icon / colors | `android/res/drawable/`, `android/res/values/` |

Fork it, change the prompt for your own style or language, rebuild.

## License

[MIT](LICENSE)
