package kr.co.nuni.gildam.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KoreanLabelLocalizerTest {
    private val ko = """["coalesce",["get","name:ko"],["get","name"]]"""

    @Test fun detectsNameExpressions() {
        assertTrue(KoreanLabelLocalizer.usesNameField("""["case",["has","name:nonlatin"],["concat",["get","name:latin"]," ",["get","name:nonlatin"]],["coalesce",["get","name_en"],["get","name"]]]"""))
        assertTrue(KoreanLabelLocalizer.usesNameField("\"{name:latin}\""))
        assertTrue(KoreanLabelLocalizer.usesNameField("""["get","name"]"""))
    }

    @Test fun keepsNonNameLabels() {
        assertFalse(KoreanLabelLocalizer.usesNameField("""["to-string",["get","ref"]]"""))
        assertFalse(KoreanLabelLocalizer.usesNameField("\"{housenumber}\""))
        assertFalse(KoreanLabelLocalizer.usesNameField("""["get","network"]"""))
        assertFalse(KoreanLabelLocalizer.usesNameField("not json ["))
    }

    @Test fun localizesStyleAndKeepsEverythingElse() {
        val style = """{"version":8,"sources":{"s":{"type":"vector","url":"https://x"}},"glyphs":"g","layers":[
            {"id":"bg","type":"background"},
            {"id":"place","type":"symbol","layout":{"text-field":["coalesce",["get","name_en"],["get","name"]],"text-size":12},"paint":{"text-color":"#333"}},
            {"id":"shield","type":"symbol","layout":{"text-field":["to-string",["get","ref"]]}}]}"""
        val out = Json.parseToJsonElement(KoreanLabelLocalizer.localizeStyleJson(style)).jsonObject
        val layers = out["layers"]!!.jsonArray.map { it.jsonObject }
        assertEquals(ko, layers[1]["layout"]!!.jsonObject["text-field"].toString())
        assertEquals("12", layers[1]["layout"]!!.jsonObject["text-size"].toString())
        assertEquals("""["to-string",["get","ref"]]""", layers[2]["layout"]!!.jsonObject["text-field"].toString())
        assertEquals("\"g\"", out["glyphs"].toString())
        assertEquals(3, layers.size)
    }

    @Test fun bundledOpenFreeMapStylesGetKoreanLabels() {
        listOf("liberty", "dark").forEach { name ->
            val f = listOf(File("src/main/assets/styles/$name.json"), File("map/src/main/assets/styles/$name.json")).first { it.exists() }
            val src = Json.parseToJsonElement(f.readText()).jsonObject
            assertTrue(src["sources"].toString().contains("tiles.openfreemap.org"))
            val out = Json.parseToJsonElement(KoreanLabelLocalizer.localizeStyleJson(f.readText())).jsonObject
            val tfs = out["layers"]!!.jsonArray.mapNotNull { (it as JsonObject)["layout"]?.jsonObject?.get("text-field")?.toString() }
            assertTrue("$name has labels", tfs.size > 10)
            assertTrue(tfs.none { it.contains("name_en") || it.contains("name:latin") })
            assertTrue(tfs.all { it == ko || it.contains("\"ref\"") })
        }
    }
}
