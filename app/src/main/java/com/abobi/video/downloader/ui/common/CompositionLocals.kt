package com.abobi.video.downloader.ui.common

import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.abobi.video.downloader.ui.theme.DEFAULT_SEED_COLOR
import com.abobi.video.downloader.util.DarkThemePreference
import com.abobi.video.downloader.util.PreferenceUtil
import com.abobi.video.downloader.util.STYLE_VIBRANT
import com.kyant.monet.LocalTonalPalettes
import com.kyant.monet.PaletteStyle
import com.kyant.monet.TonalPalettes.Companion.toTonalPalettes

val LocalDarkTheme = compositionLocalOf { DarkThemePreference() }
val LocalSeedColor = compositionLocalOf { DEFAULT_SEED_COLOR }
val LocalWindowWidthState = staticCompositionLocalOf { WindowWidthSizeClass.Compact }
val LocalDynamicColorSwitch = compositionLocalOf { false }
val LocalPaletteStyleIndex = compositionLocalOf { 0 }

@Composable
fun SettingsProvider(windowWidthSizeClass: WindowWidthSizeClass, content: @Composable () -> Unit) {
    PreferenceUtil.AppSettingsStateFlow.collectAsState().value.run {
        CompositionLocalProvider(
            LocalDarkTheme provides darkTheme,
            LocalSeedColor provides DEFAULT_SEED_COLOR,
            LocalPaletteStyleIndex provides STYLE_VIBRANT,
            LocalTonalPalettes provides if (isDynamicColorEnabled && Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(
                LocalContext.current
            ).toTonalPalettes()
            else Color(DEFAULT_SEED_COLOR).toTonalPalettes(PaletteStyle.Vibrant),
            LocalWindowWidthState provides windowWidthSizeClass,
            LocalDynamicColorSwitch provides isDynamicColorEnabled,
            content = content
        )
    }
}