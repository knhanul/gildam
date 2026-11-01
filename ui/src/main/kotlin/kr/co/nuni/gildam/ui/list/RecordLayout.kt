package kr.co.nuni.gildam.ui.list

/** 기록 보관함을 카드로 볼지 한 줄 목록으로 볼지 */
enum class RecordLayout(val labelKo: String) {
    CARD("카드로 보기"),
    LIST("리스트로 보기");

    companion object {
        fun fromName(name: String?): RecordLayout =
            entries.firstOrNull { it.name == name } ?: CARD
    }
}
