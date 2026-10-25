package kr.co.nuni.gildam.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * 지도 라벨을 한글로: 이름을 쓰는 text-field 를 ["coalesce", ["get","name:ko"], ["get","name"]] 로 바꿉니다.
 * 도로 번호(ref) 같은 이름 아닌 라벨은 그대로 둡니다. (순수 Kotlin, 단위 테스트 대상)
 */
object KoreanLabelLocalizer {
    private val json = Json { prettyPrint = false }

    /** name:ko 우선, 없으면 name */
    val KOREAN_TEXT_FIELD: JsonArray = buildJsonArray {
        add(JsonPrimitive("coalesce"))
        add(buildJsonArray { add(JsonPrimitive("get")); add(JsonPrimitive("name:ko")) })
        add(buildJsonArray { add(JsonPrimitive("get")); add(JsonPrimitive("name")) })
    }

    private val NAME_KEY = Regex("^name([:_].*)?$")

    /** text-field 값(문자열 템플릿 "{name}" 또는 표현식)이 지명(name 계열)을 읽는지 */
    fun usesNameField(textField: JsonElement): Boolean = when (textField) {
        is JsonPrimitive -> textField.contentOrNull?.let { Regex("\\{(name[^}]*)\\}").containsMatchIn(it) } ?: false
        is JsonArray -> {
            val op = (textField.firstOrNull() as? JsonPrimitive)?.contentOrNull
            if ((op == "get" || op == "has") && textField.size >= 2 &&
                ((textField[1] as? JsonPrimitive)?.contentOrNull?.let(NAME_KEY::matches) == true)
            ) true else textField.any { usesNameField(it) }
        }
        is JsonObject -> textField.values.any { usesNameField(it) }
    }

    /** 표현식 JSON 문자열 버전 (런타임 레이어 패치용) */
    fun usesNameField(textFieldJson: String): Boolean =
        runCatching { usesNameField(json.parseToJsonElement(textFieldJson)) }.getOrDefault(false)

    /** 스타일 JSON 전체를 받아 이름 라벨을 한글로 바꾼 스타일 JSON 을 돌려줍니다. */
    fun localizeStyleJson(styleJson: String): String {
        val root = json.parseToJsonElement(styleJson).jsonObject
        val layers = root["layers"]?.jsonArray ?: return styleJson
        val newLayers = JsonArray(layers.map { layer ->
            val obj = layer as? JsonObject ?: return@map layer
            val layout = obj["layout"] as? JsonObject ?: return@map layer
            val tf = layout["text-field"] ?: return@map layer
            if (!usesNameField(tf)) return@map layer
            JsonObject(obj + ("layout" to JsonObject(layout + ("text-field" to KOREAN_TEXT_FIELD))))
        })
        return json.encodeToString(JsonObject.serializer(), JsonObject(root + ("layers" to newLayers)))
    }
}
