# 💡 LUMIX – Lichtshow Programmer

Web-App zur Programmierung und Steuerung von HappyLighting-kompatiblen BLE LED-Strahlern – direkt im Browser, ohne Installation.

## ✨ Features

- 🎨 **Timeline-Editor** – Farb-, Weiß-, Effekt-, An/Aus- und Pause-Schritte
- 🔵 **Web Bluetooth** – direkte BLE-Verbindung ohne App oder Server
- 🔍 **Diagnose-Modus** – automatisches Erkennen von Write-Characteristics
- 🔁 **Loop-Wiedergabe** – Shows endlos abspielen
- 💾 **Import/Export** – Shows als JSON speichern und laden

## 🌐 Live Demo

Nach dem Push verfügbar unter:
`https://DEIN_USER.github.io/lumix/`

## 📱 Kompatibilität

| Plattform | Support |
|-----------|---------|
| Chrome / Edge (Desktop) | ✅ |
| Chrome (Android) | ✅ |
| Safari / iOS | ❌ (kein Web Bluetooth) |

## 🚀 Nutzung

Einfach `index.html` im Browser öffnen oder lokal hosten:

```bash
python3 -m http.server 8080
```

## 📡 BLE Protokoll

| Protokoll | Write UUID |
|-----------|-----------|
| HappyLighting | `0000ffd9-0000-1000-8000-00805f9b34fb` |
| Magic Home | `0000ffe9-0000-1000-8000-00805f9b34fb` |
