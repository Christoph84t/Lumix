# S7 PLC Monitor – Android App

Android-App zur Kommunikation mit **Siemens S7 SPSen** über das ISO-on-TCP Protokoll (Port 102).

## Unterstützte SPS-Modelle

| SPS-Modell | Rack | Slot |
|---|---|---|
| S7-300 | 0 | 2 |
| S7-400 | 0 | 1–4 (je nach Konfiguration) |
| S7-1200 | 0 | 1 |
| S7-1500 | 0 | 1 |
| LOGO! 0BA8+ | 0 | 1 |

## Features

- **Tag-Monitor** – Lesen und Schreiben beliebiger SPS-Variablen mit konfigurierbarem Polling-Intervall
- **DB-Browser** – Rohe Byte-Ansicht von Datenbausteinen, Merkern, Ein- und Ausgängen
- **Unterstützte Datentypen** – Bool, Byte, Word, Int, DWord, DInt, Real
- **Speicherbereiche** – DB, Merker (M), Eingänge (E), Ausgänge (A), Zähler (Z), Timer (T)
- **Keine externen Bibliotheken** – reines Kotlin, implementiert das S7-Protokoll von Grund auf

## Protokoll-Stack

```
TCP (Port 102)
└── TPKT (RFC 1006)
    └── COTP (ISO 8073)
        └── S7 Communication (S7comm)
```

## Voraussetzungen

- Android 8.0 (API 26) oder höher
- Netzwerkzugriff auf die SPS (TCP Port 102)
- Bei **S7-1200/1500**: PUT/GET-Zugriff in den Verbindungseigenschaften des TIA-Portals aktivieren

## Projektstruktur

```
app/src/main/java/com/lumix/s7plc/
├── protocol/
│   ├── S7Client.kt       ← Haupt-Kommunikationsklasse
│   ├── S7Packet.kt       ← Protokoll-Pakete (TPKT, COTP, S7)
│   ├── S7Area.kt         ← SPS-Speicherbereiche und Datentypen
│   └── S7Exception.kt    ← Fehlerbehandlung
├── model/
│   ├── PlcConnection.kt  ← Verbindungsparameter & Tag-Definitionen
│   └── PlcRepository.kt  ← Datenzugriffs-Schicht
├── viewmodel/
│   ├── ConnectionViewModel.kt
│   ├── MonitorViewModel.kt
│   └── DataBlockViewModel.kt
└── ui/
    ├── MainActivity.kt        ← Verbindungsaufbau
    ├── MonitorActivity.kt     ← Tag-Monitor
    ├── DataBlockActivity.kt   ← DB-Browser
    └── adapter/
        ├── TagAdapter.kt
        └── ByteRowAdapter.kt
```

## S7Client – Verwendungsbeispiel

```kotlin
val client = S7Client()

// Verbinden (S7-1200, Rack 0, Slot 1)
client.connect("192.168.0.1", rack = 0, slot = 1)

// DB1.DBW0 lesen (2 Bytes = 1 Word)
val bytes = client.readDB(dbNumber = 1, start = 0, size = 2)
val wordValue = client.getWord(bytes, 0)

// DB1.DBD4 als Real lesen
val realBytes = client.readDB(1, 4, 4)
val temperature = client.getReal(realBytes, 0)

// M0.0 (Merkerbit) lesen
val merker = client.readMerker(start = 0, size = 1)
val bit0 = client.getBoolean(merker, 0, 0)

// In DB1.DBW0 schreiben
val writeBuffer = ByteArray(2)
client.setWord(writeBuffer, 0, 1234)
client.writeDB(1, 0, writeBuffer)

client.disconnect()
```

## Hinweis für S7-1200/1500

Im TIA-Portal unter **Gerätekonfiguration → Verbindungsmechanismen** muss
"PUT/GET-Kommunikation von einem entfernten Partner erlauben" aktiviert sein.

## Build

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
