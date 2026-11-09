package corsica.universita.carnet.ui.theme

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

private val LightPotagerColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0A3810),
    secondary = Color(0xFF558B2F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEDC8),
    onSecondaryContainer = Color(0xFF1B3706),
    tertiary = Color(0xFFF57C00),
    background = Color(0xFFF4F8F3),
    surface = Color.White,
)

private val DarkPotagerColors = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF0A3810),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFAED581),
    onSecondary = Color(0xFF1B3706),
    secondaryContainer = Color(0xFF33691E),
    onSecondaryContainer = Color(0xFFDCEDC8),
    tertiary = Color(0xFFFFB74D),
)

@Composable
fun CarnetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkPotagerColors
        else -> LightPotagerColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

