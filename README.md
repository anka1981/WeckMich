# Weck mich / Wake Me

Android-Wecker im Stil der Android-Uhr – für Rhythmen, die die eingebaute Uhr nicht kann: A/B-Wochen,
Wechselschicht, Stundenpläne, die jede zweite Woche anders aussehen, oder Termine wie „jeden 1. Dienstag“.

- **jede n-te Woche** an bestimmten Wochentagen (z. B. jede 2. Woche montags); das Startdatum legt fest, welche Woche zählt
- **monatlich am n-ten Wochentag** (z. B. jeden 1. Dienstag, jeden 1. und 3. Donnerstag, jeden letzten Freitag)
- **monatlich an einem Tag** (z. B. am 15. oder am letzten Tag des Monats)
- alle n Tage, jährlich, einmalig – jeweils mit Startdatum und optionalem Enddatum

Der Wiederholungsdialog zeigt die nächsten Termine als Vorschau.

## Funktionen

- Aussetzen für Tage, Wochen, Monate, eine Anzahl Termine oder bis zu einem Datum – mit Liste der entfallenden Termine
- Handy umdrehen (Display nach unten) zum Schlummern; abschaltbar
- Weckton aus den Weck- und Klingeltönen des Geräts oder einer eigenen Audiodatei, mit Vorhören
- Schlummerdauer und ansteigende Lautstärke pro Wecker oder als Standard
- Klingelt zuverlässig in Doze, Stromsparmodus und „Nicht stören“ (`AlarmManager.setAlarmClock`, Wecker-Audiokanal)
- Funktioniert nach einem Neustart schon vor dem ersten Entsperren (Direct Boot)
- Hinweis „Verpasster Wecker“, wenn das Gerät aus war oder der Wecker ohne Reaktion verstummt ist
- Prüft Berechtigungen, Weckerlautstärke und „Nicht stören“ und zeigt, was das Klingeln verhindern würde
- Probewecker in 1 Minute zum Testen, Hilfeseite in der App
- Sprachen: Englisch (Standard), Deutsch, Französisch, Spanisch, Italienisch, Portugiesisch, Niederländisch –
  die App folgt der Systemsprache (unter Android 13+ auch pro App einstellbar)
- Keine Internet-Berechtigung, keine Werbung, kein Tracking

## Bauen

Voraussetzung: Android SDK, JDK 17 oder neuer (z. B. das JBR aus Android Studio).

```
./gradlew testDebugUnitTest   # Tests der Wiederholungsregeln, Texte und Umdreh-Erkennung
./gradlew assembleDebug       # Testversion „Weck mich (Test)“, installierbar neben der echten App
./gradlew clean assembleRelease   # Release-Version (signiert, wenn keystore.properties vorhanden ist)
```

Für den signierten Release-Build wird `keystore.properties` im Projektordner benötigt (nicht im Repository):

```
storeFile=app/keystore/weckmich-release.jks
storePassword=…
keyAlias=weckmich
keyPassword=…
```

Ohne diese Datei entsteht eine unsignierte APK (so baut auch F-Droid). Der Release-Build ist
reproduzierbar, siehe [`fdroid/README.md`](fdroid/README.md).

## Übersetzungen

Die Texte stehen in `app/src/main/res/values*/strings.xml`; Englisch (`values/`) ist die Rückfallsprache.
Beschreibungen von Wiederholungsregeln werden aus Bausteinen zusammengesetzt (`model/Format.kt`) –
Ordinalzahlen, Geschlecht der Wochentage und Satzmuster sind je Sprache in den Ressourcen festgelegt.

## Lizenz

GNU General Public License, nur Version 3 (GPL-3.0-only) – siehe [LICENSE](LICENSE). Der eingebaute Ersatz-Piepton
(`app/src/main/res/raw/fallback_alarm.wav`) wurde für dieses Projekt selbst erzeugt und steht unter derselben Lizenz.
