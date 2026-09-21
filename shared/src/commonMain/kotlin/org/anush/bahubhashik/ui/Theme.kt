package org.anush.bahubhashik.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Deliberately large type throughout. The people using this are in their
 * sixties and seventies, often reading a script they're more comfortable with
 * than the interface language, so nothing here is smaller than 18sp.
 */
private val Scheme = lightColorScheme(
    primary = Color(0xFF7A3E12),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF2B1600),
    secondary = Color(0xFF00696E),
    onSecondary = Color.White,
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFF3DFD1),
    onSurfaceVariant = Color(0xFF52443A),
    error = Color(0xFFBA1A1A),
)

private val BigType = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontSize = 21.sp),
        bodyLarge = bodyLarge.copy(fontSize = 20.sp, lineHeight = 30.sp),
        bodyMedium = bodyMedium.copy(fontSize = 18.sp, lineHeight = 27.sp),
        labelLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun BahuBhashikTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = BigType, content = content)
}
