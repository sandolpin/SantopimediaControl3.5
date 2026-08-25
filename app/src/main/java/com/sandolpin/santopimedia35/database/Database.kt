package com.sandolpin.santopimedia35.database

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

// DataStoreのシングルトン拡張
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "santopi_settings")

// ===== 設定データクラス =====
data class AppSettings(
    // 外観
    val playerUiStyle: String = "A",          // "A" or "B" or "C"
    val extractColorFromArt: Boolean = false,
    val colorExtractPattern: Int = 1,          // 色抽出パターン 1〜5
    val colorSaturation: Float = 1.0f,         // 彩度補正 (0.5～1.5)
    val colorBrightness: Float = 1.0f,         // 明度補正 (0.5～1.5)
    val backgroundStyle: String = "default",   // "default" / "gradient" / "blur" / "animated"
    val gradientStyle: String = "normal",      // "normal"=上:抽出色→下:背景色 / "vivid"=上:抽出色→下:色相+30°
    val blurStyle: String = "normal",          // "normal" / "dark_mode"
    val blurFixedColor: String = "none",       // ぼかし時の色固定 "none" / "white" / "black" / "sync_dark"
    val vibrationEnabled: Boolean = true,
    val vibrationStrength: Int = 30,           // 10〜100ms

    // テキスト
    val titleFontSize: Int = 22,
    val titleFontWeight: Int = 700,
    val artistFontSize: Int = 14,
    val artistFontWeight: Int = 400,
    val albumFontSize: Int = 12,
    val albumFontWeight: Int = 400,

    // 再生画面
    val waveSeekBar: Boolean = false,          // UI-Aのみ有効
    val waveSeekBarStyle: Int = 1,             // 1=シンプル 2=複数波
    val seekBarThickness: Int = 4,             // 3〜15dp
    val seekBarUpdateInterval: Float = 0.5f,   // 0.1 / 0.3 / 0.5 / 1.0 / 0(なし)
    val showWaveform: Boolean = true,
    val showVolumeBar: Boolean = true,

    // キュー画面
    val queueShowMiniPlayer: Boolean = true,
    val queueSwipeFromHome: Boolean = false,

    // アプリ切り替え画面
    val appSelectShowMiniPlayer: Boolean = true,
    val appSelectShowIcon: Boolean = true,

    // テーマ
    val themeMode: String = "system",         // "system" / "light" / "dark"

    // 歌詞設定
    val lyricsBg: String = "normal",          // "player" / "normal" / "gradient" / "blur"
    val lyricsFontSize: Int = 34,             // フォントサイズ
    val lyricsInactiveLight: Boolean = true,  // 非アクティブを細字にする
    val lyricsInterludeThreshold: Int = 15,   // 間奏ドット表示の閾値(秒)
    val lyricsMultiLineThreshold: Float = 2.5f, // 複数行表示の閾値(秒)
    val lyricsFontWeight: Int = 700,          // アクティブ行の太さ(100～900)
    val lyricsLineHeight: Float = 1.4f,       // 行間倍率(1.0～2.0)
    val lyricsLineSpacing: Float = 8f,        // 行と行の間隔(dp)
    val lyricsAutoRetry: Boolean = true,      // 見つからない時タイトルのみ再検索
    val lyricsUseNetwork: Boolean = true,     // ネットワークを使用して歌詞を検索
    val lyricsScrollSpeedMs: Int = 500,       // スクロールの速さ(100〜1000ms)
    val lyricsScrollEasing: String = "decelerate", // 内部使用（設定UIから削除）
    val lyricsOffsetSync: String = "stagger", // 内部使用（設定UIから削除）
    val lyricsOffsetDelayMs: Int = 50,        // 内部使用（設定UIから削除）
    val lyricsOffsetStaggerMs: Int = 50,      // 内部使用（設定UIから削除）

    // 歌詞 追加設定
    val lyricsInactiveScale: Float = 0.98f,   // 非アクティブ歌詞の大きさ(0.97～1.00)
    val lyricsInactiveBlur: Boolean = false,  // 非アクティブ歌詞をぼかす
    val lyricsInactiveBlurRadius: Float = 1.5f, // ぼかし強度(dp): 0=自動(行距離に応じて調整)
    val lyricsScrollAnimType: String = "decelerate_quint", // スクロールアニメ: "decelerate_circ"/"decelerate_quad"/"decelerate_quint"/"decelerate_expo"(減速4種)/"overshoot"
    val lyricsOvershootDistance: Int = 10,     // オーバーシュートの行き過ぎ量(%): 3/5/10/15
    val lyricsFontPath: String = "",          // カスタムフォントファイルのパス

    // 再生画面追加
    val scrollLongTitle: Boolean = true,      // タイトルが長い場合スクロール

    // ホーム画面ショートカット（音量バー両脇のボタン）
    // 選択肢: "lyrics" / "favorite" / "history" / "share" / "repeat" / "shuffle"
    val homeShortcutLeft: String = "lyrics",
    val homeShortcutRight: String = "repeat",

    // アクションメニュー（項目ID をカンマ区切りにした文字列で並び順・非表示を管理）
    // 全項目ID: "share","search","time_seek","lyrics","favorites_list","favorite_toggle","property","settings"
    val actionMenuOrder: String = "share,search,time_seek,lyrics,favorites_list,favorite_toggle,property,settings",
    val actionMenuHidden: String = "", // カンマ区切りで非表示にしたIDを列挙（空文字=すべて表示）

    // バックグラウンド処理
    val backgroundHistoryEnabled: Boolean = false,  // バックグラウンドで履歴記録

    // 歌詞ファイル
    val lyricsFolder: String = "",  // ローカル歌詞ファイルのフォルダパス

    // 歌詞動作
    val lyricsForceInactiveOnEnd: Boolean = true,  // endMs到達で強制非アクティブ
    val sdlrcDisableMultiShow: Boolean = false,     // sdlrc複数同時表示を許可しない
    val sdlrcDisableRight: Boolean = false,         // sdlrc右寄せ表示を許可しない

    // お気に入り
    // お気に入り
    val favoriteViewStyle: String = "list",         // "list" / "grid"
    val favoriteWarningDismissed: Boolean = false,  // 警告バナーを「以後表示しない」にしたか
    // お気に入り表示設定（設定ダイアログ）
    val favoriteShowItem: String = "album",         // "artist" / "album" / "all"（表示する項目）
    val favoriteDisplayMode: String = "list",       // "list" / "grid"（表示方法）
    val favoriteGridColumns: Int = 2,              // グリッド列数 1〜5
    val favoriteCardStyle: String = "border",       // "border"=縁取り / "card"=カード / "shadow"=影
    val favoriteColorFill: Boolean = false,        // アルバムアートに合わせた色塗りつぶし

    // 動的アニメーション背景
    val animatedWhiteIcon: Boolean = true,
    // 動的アニメーション時にアイコン・テキストを白固定
    val animatedColorSource: String = "accent", // "accent"=アクセントカラーから / "album_art"=アルバムアートから強制
    val animatedBgDarkness: Float = 0.88f,      // 背景の色の濃さ(明るさ倍率) 0.6〜1.0。低いほど暗い
    val animatedColorPattern: String = "normal", // 色の混ぜ方: "soft"/"normal"/"vivid"/"complementary"

    // 履歴画面
    val historyCardBackgroundStyle: String = "blur", // "blur"=アルバムアートのぼかし / "animated"=複数色ミックス / "color_fill"=アルバムアート色で塗りつぶし
    val historyTimeDisplayMode: String = "auto",     // "auto"=自動調整 / "seconds"=秒に固定 / "minutes"=分に固定 / "hours"=時間に固定
    val historyShowDate: Boolean = true,             // カードの日時表示に日付を含めるか
    val historyTimeRangeMode: String = "start",      // "start"=開始時間 / "end"=終了時間 / "both"=両方
    val historyArtBorderEnabled: Boolean = false,     // アルバムアートを白で縁取りするか
    val historyShowPlayTime: Boolean = false,        // カードに実際に聴いた時間(〇:〇〇聴きました)を表示するか
    val historyShowProgressBar: Boolean = true,      // カードに再生位置の進捗バーを表示するか
    val historyArtSize: String = "medium",           // "small" / "medium" / "large" / "xlarge"

    // ホーム画面C（上下分割プレーヤー）
    val homeScreenCSecondaryContent: String = "lyrics", // "lyrics"=歌詞 / "queue"=キュー / "history"=りれき
    val homeScreenCCardAlpha: Float = 0.35f, // HomeScreenC(上下分割)のPaneCard背景(白)の透明度 0.0〜1.0

    // 歌詞画面（縦画面）のミニプレーヤー位置
    val lyricsMiniPlayerPosition: String = "top", // "top"=上部 / "bottom"=下部

    // ナビゲーションバー（下部ピル）のショートカットメニューから変更可能な設定
    val navBarBackgroundStyle: String = "accent", // "accent"=アクセントカラーから自動計算 / "solid"=テーマ固定の単色
    val navBarOpacity: Float = 0.97f,   // ナビゲーションバー（下部ピル）自体の不透明度 0.5〜1.0
    val homeIconOpacity: Float = 1.0f,  // ホーム画面（プレーヤー）のコントロールアイコンの不透明度 0.5〜1.0
    val navBarSizeLevel: Int = 3,        // ナビゲーションバー（下部ピル）全体の大きさ 1(小)〜5(大)
    val navBarButtonSpacing: Int = 10,   // ナビゲーションバーのボタン間隔(dp) 2〜20
)

