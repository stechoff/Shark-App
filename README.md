# 🦈 Shark Control – Android App

Inoffizielle Android-Steuerungs-App für Shark Saugroboter (IQ, AI, Matrix Serien).

## Unterstützte Geräte

| Modell              | Kompatibilität |
|---------------------|----------------|
| Shark IQ RV1000er   | ✅ Vollständig  |
| Shark AI RV2000er   | ✅ Vollständig  |
| Shark Matrix / Ultra| ✅ Vollständig  |
| Shark ION (ältere)  | ⚠️ Teilweise    |

## Funktionen

- **Anmelden** mit deinen SharkClean-Zugangsdaten
- **Gerätestatus** in Echtzeit (Batterie, Modus, Leistung)
- **Steuerbefehle:** Starten, Stoppen, Pausieren, Zur Basis
- **Automatische Aktualisierung** alle 10 Sekunden
- **Mehrere Geräte** verwaltbar über Dropdown
- Dark Mode Design

## Technischer Hintergrund

Die App nutzt die **Ayla Networks IoT API**, die Shark/Ninja intern für alle WLAN-Modelle verwendet.  
Dies ist ein Reverse-Engineering-Ergebnis der Community (primär basierend auf [sharkiq](https://github.com/ajmarks/sharkiq)).

### API-Endpunkte

```
Auth:    https://user-field.aylanetworks.com/users/sign_in.json
Geräte:  https://ads-field.aylanetworks.com/apiv1/devices.json
Status:  https://ads-field.aylanetworks.com/apiv1/dsns/{DSN}/properties.json
Befehl:  https://ads-field.aylanetworks.com/apiv1/dsns/{DSN}/properties/{PROP}/datapoints.json
```

### Wichtige Properties

| Property                        | Beschreibung              |
|---------------------------------|---------------------------|
| `GET_Operating_Mode`            | Aktueller Modus           |
| `SET_Operating_Mode`            | Befehl senden             |
| `GET_Battery_Capacity`          | Akkustand (0-100)         |
| `GET_Power_Mode`                | Saugleistung              |
| `GET_Charging_Status`           | Ladesstatus               |
| `GET_Cleaning_Statistics_Minutes` | Reinigungsdauer         |

### Operating Modes

| Wert       | Bedeutung              |
|------------|------------------------|
| `start`    | Starten / Aktiv        |
| `stop`     | Stoppen                |
| `pause`    | Pause                  |
| `return`   | Zur Ladestation        |

## Projektstruktur

```
app/src/main/
├── java/com/sharkcontrol/
│   ├── api/
│   │   └── AylaApiClient.java    # Gesamte API-Kommunikation
│   ├── model/
│   │   ├── SharkDevice.java      # Gerätedaten
│   │   └── RobotStatus.java      # Statusdaten
│   └── ui/
│       ├── LoginActivity.java    # Anmelde-Screen
│       └── MainActivity.java     # Steuer-Screen
└── res/
    ├── layout/
    │   ├── activity_login.xml
    │   └── activity_main.xml
    └── drawable/ (Button-Styles)
```

## Einrichtung in Android Studio

1. **Android Studio** öffnen (Electric Eel oder neuer)
2. **"Open"** → diesen Ordner `SharkControl/` auswählen
3. **Gradle Sync** abwarten
4. Gerät/Emulator verbinden
5. **Run** ▶ drücken

## Anmeldung

Verwende dieselbe **E-Mail + Passwort** wie in der offiziellen SharkClean App.  
Der Token wird lokal im `SharedPreferences` gespeichert.

## ⚠️ Hinweise

- Dies ist eine **inoffizielle App** – Shark/Ninja könnten die API jederzeit ändern.
- Die App sendet keine Daten an Dritte; kommuniziert nur mit den Ayla-Servern.
- `app_id`/`app_secret` sind im SharkClean APK öffentlich auffindbar.

## Erweiterungsmöglichkeiten

- [ ] Reinigungskarte anzeigen (soweit verfügbar)
- [ ] Zeitpläne verwalten
- [ ] Saugleistung einstellen
- [ ] Push-Benachrichtigungen
- [ ] Widget für Homescreen
- [ ] Alexa/Google Home Integration

## Lizenz

MIT – Privat- und Lernzwecke. Kein Zusammenhang mit SharkNinja.
