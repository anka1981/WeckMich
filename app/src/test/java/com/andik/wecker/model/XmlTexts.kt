package com.andik.wecker.model

import com.andik.wecker.R
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * [Texts] für Unit-Tests: liest die strings.xml einer Sprache (mit Englisch als Rückfall) direkt,
 * so wie Android es zur Laufzeit täte (Pluralregel für die Sprachen dieser App).
 */
class XmlTexts(lang: String?, override val is24Hour: Boolean = true) : Texts {
    override val locale: Locale = Locale.forLanguageTag(lang ?: "en")
    private val strings = mutableMapOf<String, String>()
    private val plurals = mutableMapOf<String, Map<String, String>>()
    private val arrays = mutableMapOf<String, Array<String>>()

    init {
        load(File("src/main/res/values/strings.xml"))
        if (lang != null) load(File("src/main/res/values-$lang/strings.xml"))
    }

    private fun load(f: File) {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        fun Element.items() = getElementsByTagName("item").let { l -> (0 until l.length).map { l.item(it) as Element } }
        fun clean(t: String) = t.trim().removeSurrounding("\"").replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"")
        val root = doc.documentElement
        root.getElementsByTagName("string").let { l ->
            for (i in 0 until l.length) (l.item(i) as Element).let { strings[it.getAttribute("name")] = clean(it.textContent) }
        }
        root.getElementsByTagName("plurals").let { l ->
            for (i in 0 until l.length) (l.item(i) as Element).let { e ->
                plurals[e.getAttribute("name")] = e.items().associate { it.getAttribute("quantity") to clean(it.textContent) }
            }
        }
        root.getElementsByTagName("string-array").let { l ->
            for (i in 0 until l.length) (l.item(i) as Element).let { e ->
                arrays[e.getAttribute("name")] = e.items().map { clean(it.textContent) }.toTypedArray()
            }
        }
    }

    private fun name(cls: Class<*>, id: Int) = cls.fields.first { it.getInt(null) == id }.name

    override fun str(id: Int, vararg args: Any): String =
        strings.getValue(name(R.string::class.java, id)).let { if (args.isEmpty()) it else String.format(locale, it, *args) }

    override fun plural(id: Int, n: Int, vararg args: Any): String {
        val forms = plurals.getValue(name(R.plurals::class.java, id))
        // Wie Android: Im Französischen und Portugiesischen gilt auch 0 als „one“.
        val one = n == 1 || (n == 0 && locale.language in setOf("fr", "pt"))
        val f = (if (one) forms["one"] else null) ?: forms.getValue("other")
        return String.format(locale, f, *(if (args.isEmpty()) arrayOf(n) else args))
    }

    override fun array(id: Int): Array<String> = arrays.getValue(name(R.array::class.java, id))
}