// ===== DataStore キー定義 =====
object SettingsKeys {
    val PLAYER_UI_STYLE = stringPreferencesKey("player_ui_style")
    val EXTRACT_COLOR = booleanPreferencesKey("extract_color")
    val COLOR_EXTRACT_PATTERN = intPreferencesKey("color_extract_pattern")
    val COLOR_SATURATION = floatPreferencesKey("color_saturation")
    val COLOR_BRIGHTNESS = floatPreferencesKey("color_brightness")
    val BACKGROUND_STYLE = stringPreferencesKey("background_style")
    val GRADIENT_STYLE   = stringPreferencesKey("gradient_style")
    val BLUR_STYLE       = stringPreferencesKey("blur_style")
    val BLUR_FIXED_COLOR = stringPreferencesKey("blur_fixed_color")
    val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
    val VIBRATION_STRENGTH = intPreferencesKey("vibration_strength")

    val TITLE_FONT_SIZE = intPreferencesKey("title_font_size")
    val TITLE_FONT_WEIGHT = intPreferencesKey("title_font_weight")
    val ARTIST_FONT_SIZE = intPreferencesKey("artist_font_size")
    val ARTIST_FONT_WEIGHT = intPreferencesKey("artist_font_weight")
    val ALBUM_FONT_SIZE = intPreferencesKey("album_font_size")
    val ALBUM_FONT_WEIGHT = intPreferencesKey("album_font_weight")

    val WAVE_SEEK_BAR = booleanPreferencesKey("wave_seek_bar")
    val WAVE_SEEK_BAR_STYLE = intPreferencesKey("wave_seek_bar_style")
    val SEEK_BAR_THICKNESS = intPreferencesKey("seek_bar_thickness")
    val SEEK_BAR_UPDATE_INTERVAL = floatPreferencesKey("seek_bar_update_interval")
    val SHOW_WAVEFORM = booleanPreferencesKey("show_waveform")
    val SHOW_VOLUME_BAR = booleanPreferencesKey("show_volume_bar")

    val QUEUE_SHOW_MINI_PLAYER = booleanPreferencesKey("queue_show_mini_player")
    val QUEUE_SWIPE_FROM_HOME = booleanPreferencesKey("queue_swipe_from_home")

    val APP_SELECT_SHOW_MINI_PLAYER = booleanPreferencesKey("app_select_show_mini_player")
    val APP_SELECT_SHOW_ICON = booleanPreferencesKey("app_select_show_icon")

    val THEME_MODE = stringPreferencesKey("theme_mode")

