package com.andik.wecker.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Eigene Wecktöne werden beim Auswählen in den geräteverschlüsselten App-Speicher kopiert. So muss
 * beim Klingeln nichts aus einer Cloud oder einem anderen App-Anbieter geladen werden (das konnte
 * den Klingeldienst blockieren), und der Ton ist auch vor dem ersten Entsperren nach einem Neustart
 * lesbar.
 */
object SoundFiles {
    private const val MAX_BYTES = 30L * 1024 * 1024

    private fun dir(context: Context) =
        File(context.applicationContext.createDeviceProtectedStorageContext().filesDir, "sounds").apply { mkdirs() }

    /** Kopiert [source]; liefert die lokale Datei-URI oder `null` (zu groß, unlesbar). */
    fun import(context: Context, source: Uri): Uri? {
        val target = File(dir(context), UUID.randomUUID().toString())
        return try {
            var total = 0L
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IllegalStateException("zu groß")
                        out.write(buf, 0, n)
                    }
                }
            } ?: return null
            Uri.fromFile(target)
        } catch (e: Exception) {
            target.delete()
            null
        }
    }

    /**
     * Der eingebaute Piepton – über den Ressourcennamen, nicht die ID: Die ID kann sich mit jedem Build
     * ändern, und ein Kanalton lässt sich nach dem Anlegen des Kanals nie mehr ändern.
     */
    fun builtinUri(context: Context) = "android.resource://${context.packageName}/raw/fallback_alarm"

    /** Entfernt kopierte Töne, die kein Wecker mehr verwendet. */
    fun cleanup(context: Context, inUse: Collection<String?>) {
        val used = inUse.filterNotNull().toSet()
        dir(context).listFiles()?.forEach { f -> if (Uri.fromFile(f).toString() !in used) f.delete() }
    }
}
