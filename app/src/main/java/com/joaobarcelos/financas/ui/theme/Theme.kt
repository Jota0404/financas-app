package com.joaobarcelos.financas.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Verde do Fôlego, a cor do ícone, para os celulares sem cor dinâmica (Android 8 a 11)
private val ClaroVerde = lightColorScheme(
    primary = Color(0xFF2E7D4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1C8),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4F6354),
    secondaryContainer = Color(0xFFD1E8D5),
    tertiary = Color(0xFF3C6472),
    tertiaryContainer = Color(0xFFC0E9FA),
)

private val EscuroVerde = darkColorScheme(
    primary = Color(0xFF98D5AD),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF12512F),
    onPrimaryContainer = Color(0xFFB4F1C8),
    secondary = Color(0xFFB6CCB9),
    secondaryContainer = Color(0xFF374B3D),
    tertiary = Color(0xFFA4CDDD),
    tertiaryContainer = Color(0xFF234C5A),
)

@Composable
fun FinancasTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        // Cor dinâmica (do papel de parede) existe a partir do Android 12
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> EscuroVerde
        else -> ClaroVerde
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