    val LYRICS_BG = stringPreferencesKey("lyrics_bg")
    val LYRICS_FONT_SIZE = intPreferencesKey("lyrics_font_size")
    val LYRICS_INACTIVE_LIGHT = booleanPreferencesKey("lyrics_inactive_light")
    val LYRICS_INTERLUDE_THRESHOLD = intPreferencesKey("lyrics_interlude_threshold")
    val LYRICS_MULTI_LINE_THRESHOLD = floatPreferencesKey("lyrics_multi_line_threshold")
    val LYRICS_FONT_WEIGHT = intPreferencesKey("lyrics_font_weight")
    val LYRICS_LINE_HEIGHT = floatPreferencesKey("lyrics_line_height")
    val LYRICS_LINE_SPACING = floatPreferencesKey("lyrics_line_spacing")
    val LYRICS_AUTO_RETRY = booleanPreferencesKey("lyrics_auto_retry")
    val LYRICS_USE_NETWORK = booleanPreferencesKey("lyrics_use_network")
    val LYRICS_SCROLL_SPEED_MS = intPreferencesKey("lyrics_scroll_speed_ms")
    val LYRICS_SCROLL_EASING = stringPreferencesKey("lyrics_scroll_easing")
    val LYRICS_OFFSET_SYNC = stringPreferencesKey("lyrics_offset_sync")
    val LYRICS_OFFSET_DELAY_MS = intPreferencesKey("lyrics_offset_delay_ms")
    val LYRICS_OFFSET_STAGGER_MS = intPreferencesKey("lyrics_offset_stagger_ms")
    val LYRICS_INACTIVE_SCALE = floatPreferencesKey("lyrics_inactive_scale")
    val LYRICS_INACTIVE_BLUR = booleanPreferencesKey("lyrics_inactive_blur")
    val LYRICS_INACTIVE_BLUR_RADIUS = floatPreferencesKey("lyrics_inactive_blur_radius")
    val LYRICS_SCROLL_ANIM_TYPE = stringPreferencesKey("lyrics_scroll_anim_type")
    val LYRICS_OVERSHOOT_DISTANCE = intPreferencesKey("lyrics_overshoot_distance")
    val LYRICS_FONT_PATH = stringPreferencesKey("lyrics_font_path")
    val SCROLL_LONG_TITLE = booleanPreferencesKey("scroll_long_title")
    val HOME_SHORTCUT_LEFT = stringPreferencesKey("home_shortcut_left")
    val HOME_SHORTCUT_RIGHT = stringPreferencesKey("home_shortcut_right")
    val ACTION_MENU_ORDER = stringPreferencesKey("action_menu_order")
    val ACTION_MENU_HIDDEN = stringPreferencesKey("action_menu_hidden")
    val BACKGROUND_HISTORY_ENABLED = booleanPreferencesKey("background_history_enabled")
    val LYRICS_FOLDER = stringPreferencesKey("lyrics_folder")
    val LYRICS_FORCE_INACTIVE_ON_END = booleanPreferencesKey("lyrics_force_inactive_on_end")
    val SDLRC_DISABLE_MULTI_SHOW = booleanPreferencesKey("sdlrc_disable_multi_show")
    val SDLRC_DISABLE_RIGHT = booleanPreferencesKey("sdlrc_disable_right")
    val FAVORITE_VIEW_STYLE = stringPreferencesKey("favorite_view_style")
    val FAVORITE_WARNING_DISMISSED = booleanPreferencesKey("favorite_warning_dismissed")
    val ANIMATED_WHITE_ICON = booleanPreferencesKey("animated_white_icon")
    val ANIMATED_COLOR_SOURCE = stringPreferencesKey("animated_color_source")
    val ANIMATED_BG_DARKNESS = floatPreferencesKey("animated_bg_darkness")
    val ANIMATED_COLOR_PATTERN = stringPreferencesKey("animated_color_pattern")
    val FAVORITE_SHOW_ITEM = stringPreferencesKey("favorite_show_item")
    val FAVORITE_DISPLAY_MODE = stringPreferencesKey("favorite_display_mode")
    val FAVORITE_GRID_COLUMNS = intPreferencesKey("favorite_grid_columns")
    val FAVORITE_CARD_STYLE = stringPreferencesKey("favorite_card_style")
    val FAVORITE_COLOR_FILL = booleanPreferencesKey("favorite_color_fill")
    val HISTORY_CARD_BACKGROUND_STYLE = stringPreferencesKey("history_card_background_style")
    val HISTORY_TIME_DISPLAY_MODE = stringPreferencesKey("history_time_display_mode")
    val HISTORY_SHOW_DATE = booleanPreferencesKey("history_show_date")
    val HISTORY_TIME_RANGE_MODE = stringPreferencesKey("history_time_range_mode")
    val HISTORY_ART_BORDER_ENABLED = booleanPreferencesKey("history_art_border_enabled")
    val HISTORY_SHOW_PLAY_TIME = booleanPreferencesKey("history_show_play_time")
    val HISTORY_SHOW_PROGRESS_BAR = booleanPreferencesKey("history_show_progress_bar")
    val HISTORY_ART_SIZE = stringPreferencesKey("history_art_size")
    val HOME_SCREEN_C_SECONDARY_CONTENT = stringPreferencesKey("home_screen_c_secondary_content")
    val HOME_SCREEN_C_CARD_ALPHA = floatPreferencesKey("home_screen_c_card_alpha")
    val LYRICS_MINI_PLAYER_POSITION = stringPreferencesKey("lyrics_mini_player_position")
    val NAV_BAR_BACKGROUND_STYLE = stringPreferencesKey("nav_bar_background_style")
    val NAV_BAR_OPACITY = floatPreferencesKey("nav_bar_opacity")
    val HOME_ICON_OPACITY = floatPreferencesKey("home_icon_opacity")
    val NAV_BAR_SIZE_LEVEL = intPreferencesKey("nav_bar_size_level")
    val NAV_BAR_BUTTON_SPACING = intPreferencesKey("nav_bar_button_spacing")
}

