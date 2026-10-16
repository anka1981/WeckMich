# F-Droid

`io.github.anka1981.weckmich.yml` ist das Bau-Rezept für F-Droid. Es wird nicht aus diesem
Repository gelesen, sondern muss in F-Droids Datensammlung
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) unter
`metadata/io.github.anka1981.weckmich.yml` liegen. Die Kopie hier hält den Stand nachvollziehbar.

Beschreibungstexte, Icon und Bildschirmfotos zieht F-Droid direkt aus [`fastlane/`](../fastlane).

## Reproduzierbarer Build

F-Droid baut die App selbst nach und vergleicht das Ergebnis mit der signierten APK aus dem
GitHub-Release (`Binaries:`). Stimmen sie bis auf die Signatur überein, verteilt F-Droid die
APK mit unserer Signatur (`AllowedAPKSigningKeys:` = SHA-256 des Signaturzertifikats). Dafür sind
in `app/build.gradle.kts` die Build-Tools-Version fest vorgegeben sowie `dependenciesInfo` und
`vcsInfo` abgeschaltet.

## Bei jeder neuen Version

1. `versionCode` und `versionName` in `app/build.gradle.kts` erhöhen.
2. Änderungen unter `fastlane/metadata/android/<sprache>/changelogs/<versionCode>.txt` beschreiben.
3. Commit, Tag `v<versionName>` setzen und pushen. Dann aus genau diesem Stand **sauber** bauen:
   `./gradlew clean assembleRelease` – ein inkrementeller Build kann sich (Compose-Compiler) vom
   Nachbau unterscheiden und fiele bei F-Droids Vergleich durch.
4. GitHub-Release `v<versionName>` mit der APK als `WeckMich-<versionName>.apk` anlegen.

Mit `UpdateCheckMode: Tags` findet F-Droid neue Versionen anhand der Tags von selbst.
Im Rezept steht unter `commit:` immer der **volle Commit-Hash** des Tags, nicht der Tag selbst
(Vorgabe der F-Droid-Prüfer).
