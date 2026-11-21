package kr.co.nuni.gildam.ui.illustration

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kr.co.nuni.gildam.ui.R
import kr.co.nuni.gildam.ui.theme.GildamPalette

/**
 * 길담 로고. 기본은 nuni_icon(실제 파일명 gildam_icon) 을 사용하고,
 * 테마 팔레트에 따라 orange / purple 버전을 선택합니다.
 *
 * - SUNNY  → orange
 * - SUNSET → purple
 * - 기타(MINT, CLASSIC) → 기본
 */
@Composable
fun GildamLogo(
    palette: GildamPalette,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val resId = when (palette) {
        GildamPalette.SUNNY -> R.drawable.gildam_icon_orange
        GildamPalette.SUNSET -> R.drawable.gildam_icon_purple
        else -> R.drawable.gildam_icon
    }
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
