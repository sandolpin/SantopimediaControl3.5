package com.sandolpin.santopimedia35.mainactivity

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.sandolpin.santopimedia35.*
import com.sandolpin.santopimedia35.lyricsscreen.*
import com.sandolpin.santopimedia35.settingscreen.*

// アプリのテーマ（ダイナミックカラー）とシステムバー色

@Composable
fun SantopimediaThemeWrapper() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tempRepo = remember { com.sandolpin.santopimedia35.database.SettingsRepository(context) }
    val settings by tempRepo.settingsFlow.collectAsState(
        initial = com.sandolpin.santopimedia35.database.AppSettings()
    )
    val darkTheme = when (settings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    // ★ 以前は darkColorScheme()/lightColorScheme() の標準色をそのまま使っていたため、
    //   MaterialTheme.colorScheme.primary がMaterial3標準の紫(0xFF6750A4系)に固定されていた
    //   （設定画面のスイッチ・ラジオボタン・フィルターチップ等、accentColorを指定していない
    //   箇所すべてに影響していた）。
    //   Android 12(API31)以降は dynamicLightColorScheme/dynamicDarkColorScheme を使い、
    //   端末の壁紙から生成されるMaterial Youの配色に従うようにする。
    //   API31未満はdynamic color自体が存在しないAPIのため、従来の紫ベース配色にフォールバックする。
    val baseColorScheme = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) darkColorScheme() else lightColorScheme()
    }
    // background と surface だけ純白・純黒に上書きする（dynamic color時も含め統一する）
    val colorScheme = if (darkTheme) {
        baseColorScheme.copy(
            background = Color.Black,
            surface    = Color(0xFF121212)
        )
    } else {
        baseColorScheme.copy(
            background = Color.White,
            surface    = Color.White
        )
    }
    MaterialTheme(colorScheme = colorScheme) {
        SantopimediaApp()
    }
}

@Composable
fun SystemBarColorEffect(accentColor: Color, darkTheme: Boolean) {
    val view = LocalView.current
    val window = (view.context as? android.app.Activity)?.window ?: return
    val barColor = remember(accentColor, darkTheme) {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (accentColor.red   * 255).toInt(),
            (accentColor.green * 255).toInt(),
            (accentColor.blue  * 255).toInt(),
            hsv
        )
        hsv[2] = (hsv[2] * if (darkTheme) 0.25f else 0.35f).coerceIn(0f, 1f)
        hsv[1] = (hsv[1] * 0.7f).coerceIn(0f, 1f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }
    SideEffect {
        window.statusBarColor     = barColor.toArgb()
        window.navigationBarColor = barColor.toArgb()
        val ctrl = WindowInsetsControllerCompat(window, view)
        val useDarkIcons = barColor.luminance() > 0.179f
        ctrl.isAppearanceLightStatusBars     = useDarkIcons
        ctrl.isAppearanceLightNavigationBars = useDarkIcons
    }
}