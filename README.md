# Gemma Lokal

Einfache Android-App für lokale Text-Chats mit **Gemma 4 E4B-it**. Jeder Chat hat einen eigenen Verlauf. Ein neuer Chat erhält keine Nachrichten aus anderen Chats.

## Installation

Die installierbare APK liegt unter `Gemma-Lokal.apk`. Unterstützt werden Android 8.0 oder neuer und Geräte mit `arm64-v8a`. Das Modell benötigt etwa 3,66 GB Speicherplatz und zusätzlich mehrere GB freien Arbeitsspeicher. Ob es auf einem bestimmten Gerät flüssig läuft, hängt von dessen Hardware ab.

Nach der Installation in der App **⚙ → Modell herunterladen** wählen. Android lädt dann das quantisierte Modell von [LiteRT Community auf Hugging Face](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm). Die App prüft anschließend Größe und SHA-256-Prüfsumme. Danach **Modell laden** wählen, um es in den Arbeitsspeicher zu laden und den Chat freizuschalten. **Modell entladen** gibt den Arbeitsspeicher wieder frei, während die heruntergeladene Datei und alle Chats erhalten bleiben. Der Download kann Mobilfunkdaten verbrauchen.

## Datenschutz

Fragen, Antworten und Chat-Verläufe werden nur im privaten App-Speicher gespeichert. Die App enthält keine Anmeldung, keine Analyse-SDKs und keine Telemetrie. Die einzige Internetverbindung der App dient dem ausdrücklich ausgelösten Modelldownload. Antworten werden auf dem Gerät mit [LiteRT-LM](https://ai.google.dev/edge/litert-lm) berechnet.

## Aus dem Quellcode bauen

Android SDK mit Plattform 35 und Build Tools 35, JDK 17 sowie Gradle 8.14.3 verwenden:

```powershell
./gradlew.bat :app:assembleDebug
```

Ergebnis: `app/build/outputs/apk/debug/app-debug.apk`. Der Debug-Build ist mit einem lokalen Debug-Schlüssel signiert und direkt installierbar. Für eine Veröffentlichung im Play Store wäre ein eigener Release-Schlüssel nötig.

Modell: `litert-community/gemma-4-E4B-it-litert-lm/gemma-4-E4B-it.litertlm`, SHA-256 `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`. Die Modellgewichte werden nicht in der APK verteilt.