// ===== SettingsRepository =====
class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            playerUiStyle = prefs[SettingsKeys.PLAYER_UI_STYLE] ?: "A",
            extractColorFromArt = prefs[SettingsKeys.EXTRACT_COLOR] ?: false,
            colorExtractPattern = prefs[SettingsKeys.COLOR_EXTRACT_PATTERN] ?: 1,
            colorSaturation = prefs[SettingsKeys.COLOR_SATURATION] ?: 1.0f,
            colorBrightness = prefs[SettingsKeys.COLOR_BRIGHTNESS] ?: 1.0f,
            backgroundStyle = prefs[SettingsKeys.BACKGROUND_STYLE] ?: "default",
            gradientStyle   = prefs[SettingsKeys.GRADIENT_STYLE]   ?: "normal",
            blurStyle       = prefs[SettingsKeys.BLUR_STYLE]       ?: "normal",
            blurFixedColor  = prefs[SettingsKeys.BLUR_FIXED_COLOR] ?: "none",
            vibrationEnabled = prefs[SettingsKeys.VIBRATION_ENABLED] ?: true,
            vibrationStrength = prefs[SettingsKeys.VIBRATION_STRENGTH] ?: 30,

            titleFontSize = prefs[SettingsKeys.TITLE_FONT_SIZE] ?: 22,
            titleFontWeight = prefs[SettingsKeys.TITLE_FONT_WEIGHT] ?: 700,
            artistFontSize = prefs[SettingsKeys.ARTIST_FONT_SIZE] ?: 14,
            artistFontWeight = prefs[SettingsKeys.ARTIST_FONT_WEIGHT] ?: 400,
            albumFontSize = prefs[SettingsKeys.ALBUM_FONT_SIZE] ?: 12,
            albumFontWeight = prefs[SettingsKeys.ALBUM_FONT_WEIGHT] ?: 400,

            waveSeekBar = prefs[SettingsKeys.WAVE_SEEK_BAR] ?: false,
            waveSeekBarStyle = prefs[SettingsKeys.WAVE_SEEK_BAR_STYLE] ?: 1,
            seekBarThickness = prefs[SettingsKeys.SEEK_BAR_THICKNESS] ?: 4,
            seekBarUpdateInterval = prefs[SettingsKeys.SEEK_BAR_UPDATE_INTERVAL] ?: 0.5f,
            showWaveform = prefs[SettingsKeys.SHOW_WAVEFORM] ?: true,
            showVolumeBar = prefs[SettingsKeys.SHOW_VOLUME_BAR] ?: true,

            queueShowMiniPlayer = prefs[SettingsKeys.QUEUE_SHOW_MINI_PLAYER] ?: true,
            queueSwipeFromHome = prefs[SettingsKeys.QUEUE_SWIPE_FROM_HOME] ?: false,

            appSelectShowMiniPlayer = prefs[SettingsKeys.APP_SELECT_SHOW_MINI_PLAYER] ?: true,
            appSelectShowIcon = prefs[SettingsKeys.APP_SELECT_SHOW_ICON] ?: true,

            themeMode = prefs[SettingsKeys.THEME_MODE] ?: "system",

            lyricsBg = prefs[SettingsKeys.LYRICS_BG] ?: "normal",
            lyricsFontSize = prefs[SettingsKeys.LYRICS_FONT_SIZE] ?: 34,
            lyricsInactiveLight = prefs[SettingsKeys.LYRICS_INACTIVE_LIGHT] ?: true,
            lyricsInterludeThreshold = prefs[SettingsKeys.LYRICS_INTERLUDE_THRESHOLD] ?: 15,
            lyricsMultiLineThreshold = prefs[SettingsKeys.LYRICS_MULTI_LINE_THRESHOLD] ?: 2.5f,
            lyricsFontWeight = prefs[SettingsKeys.LYRICS_FONT_WEIGHT] ?: 700,
            lyricsLineHeight = prefs[SettingsKeys.LYRICS_LINE_HEIGHT] ?: 1.4f,
            lyricsLineSpacing = prefs[SettingsKeys.LYRICS_LINE_SPACING] ?: 8f,
            lyricsAutoRetry = prefs[SettingsKeys.LYRICS_AUTO_RETRY] ?: true,
            lyricsUseNetwork = prefs[SettingsKeys.LYRICS_USE_NETWORK] ?: true,
            lyricsScrollSpeedMs = prefs[SettingsKeys.LYRICS_SCROLL_SPEED_MS] ?: 500,
            lyricsScrollEasing = prefs[SettingsKeys.LYRICS_SCROLL_EASING] ?: "decelerate",
            lyricsOffsetSync = prefs[SettingsKeys.LYRICS_OFFSET_SYNC] ?: "stagger",
            lyricsOffsetDelayMs = prefs[SettingsKeys.LYRICS_OFFSET_DELAY_MS] ?: 50,
            lyricsOffsetStaggerMs = prefs[SettingsKeys.LYRICS_OFFSET_STAGGER_MS] ?: 50,
            lyricsInactiveScale = prefs[SettingsKeys.LYRICS_INACTIVE_SCALE] ?: 0.98f,
            lyricsInactiveBlur = prefs[SettingsKeys.LYRICS_INACTIVE_BLUR] ?: false,
            lyricsInactiveBlurRadius = prefs[SettingsKeys.LYRICS_INACTIVE_BLUR_RADIUS] ?: 1.5f,
            lyricsScrollAnimType = prefs[SettingsKeys.LYRICS_SCROLL_ANIM_TYPE] ?: "decelerate_quint",
            lyricsOvershootDistance = prefs[SettingsKeys.LYRICS_OVERSHOOT_DISTANCE] ?: 10,
            lyricsFontPath = prefs[SettingsKeys.LYRICS_FONT_PATH] ?: "",
            scrollLongTitle = prefs[SettingsKeys.SCROLL_LONG_TITLE] ?: true,
            homeShortcutLeft = prefs[SettingsKeys.HOME_SHORTCUT_LEFT] ?: "lyrics",
            homeShortcutRight = prefs[SettingsKeys.HOME_SHORTCUT_RIGHT] ?: "repeat",
            actionMenuOrder = prefs[SettingsKeys.ACTION_MENU_ORDER]
                ?: "share,search,time_seek,lyrics,favorites_list,favorite_toggle,property,settings",
            actionMenuHidden = prefs[SettingsKeys.ACTION_MENU_HIDDEN] ?: "",
            backgroundHistoryEnabled = prefs[SettingsKeys.BACKGROUND_HISTORY_ENABLED] ?: false,
            lyricsFolder = prefs[SettingsKeys.LYRICS_FOLDER] ?: "",
            lyricsForceInactiveOnEnd = prefs[SettingsKeys.LYRICS_FORCE_INACTIVE_ON_END] ?: true,
            sdlrcDisableMultiShow = prefs[SettingsKeys.SDLRC_DISABLE_MULTI_SHOW] ?: false,
            sdlrcDisableRight = prefs[SettingsKeys.SDLRC_DISABLE_RIGHT] ?: false,
            favoriteViewStyle = prefs[SettingsKeys.FAVORITE_VIEW_STYLE] ?: "list",
            favoriteWarningDismissed = prefs[SettingsKeys.FAVORITE_WARNING_DISMISSED] ?: false,
            animatedWhiteIcon = prefs[SettingsKeys.ANIMATED_WHITE_ICON] ?: true,
            animatedColorSource = prefs[SettingsKeys.ANIMATED_COLOR_SOURCE] ?: "accent",
            animatedBgDarkness = prefs[SettingsKeys.ANIMATED_BG_DARKNESS] ?: 0.88f,
            animatedColorPattern = prefs[SettingsKeys.ANIMATED_COLOR_PATTERN] ?: "normal",
            favoriteShowItem = prefs[SettingsKeys.FAVORITE_SHOW_ITEM] ?: "album",
            favoriteDisplayMode = prefs[SettingsKeys.FAVORITE_DISPLAY_MODE] ?: "list",
            favoriteGridColumns = prefs[SettingsKeys.FAVORITE_GRID_COLUMNS] ?: 2,
            favoriteCardStyle = prefs[SettingsKeys.FAVORITE_CARD_STYLE] ?: "border",
            favoriteColorFill = prefs[SettingsKeys.FAVORITE_COLOR_FILL] ?: false,
            historyCardBackgroundStyle = prefs[SettingsKeys.HISTORY_CARD_BACKGROUND_STYLE] ?: "blur",
            historyTimeDisplayMode = prefs[SettingsKeys.HISTORY_TIME_DISPLAY_MODE] ?: "auto",
            historyShowDate = prefs[SettingsKeys.HISTORY_SHOW_DATE] ?: true,
            historyTimeRangeMode = prefs[SettingsKeys.HISTORY_TIME_RANGE_MODE] ?: "start",
            historyArtBorderEnabled = prefs[SettingsKeys.HISTORY_ART_BORDER_ENABLED] ?: false,
            historyShowPlayTime = prefs[SettingsKeys.HISTORY_SHOW_PLAY_TIME] ?: false,
            historyShowProgressBar = prefs[SettingsKeys.HISTORY_SHOW_PROGRESS_BAR] ?: true,
            historyArtSize = prefs[SettingsKeys.HISTORY_ART_SIZE] ?: "medium",
            homeScreenCSecondaryContent = prefs[SettingsKeys.HOME_SCREEN_C_SECONDARY_CONTENT] ?: "lyrics",
            homeScreenCCardAlpha = prefs[SettingsKeys.HOME_SCREEN_C_CARD_ALPHA] ?: 0.35f,
            lyricsMiniPlayerPosition = prefs[SettingsKeys.LYRICS_MINI_PLAYER_POSITION] ?: "top",
            navBarBackgroundStyle = prefs[SettingsKeys.NAV_BAR_BACKGROUND_STYLE] ?: "accent",
            navBarOpacity = prefs[SettingsKeys.NAV_BAR_OPACITY] ?: 0.97f,
            homeIconOpacity = prefs[SettingsKeys.HOME_ICON_OPACITY] ?: 1.0f,
            navBarSizeLevel = prefs[SettingsKeys.NAV_BAR_SIZE_LEVEL] ?: 3,
            navBarButtonSpacing = prefs[SettingsKeys.NAV_BAR_BUTTON_SPACING] ?: 10,
        )
    }

    suspend fun updateSetting(block: suspend (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    // ===== 設定のエクスポート/インポート =====
    // AppSettingsの全項目を1つのJSON文字列にまとめて書き出し・読み込みする。
    // 実際のフィールド変換ロジックは SettingsBackup（このファイル下部）に集約している。

    /** 現在の設定をJSON文字列として書き出す */
    suspend fun exportSettingsJson(): String {
        val current = settingsFlow.first()
        return SettingsBackup.toJson(current)
    }

    /**
     * JSON文字列から設定を読み込み、DataStoreへ反映する。
     * JSONに含まれていない項目（古いバージョンで書き出したファイル等）は
     * 今の設定値のまま維持され、上書きされない。
     */
    suspend fun importSettingsJson(json: String) {
        val current = settingsFlow.first()
        val merged = SettingsBackup.fromJson(json, current)
        updateSetting { prefs -> SettingsBackup.writeToPreferences(merged, prefs) }
    }
}

// =============================================================================
// 設定のエクスポート/インポート（バックアップ）
// -----------------------------------------------------------------------------
// AppSettingsの全フィールドをJSON文字列へ変換する/JSON文字列から復元するための変換表。
//
// ★ 設計メモ:
//   kotlin-reflect（リフレクション）は使わず、1項目ずつ明示的に変換している。
//   理由は2つ:
//     1) リフレクション用ライブラリへの依存を増やしたくない
//        （このプロジェクトはリフレクションを一切使わない設計のため）
//     2) AppSettingsに項目を追加/削除したときに、ここを更新し忘れると
//        「書き出したはずなのに読み込むと消える」バグになりやすい。明示的な
//        コードにしておけば、少なくともコード上どこを直せばよいかは一目瞭然になる。
//   JSON側のキー名はDataStoreの内部キー(SettingsKeys、スネークケース)ではなく
//   AppSettingsのフィールド名（キャメルケース）をそのまま使う。書き出したJSONを
//   人間が開いたときに、DataStoreの内部実装を知らなくても意味が分かるようにするため。
// =============================================================================
object SettingsBackup {

    private fun JSONObject.putFloat(key: String, value: Float) { put(key, value.toDouble()) }
    private fun JSONObject.floatOrDefault(key: String, default: Float): Float =
        if (has(key)) optDouble(key, default.toDouble()).toFloat() else default
    private fun JSONObject.intOrDefault(key: String, default: Int): Int =
        if (has(key)) optInt(key, default) else default
    private fun JSONObject.boolOrDefault(key: String, default: Boolean): Boolean =
        if (has(key)) optBoolean(key, default) else default
    private fun JSONObject.stringOrDefault(key: String, default: String): String =
        if (has(key)) optString(key, default) else default

    // バックアップ形式のバージョン。将来フォーマットを大きく変える場合に使う（現状は未使用）
    private const val FORMAT_VERSION = 1

    /** 設定をJSON文字列に変換する（エクスポート用） */
    fun toJson(s: AppSettings): String {
        val o = JSONObject()
        o.put("__formatVersion", FORMAT_VERSION)

        // 外観
        o.put("playerUiStyle", s.playerUiStyle)
        o.put("extractColorFromArt", s.extractColorFromArt)
        o.put("colorExtractPattern", s.colorExtractPattern)
        o.putFloat("colorSaturation", s.colorSaturation)
        o.putFloat("colorBrightness", s.colorBrightness)
        o.put("backgroundStyle", s.backgroundStyle)
        o.put("gradientStyle", s.gradientStyle)
        o.put("blurStyle", s.blurStyle)
        o.put("blurFixedColor", s.blurFixedColor)
        o.put("vibrationEnabled", s.vibrationEnabled)
        o.put("vibrationStrength", s.vibrationStrength)

        // テキスト
        o.put("titleFontSize", s.titleFontSize)
        o.put("titleFontWeight", s.titleFontWeight)
        o.put("artistFontSize", s.artistFontSize)
        o.put("artistFontWeight", s.artistFontWeight)
        o.put("albumFontSize", s.albumFontSize)
        o.put("albumFontWeight", s.albumFontWeight)

        // 再生画面
        o.put("waveSeekBar", s.waveSeekBar)
        o.put("waveSeekBarStyle", s.waveSeekBarStyle)
        o.put("seekBarThickness", s.seekBarThickness)
        o.putFloat("seekBarUpdateInterval", s.seekBarUpdateInterval)
        o.put("showWaveform", s.showWaveform)
        o.put("showVolumeBar", s.showVolumeBar)

        // キュー画面
        o.put("queueShowMiniPlayer", s.queueShowMiniPlayer)
        o.put("queueSwipeFromHome", s.queueSwipeFromHome)

        // アプリ切り替え画面
        o.put("appSelectShowMiniPlayer", s.appSelectShowMiniPlayer)
        o.put("appSelectShowIcon", s.appSelectShowIcon)

        // テーマ
        o.put("themeMode", s.themeMode)

        // 歌詞設定
        o.put("lyricsBg", s.lyricsBg)
        o.put("lyricsFontSize", s.lyricsFontSize)
        o.put("lyricsInactiveLight", s.lyricsInactiveLight)
        o.put("lyricsInterludeThreshold", s.lyricsInterludeThreshold)
        o.putFloat("lyricsMultiLineThreshold", s.lyricsMultiLineThreshold)
        o.put("lyricsFontWeight", s.lyricsFontWeight)
        o.putFloat("lyricsLineHeight", s.lyricsLineHeight)
        o.putFloat("lyricsLineSpacing", s.lyricsLineSpacing)
        o.put("lyricsAutoRetry", s.lyricsAutoRetry)
        o.put("lyricsUseNetwork", s.lyricsUseNetwork)
        o.put("lyricsScrollSpeedMs", s.lyricsScrollSpeedMs)
        o.put("lyricsScrollEasing", s.lyricsScrollEasing)
        o.put("lyricsOffsetSync", s.lyricsOffsetSync)
        o.put("lyricsOffsetDelayMs", s.lyricsOffsetDelayMs)
        o.put("lyricsOffsetStaggerMs", s.lyricsOffsetStaggerMs)

        // 歌詞 追加設定
        o.putFloat("lyricsInactiveScale", s.lyricsInactiveScale)
        o.put("lyricsInactiveBlur", s.lyricsInactiveBlur)
        o.putFloat("lyricsInactiveBlurRadius", s.lyricsInactiveBlurRadius)
        o.put("lyricsScrollAnimType", s.lyricsScrollAnimType)
        o.put("lyricsOvershootDistance", s.lyricsOvershootDistance)
        o.put("lyricsFontPath", s.lyricsFontPath)

        // 再生画面追加
        o.put("scrollLongTitle", s.scrollLongTitle)

        // ホーム画面ショートカット
        o.put("homeShortcutLeft", s.homeShortcutLeft)
        o.put("homeShortcutRight", s.homeShortcutRight)

        // アクションメニュー
        o.put("actionMenuOrder", s.actionMenuOrder)
        o.put("actionMenuHidden", s.actionMenuHidden)

        // バックグラウンド処理
        o.put("backgroundHistoryEnabled", s.backgroundHistoryEnabled)

        // 歌詞ファイル
        o.put("lyricsFolder", s.lyricsFolder)

        // 歌詞動作
        o.put("lyricsForceInactiveOnEnd", s.lyricsForceInactiveOnEnd)
        o.put("sdlrcDisableMultiShow", s.sdlrcDisableMultiShow)
        o.put("sdlrcDisableRight", s.sdlrcDisableRight)

        // お気に入り
        o.put("favoriteViewStyle", s.favoriteViewStyle)
        o.put("favoriteWarningDismissed", s.favoriteWarningDismissed)
        o.put("favoriteShowItem", s.favoriteShowItem)
        o.put("favoriteDisplayMode", s.favoriteDisplayMode)
        o.put("favoriteGridColumns", s.favoriteGridColumns)
        o.put("favoriteCardStyle", s.favoriteCardStyle)
        o.put("favoriteColorFill", s.favoriteColorFill)

        // 動的アニメーション背景
        o.put("animatedWhiteIcon", s.animatedWhiteIcon)
        o.put("animatedColorSource", s.animatedColorSource)
        o.putFloat("animatedBgDarkness", s.animatedBgDarkness)
        o.put("animatedColorPattern", s.animatedColorPattern)

        // 履歴画面
        o.put("historyCardBackgroundStyle", s.historyCardBackgroundStyle)
        o.put("historyTimeDisplayMode", s.historyTimeDisplayMode)
        o.put("historyShowDate", s.historyShowDate)
        o.put("historyTimeRangeMode", s.historyTimeRangeMode)
        o.put("historyArtBorderEnabled", s.historyArtBorderEnabled)
        o.put("historyShowPlayTime", s.historyShowPlayTime)
        o.put("historyShowProgressBar", s.historyShowProgressBar)
        o.put("historyArtSize", s.historyArtSize)

        // ホーム画面C
        o.put("homeScreenCSecondaryContent", s.homeScreenCSecondaryContent)
        o.putFloat("homeScreenCCardAlpha", s.homeScreenCCardAlpha)

        // 歌詞画面ミニプレーヤー位置
        o.put("lyricsMiniPlayerPosition", s.lyricsMiniPlayerPosition)

        // ナビゲーションバー
        o.put("navBarBackgroundStyle", s.navBarBackgroundStyle)
        o.putFloat("navBarOpacity", s.navBarOpacity)
        o.putFloat("homeIconOpacity", s.homeIconOpacity)
        o.put("navBarSizeLevel", s.navBarSizeLevel)
        o.put("navBarButtonSpacing", s.navBarButtonSpacing)

        return o.toString(2)
    }

    /**
     * JSON文字列から設定を復元する。
     * @param base JSONに存在しない項目に使う値（通常は「今の設定」を渡し、
     *             差分だけを上書きする）。JSONのパース自体に失敗した場合はbaseをそのまま返す
     *             （壊れたファイルを読み込ませても設定が消えたり例外で落ちたりしないようにするため）。
     */
    fun fromJson(json: String, base: AppSettings): AppSettings {
        val o = try { JSONObject(json) } catch (e: Exception) { return base }

        return base.copy(
            playerUiStyle = o.stringOrDefault("playerUiStyle", base.playerUiStyle),
            extractColorFromArt = o.boolOrDefault("extractColorFromArt", base.extractColorFromArt),
            colorExtractPattern = o.intOrDefault("colorExtractPattern", base.colorExtractPattern),
            colorSaturation = o.floatOrDefault("colorSaturation", base.colorSaturation),
            colorBrightness = o.floatOrDefault("colorBrightness", base.colorBrightness),
            backgroundStyle = o.stringOrDefault("backgroundStyle", base.backgroundStyle),
            gradientStyle = o.stringOrDefault("gradientStyle", base.gradientStyle),
            blurStyle = o.stringOrDefault("blurStyle", base.blurStyle),
            blurFixedColor = o.stringOrDefault("blurFixedColor", base.blurFixedColor),
            vibrationEnabled = o.boolOrDefault("vibrationEnabled", base.vibrationEnabled),
            vibrationStrength = o.intOrDefault("vibrationStrength", base.vibrationStrength),

            titleFontSize = o.intOrDefault("titleFontSize", base.titleFontSize),
            titleFontWeight = o.intOrDefault("titleFontWeight", base.titleFontWeight),
            artistFontSize = o.intOrDefault("artistFontSize", base.artistFontSize),
            artistFontWeight = o.intOrDefault("artistFontWeight", base.artistFontWeight),
            albumFontSize = o.intOrDefault("albumFontSize", base.albumFontSize),
            albumFontWeight = o.intOrDefault("albumFontWeight", base.albumFontWeight),

            waveSeekBar = o.boolOrDefault("waveSeekBar", base.waveSeekBar),
            waveSeekBarStyle = o.intOrDefault("waveSeekBarStyle", base.waveSeekBarStyle),
            seekBarThickness = o.intOrDefault("seekBarThickness", base.seekBarThickness),
            seekBarUpdateInterval = o.floatOrDefault("seekBarUpdateInterval", base.seekBarUpdateInterval),
            showWaveform = o.boolOrDefault("showWaveform", base.showWaveform),
            showVolumeBar = o.boolOrDefault("showVolumeBar", base.showVolumeBar),

            queueShowMiniPlayer = o.boolOrDefault("queueShowMiniPlayer", base.queueShowMiniPlayer),
            queueSwipeFromHome = o.boolOrDefault("queueSwipeFromHome", base.queueSwipeFromHome),

            appSelectShowMiniPlayer = o.boolOrDefault("appSelectShowMiniPlayer", base.appSelectShowMiniPlayer),
            appSelectShowIcon = o.boolOrDefault("appSelectShowIcon", base.appSelectShowIcon),

            themeMode = o.stringOrDefault("themeMode", base.themeMode),

            lyricsBg = o.stringOrDefault("lyricsBg", base.lyricsBg),
            lyricsFontSize = o.intOrDefault("lyricsFontSize", base.lyricsFontSize),
            lyricsInactiveLight = o.boolOrDefault("lyricsInactiveLight", base.lyricsInactiveLight),
            lyricsInterludeThreshold = o.intOrDefault("lyricsInterludeThreshold", base.lyricsInterludeThreshold),
            lyricsMultiLineThreshold = o.floatOrDefault("lyricsMultiLineThreshold", base.lyricsMultiLineThreshold),
            lyricsFontWeight = o.intOrDefault("lyricsFontWeight", base.lyricsFontWeight),
            lyricsLineHeight = o.floatOrDefault("lyricsLineHeight", base.lyricsLineHeight),
            lyricsLineSpacing = o.floatOrDefault("lyricsLineSpacing", base.lyricsLineSpacing),
            lyricsAutoRetry = o.boolOrDefault("lyricsAutoRetry", base.lyricsAutoRetry),
            lyricsUseNetwork = o.boolOrDefault("lyricsUseNetwork", base.lyricsUseNetwork),
            lyricsScrollSpeedMs = o.intOrDefault("lyricsScrollSpeedMs", base.lyricsScrollSpeedMs),
            lyricsScrollEasing = o.stringOrDefault("lyricsScrollEasing", base.lyricsScrollEasing),
            lyricsOffsetSync = o.stringOrDefault("lyricsOffsetSync", base.lyricsOffsetSync),
            lyricsOffsetDelayMs = o.intOrDefault("lyricsOffsetDelayMs", base.lyricsOffsetDelayMs),
            lyricsOffsetStaggerMs = o.intOrDefault("lyricsOffsetStaggerMs", base.lyricsOffsetStaggerMs),

            lyricsInactiveScale = o.floatOrDefault("lyricsInactiveScale", base.lyricsInactiveScale),
            lyricsInactiveBlur = o.boolOrDefault("lyricsInactiveBlur", base.lyricsInactiveBlur),
            lyricsInactiveBlurRadius = o.floatOrDefault("lyricsInactiveBlurRadius", base.lyricsInactiveBlurRadius),
            lyricsScrollAnimType = o.stringOrDefault("lyricsScrollAnimType", base.lyricsScrollAnimType),
            lyricsOvershootDistance = o.intOrDefault("lyricsOvershootDistance", base.lyricsOvershootDistance),
            lyricsFontPath = o.stringOrDefault("lyricsFontPath", base.lyricsFontPath),

            scrollLongTitle = o.boolOrDefault("scrollLongTitle", base.scrollLongTitle),

            homeShortcutLeft = o.stringOrDefault("homeShortcutLeft", base.homeShortcutLeft),
            homeShortcutRight = o.stringOrDefault("homeShortcutRight", base.homeShortcutRight),

            actionMenuOrder = o.stringOrDefault("actionMenuOrder", base.actionMenuOrder),
            actionMenuHidden = o.stringOrDefault("actionMenuHidden", base.actionMenuHidden),

            backgroundHistoryEnabled = o.boolOrDefault("backgroundHistoryEnabled", base.backgroundHistoryEnabled),

            lyricsFolder = o.stringOrDefault("lyricsFolder", base.lyricsFolder),

            lyricsForceInactiveOnEnd = o.boolOrDefault("lyricsForceInactiveOnEnd", base.lyricsForceInactiveOnEnd),
            sdlrcDisableMultiShow = o.boolOrDefault("sdlrcDisableMultiShow", base.sdlrcDisableMultiShow),
            sdlrcDisableRight = o.boolOrDefault("sdlrcDisableRight", base.sdlrcDisableRight),

            favoriteViewStyle = o.stringOrDefault("favoriteViewStyle", base.favoriteViewStyle),
            favoriteWarningDismissed = o.boolOrDefault("favoriteWarningDismissed", base.favoriteWarningDismissed),
            favoriteShowItem = o.stringOrDefault("favoriteShowItem", base.favoriteShowItem),
            favoriteDisplayMode = o.stringOrDefault("favoriteDisplayMode", base.favoriteDisplayMode),
            favoriteGridColumns = o.intOrDefault("favoriteGridColumns", base.favoriteGridColumns),
            favoriteCardStyle = o.stringOrDefault("favoriteCardStyle", base.favoriteCardStyle),
            favoriteColorFill = o.boolOrDefault("favoriteColorFill", base.favoriteColorFill),

            animatedWhiteIcon = o.boolOrDefault("animatedWhiteIcon", base.animatedWhiteIcon),
            animatedColorSource = o.stringOrDefault("animatedColorSource", base.animatedColorSource),
            animatedBgDarkness = o.floatOrDefault("animatedBgDarkness", base.animatedBgDarkness),
            animatedColorPattern = o.stringOrDefault("animatedColorPattern", base.animatedColorPattern),

            historyCardBackgroundStyle = o.stringOrDefault("historyCardBackgroundStyle", base.historyCardBackgroundStyle),
            historyTimeDisplayMode = o.stringOrDefault("historyTimeDisplayMode", base.historyTimeDisplayMode),
            historyShowDate = o.boolOrDefault("historyShowDate", base.historyShowDate),
            historyTimeRangeMode = o.stringOrDefault("historyTimeRangeMode", base.historyTimeRangeMode),
            historyArtBorderEnabled = o.boolOrDefault("historyArtBorderEnabled", base.historyArtBorderEnabled),
            historyShowPlayTime = o.boolOrDefault("historyShowPlayTime", base.historyShowPlayTime),
            historyShowProgressBar = o.boolOrDefault("historyShowProgressBar", base.historyShowProgressBar),
            historyArtSize = o.stringOrDefault("historyArtSize", base.historyArtSize),

            homeScreenCSecondaryContent = o.stringOrDefault("homeScreenCSecondaryContent", base.homeScreenCSecondaryContent),
            homeScreenCCardAlpha = o.floatOrDefault("homeScreenCCardAlpha", base.homeScreenCCardAlpha),

            lyricsMiniPlayerPosition = o.stringOrDefault("lyricsMiniPlayerPosition", base.lyricsMiniPlayerPosition),

            navBarBackgroundStyle = o.stringOrDefault("navBarBackgroundStyle", base.navBarBackgroundStyle),
            navBarOpacity = o.floatOrDefault("navBarOpacity", base.navBarOpacity),
            homeIconOpacity = o.floatOrDefault("homeIconOpacity", base.homeIconOpacity),
            navBarSizeLevel = o.intOrDefault("navBarSizeLevel", base.navBarSizeLevel),
            navBarButtonSpacing = o.intOrDefault("navBarButtonSpacing", base.navBarButtonSpacing),
        )
    }

    /** AppSettingsの全項目をDataStoreのMutablePreferencesへ書き込む（インポート適用用） */
    fun writeToPreferences(s: AppSettings, prefs: MutablePreferences) {
        prefs[SettingsKeys.PLAYER_UI_STYLE] = s.playerUiStyle
        prefs[SettingsKeys.EXTRACT_COLOR] = s.extractColorFromArt
        prefs[SettingsKeys.COLOR_EXTRACT_PATTERN] = s.colorExtractPattern
        prefs[SettingsKeys.COLOR_SATURATION] = s.colorSaturation
        prefs[SettingsKeys.COLOR_BRIGHTNESS] = s.colorBrightness
        prefs[SettingsKeys.BACKGROUND_STYLE] = s.backgroundStyle
        prefs[SettingsKeys.GRADIENT_STYLE] = s.gradientStyle
        prefs[SettingsKeys.BLUR_STYLE] = s.blurStyle
        prefs[SettingsKeys.BLUR_FIXED_COLOR] = s.blurFixedColor
        prefs[SettingsKeys.VIBRATION_ENABLED] = s.vibrationEnabled
        prefs[SettingsKeys.VIBRATION_STRENGTH] = s.vibrationStrength

        prefs[SettingsKeys.TITLE_FONT_SIZE] = s.titleFontSize
        prefs[SettingsKeys.TITLE_FONT_WEIGHT] = s.titleFontWeight
        prefs[SettingsKeys.ARTIST_FONT_SIZE] = s.artistFontSize
        prefs[SettingsKeys.ARTIST_FONT_WEIGHT] = s.artistFontWeight
        prefs[SettingsKeys.ALBUM_FONT_SIZE] = s.albumFontSize
        prefs[SettingsKeys.ALBUM_FONT_WEIGHT] = s.albumFontWeight

        prefs[SettingsKeys.WAVE_SEEK_BAR] = s.waveSeekBar
        prefs[SettingsKeys.WAVE_SEEK_BAR_STYLE] = s.waveSeekBarStyle
        prefs[SettingsKeys.SEEK_BAR_THICKNESS] = s.seekBarThickness
        prefs[SettingsKeys.SEEK_BAR_UPDATE_INTERVAL] = s.seekBarUpdateInterval
        prefs[SettingsKeys.SHOW_WAVEFORM] = s.showWaveform
        prefs[SettingsKeys.SHOW_VOLUME_BAR] = s.showVolumeBar

        prefs[SettingsKeys.QUEUE_SHOW_MINI_PLAYER] = s.queueShowMiniPlayer
        prefs[SettingsKeys.QUEUE_SWIPE_FROM_HOME] = s.queueSwipeFromHome

        prefs[SettingsKeys.APP_SELECT_SHOW_MINI_PLAYER] = s.appSelectShowMiniPlayer
        prefs[SettingsKeys.APP_SELECT_SHOW_ICON] = s.appSelectShowIcon

        prefs[SettingsKeys.THEME_MODE] = s.themeMode

        prefs[SettingsKeys.LYRICS_BG] = s.lyricsBg
        prefs[SettingsKeys.LYRICS_FONT_SIZE] = s.lyricsFontSize
        prefs[SettingsKeys.LYRICS_INACTIVE_LIGHT] = s.lyricsInactiveLight
        prefs[SettingsKeys.LYRICS_INTERLUDE_THRESHOLD] = s.lyricsInterludeThreshold
        prefs[SettingsKeys.LYRICS_MULTI_LINE_THRESHOLD] = s.lyricsMultiLineThreshold
        prefs[SettingsKeys.LYRICS_FONT_WEIGHT] = s.lyricsFontWeight
        prefs[SettingsKeys.LYRICS_LINE_HEIGHT] = s.lyricsLineHeight
        prefs[SettingsKeys.LYRICS_LINE_SPACING] = s.lyricsLineSpacing
        prefs[SettingsKeys.LYRICS_AUTO_RETRY] = s.lyricsAutoRetry
        prefs[SettingsKeys.LYRICS_USE_NETWORK] = s.lyricsUseNetwork
        prefs[SettingsKeys.LYRICS_SCROLL_SPEED_MS] = s.lyricsScrollSpeedMs
        prefs[SettingsKeys.LYRICS_SCROLL_EASING] = s.lyricsScrollEasing
        prefs[SettingsKeys.LYRICS_OFFSET_SYNC] = s.lyricsOffsetSync
        prefs[SettingsKeys.LYRICS_OFFSET_DELAY_MS] = s.lyricsOffsetDelayMs
        prefs[SettingsKeys.LYRICS_OFFSET_STAGGER_MS] = s.lyricsOffsetStaggerMs
        prefs[SettingsKeys.LYRICS_INACTIVE_SCALE] = s.lyricsInactiveScale
        prefs[SettingsKeys.LYRICS_INACTIVE_BLUR] = s.lyricsInactiveBlur
        prefs[SettingsKeys.LYRICS_INACTIVE_BLUR_RADIUS] = s.lyricsInactiveBlurRadius
        prefs[SettingsKeys.LYRICS_SCROLL_ANIM_TYPE] = s.lyricsScrollAnimType
        prefs[SettingsKeys.LYRICS_OVERSHOOT_DISTANCE] = s.lyricsOvershootDistance
        prefs[SettingsKeys.LYRICS_FONT_PATH] = s.lyricsFontPath
        prefs[SettingsKeys.SCROLL_LONG_TITLE] = s.scrollLongTitle
        prefs[SettingsKeys.HOME_SHORTCUT_LEFT] = s.homeShortcutLeft
        prefs[SettingsKeys.HOME_SHORTCUT_RIGHT] = s.homeShortcutRight
        prefs[SettingsKeys.ACTION_MENU_ORDER] = s.actionMenuOrder
        prefs[SettingsKeys.ACTION_MENU_HIDDEN] = s.actionMenuHidden
        prefs[SettingsKeys.BACKGROUND_HISTORY_ENABLED] = s.backgroundHistoryEnabled
        prefs[SettingsKeys.LYRICS_FOLDER] = s.lyricsFolder
        prefs[SettingsKeys.LYRICS_FORCE_INACTIVE_ON_END] = s.lyricsForceInactiveOnEnd
        prefs[SettingsKeys.SDLRC_DISABLE_MULTI_SHOW] = s.sdlrcDisableMultiShow
        prefs[SettingsKeys.SDLRC_DISABLE_RIGHT] = s.sdlrcDisableRight
        prefs[SettingsKeys.FAVORITE_VIEW_STYLE] = s.favoriteViewStyle
        prefs[SettingsKeys.FAVORITE_WARNING_DISMISSED] = s.favoriteWarningDismissed
        prefs[SettingsKeys.ANIMATED_WHITE_ICON] = s.animatedWhiteIcon
        prefs[SettingsKeys.ANIMATED_COLOR_SOURCE] = s.animatedColorSource
        prefs[SettingsKeys.ANIMATED_BG_DARKNESS] = s.animatedBgDarkness
        prefs[SettingsKeys.ANIMATED_COLOR_PATTERN] = s.animatedColorPattern
        prefs[SettingsKeys.FAVORITE_SHOW_ITEM] = s.favoriteShowItem
        prefs[SettingsKeys.FAVORITE_DISPLAY_MODE] = s.favoriteDisplayMode
        prefs[SettingsKeys.FAVORITE_GRID_COLUMNS] = s.favoriteGridColumns
        prefs[SettingsKeys.FAVORITE_CARD_STYLE] = s.favoriteCardStyle
        prefs[SettingsKeys.FAVORITE_COLOR_FILL] = s.favoriteColorFill
        prefs[SettingsKeys.HISTORY_CARD_BACKGROUND_STYLE] = s.historyCardBackgroundStyle
        prefs[SettingsKeys.HISTORY_TIME_DISPLAY_MODE] = s.historyTimeDisplayMode
        prefs[SettingsKeys.HISTORY_SHOW_DATE] = s.historyShowDate
        prefs[SettingsKeys.HISTORY_TIME_RANGE_MODE] = s.historyTimeRangeMode
        prefs[SettingsKeys.HISTORY_ART_BORDER_ENABLED] = s.historyArtBorderEnabled
        prefs[SettingsKeys.HISTORY_SHOW_PLAY_TIME] = s.historyShowPlayTime
        prefs[SettingsKeys.HISTORY_SHOW_PROGRESS_BAR] = s.historyShowProgressBar
        prefs[SettingsKeys.HISTORY_ART_SIZE] = s.historyArtSize
        prefs[SettingsKeys.HOME_SCREEN_C_SECONDARY_CONTENT] = s.homeScreenCSecondaryContent
        prefs[SettingsKeys.HOME_SCREEN_C_CARD_ALPHA] = s.homeScreenCCardAlpha
        prefs[SettingsKeys.LYRICS_MINI_PLAYER_POSITION] = s.lyricsMiniPlayerPosition
        prefs[SettingsKeys.NAV_BAR_BACKGROUND_STYLE] = s.navBarBackgroundStyle
        prefs[SettingsKeys.NAV_BAR_OPACITY] = s.navBarOpacity
        prefs[SettingsKeys.HOME_ICON_OPACITY] = s.homeIconOpacity
        prefs[SettingsKeys.NAV_BAR_SIZE_LEVEL] = s.navBarSizeLevel
        prefs[SettingsKeys.NAV_BAR_BUTTON_SPACING] = s.navBarButtonSpacing
    }
}

// ===== メディア状態データクラス =====
data class MediaState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtUri: String? = null,
    val albumArtBitmap: android.graphics.Bitmap? = null,  // URIがないアプリ向けフォールバック
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 0.5f,
    val dominantColor: Color = Color(0xFF6200EE),
    // 動的アニメーション背景専用: 複数Swatchをpopulation加重で混色した色
    // （dominantColorは従来通り単一Swatchのアクセントカラーとして維持）
    val blendedArtColor: Color = Color(0xFF6200EE),
    val packageName: String = "",
    val appLabel: String = "",
    val isSpatialAudio: Boolean = false,
    // ===== プロパティ画面用追加フィールド =====
    val displaySubtitle: String = "",       // METADATA_KEY_DISPLAY_SUBTITLE
    val mediaId: String = "",               // METADATA_KEY_MEDIA_ID
    val displayDescription: String = "",    // METADATA_KEY_DISPLAY_DESCRIPTION
    val artUriType: String = "",            // "art" / "album_art" / "none"
    val queueTitle: String = "",            // MediaController.getQueueTitle()
    val queueAvailable: Boolean = false,    // キューが提供されているか
    val activeQueueItemId: Long = -1L,      // 現在再生中のキューアイテムID

) {
    val currentPositionStr: String get() {
        val seconds = (currentPositionMs / 1000) % 60
        val minutes = (currentPositionMs / 1000) / 60
        return "%d:%02d".format(minutes, seconds)
    }

    val remainingTimeStr: String get() {
        val remaining = durationMs - currentPositionMs
        val seconds = (remaining / 1000) % 60
        val minutes = (remaining / 1000) / 60
        return "%d:%02d".format(minutes, seconds)
    }
}

// ===== キューアイテム =====
data class QueueItem(
    val id: Long,
    val title: String,
    val artist: String,
    val albumArtUri: String? = null
)