# Weck mich

Android-Wecker im Stil der Android-Uhr – mit Wiederholungsregeln, die die eingebaute Uhr nicht kann:

- **jede n-te Woche** an bestimmten Wochentagen (z. B. jede 2. Woche montags)
- **monatlich am n-ten Wochentag** (z. B. jeden 1. Dienstag, jeden 1. und 3. Donnerstag, jeden letzten Freitag)
- **monatlich an einem Tag** (z. B. am 15. oder am letzten Tag des Monats)
- alle n Tage, jährlich, einmalig – jeweils mit Startdatum und optionalem Enddatum

Der Wiederholungsdialog zeigt die nächsten Termine als Vorschau.

## Funktionen

- Weckton aus den Weck- und Klingeltönen des Geräts oder einer eigenen Audiodatei, mit Vorhören
- Schlummerdauer und ansteigende Lautstärke pro Wecker oder als Standard
- Klingelt zuverlässig in Doze, Stromsparmodus und „Nicht stören“ (`AlarmManager.setAlarmClock`, Wecker-Audiokanal)
- Funktioniert nach einem Neustart schon vor dem ersten Entsperren (Direct Boot)
- Hinweis „Verpasster Wecker“, wenn das Gerät aus war oder der Wecker ohne Reaktion verstummt ist
- Prüft Berechtigungen, Weckerlautstärke und „Nicht stören“ und zeigt, was das Klingeln verhindern würde
- Probewecker in 1 Minute zum Testen

## Bauen

Voraussetzung: Android SDK, JDK 17 (z. B. das JBR aus Android Studio).

```
./gradlew testDebugUnitTest   # Tests der Wiederholungsregeln
./gradlew assembleDebug       # Testversion „Weck mich (Test)“, installierbar neben der echten App
./gradlew assembleRelease     # signierte Release-Version
```

Für den Release-Build wird `keystore.properties` im Projektordner benötigt (nicht im Repository):

```
storeFile=app/keystore/weckmich-release.jks
storePassword=…
keyAlias=weckmich
keyPassword=…
```
