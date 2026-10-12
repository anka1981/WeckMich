package com.andik.wecker.model

import android.content.Context
import android.text.format.DateFormat
import java.util.Locale

/**
 * Zugriff auf die übersetzten Texte. In der App liest [AndroidTexts] die Ressourcen der aktuellen
 * Sprache; Unit-Tests setzen eine eigene Implementierung, die die XML-Dateien direkt liest.
 */
interface Texts {
    val locale: Locale
    val is24Hour: Boolean
    fun str(id: Int, vararg args: Any): String
    fun plural(id: Int, n: Int, vararg args: Any): String
    fun array(id: Int): Array<String>
}

/** Liest immer die aktuellen Ressourcen – nach einem Sprachwechsel gelten sofort die neuen Texte. */
class AndroidTexts(context: Context) : Texts {
    private val app = context.applicationContext
    private val res get() = app.resources

    override val locale: Locale get() = res.configuration.locales[0]
    override val is24Hour: Boolean get() = DateFormat.is24HourFormat(app)
    override fun str(id: Int, vararg args: Any): String = if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
    override fun plural(id: Int, n: Int, vararg args: Any): String =
        if (args.isEmpty()) res.getQuantityString(id, n, n) else res.getQuantityString(id, n, *args)
    override fun array(id: Int): Array<String> = res.getStringArray(id)
}
