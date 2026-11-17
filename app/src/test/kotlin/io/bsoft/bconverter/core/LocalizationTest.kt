package io.bsoft.bconverter.core

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Guards the translations: every language has every translatable string, with the same placeholders and plural forms. */
class LocalizationTest {
    private val res: File = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }
    private val languages = mapOf("fr" to "values-fr", "es" to "values-es", "zh" to "values-zh-rCN", "ja" to "values-ja")

    private fun load(dir: String): Pair<Map<String, String>, Map<String, List<String>>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$dir/strings.xml"))
        val strings = mutableMapOf<String, String>()
        val plurals = mutableMapOf<String, List<String>>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            when (e.tagName) {
                "string" -> if (e.getAttribute("translatable") != "false") strings[e.getAttribute("name")] = e.textContent
                "plurals" -> plurals[e.getAttribute("name")] =
                    (0 until e.getElementsByTagName("item").length).map { (e.getElementsByTagName("item").item(it) as Element).getAttribute("quantity") }
            }
        }
        return strings to plurals
    }

    private fun placeholders(s: String) = Regex("%(\\d\\$)?[ds]").findAll(s).map { it.value }.sorted().toList()
    private val base = load("values")

    @Test fun every_language_has_every_translatable_string() {
        languages.forEach { (lang, dir) ->
            val (strings, _) = load(dir)
            assertEquals("$lang is missing strings", emptySet<String>(), base.first.keys - strings.keys)
            assertEquals("$lang has strings the app does not use", emptySet<String>(), strings.keys - base.first.keys)
        }
    }

    @Test fun placeholders_match_the_english_text() {
        languages.forEach { (lang, dir) ->
            load(dir).first.forEach { (name, text) ->
                assertEquals("$lang/$name placeholders", placeholders(base.first.getValue(name)), placeholders(text))
            }
        }
    }

    @Test fun plurals_cover_the_quantities_each_language_needs() {
        val needed = mapOf("fr" to setOf("one", "many", "other"), "es" to setOf("one", "many", "other"), "zh" to setOf("other"), "ja" to setOf("other"))
        languages.forEach { (lang, dir) ->
            val (_, plurals) = load(dir)
            base.second.keys.forEach { name ->
                assertEquals("$lang/$name quantities", needed.getValue(lang), plurals.getValue(name).toSet())
            }
        }
    }

    @Test fun the_language_picker_lists_exactly_the_shipped_languages() {
        val text = File(res, "xml/locales_config.xml").readText()
        val listed = Regex("android:name=\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("en", "fr", "es", "zh-CN", "ja"), listed)
        assertTrue(languages.values.all { File(res, it).isDirectory })
    }
}
