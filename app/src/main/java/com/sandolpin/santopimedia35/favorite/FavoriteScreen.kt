package com.sandolpin.santopimedia35.favorite

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.sandolpin.santopimedia35.MediaPlayerViewModel
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.database.SettingsKeys
import com.sandolpin.santopimedia35.toBitmap

/**
 * お気に入り画面
 * - TOP: アルバムごとにグループ化したカード一覧
 * - DETAIL: アルバム内の曲一覧（タップで即再生）
 *
 * 右上の設定アイコンから表示オプションダイアログを開ける：
 *   - 表示する項目（アーティスト / アルバム / 曲全体）
 *   - 表示方法（リスト / グリッド）+ グリッド列数（1〜5）
 *   - 表示スタイル（縁取り / カード / 影）
 *   - アルバムアートに合わせた色塗りつぶし（トグル）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FavoriteScreen(
    viewModel: FavoriteViewModel,
    mediaPlayerViewModel: MediaPlayerViewModel,
    onDismiss: () -> Unit
) {
    val settings by mediaPlayerViewModel.settings.collectAsState()
    var selectedGroup by remember { mutableStateOf<FavoriteAlbumGroup?>(null) }

    // ★ アルバム一覧（FavoriteTopScreen）のスクロール位置。
    //   AnimatedContent は selectedGroup が null→アルバム→null と変化するたびに
    //   null側のコンポーザブル（FavoriteTopScreen）自体を一度破棄し、戻ってきたときに
    //   新しく作り直す。LazyColumn の状態をFavoriteTopScreen内部でrememberしていると
    //   このタイミングで一緒に破棄され、戻ってくるたびスクロール位置が0に
    //   リセットされてしまっていた（実際に発生した不具合）。
    //   AnimatedContentの外側（selectedGroupの変化に関わらず生き続けるこの階層）で
    //   LazyListStateを保持し、FavoriteTopScreenには毎回同じインスタンスを渡すことで、
    //   アルバムを開いて閉じてもスクロール位置が維持されるようにする。
    val topScreenListState = androidx.compose.foundation.lazy.rememberLazyListState()

    BackHandler(enabled = selectedGroup != null) { selectedGroup = null }

    // ★ AnimatedContentの fadeIn/fadeOut は「消えていく画面」と「現れてくる画面」を
    //   クロスフェードするが、切り替えの途中は両方とも透明度が下がった状態で重なる
    //   （例: 中間地点で双方50%透明→合成しても画面全体を覆いきれない）。
    //   このFavoriteScreen自体はMainActivity.kt側でQueueScreen等の上にオーバーレイ
    //   表示されているため、クロスフェードで一瞬透けた隙間から背後の画面
    //   （⭐ボタンを押す直前に見ていたキュー画面など）が見えてしまっていた
    //   （実際に発生した不具合）。
    //   AnimatedContentの外側に常時不透明な背景を敷いたBoxを1枚挟むことで、
    //   クロスフェード中も画面全体が途切れず覆われるようにする。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SharedTransitionLayout {
            val sharedScope = this@SharedTransitionLayout
            AnimatedContent(
                targetState = selectedGroup,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(150))
                },
                label = "favoriteNav"
            ) { group ->
                if (group == null) {
                    FavoriteTopScreen(
                        viewModel = viewModel,
                        mediaPlayerViewModel = mediaPlayerViewModel,
                        settings = settings,
                        listState = topScreenListState,
                        onSelectGroup = { selectedGroup = it },
                        onDismiss = onDismiss,
                        sharedTransitionScope = sharedScope,
                        animatedContentScope = this
                    )
                } else {
                    FavoriteAlbumDetailScreen(
                        group = group,
                        mediaPlayerViewModel = mediaPlayerViewModel,
                        onDismiss = { selectedGroup = null },
                        sharedTransitionScope = sharedScope,
                        animatedContentScope = this
                    )
                }
            }
        }
    }
}

// ===== TOP: アルバムカード一覧 =====
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun FavoriteTopScreen(
    viewModel: FavoriteViewModel,
    mediaPlayerViewModel: MediaPlayerViewModel,
    settings: AppSettings,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelectGroup: (FavoriteAlbumGroup) -> Unit,
    onDismiss: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {
    val favorites by viewModel.favorites.collectAsState()
    val groups = remember(favorites) { viewModel.groupedByAlbum() }
    val groupsByApp = remember(groups) { groups.groupBy { it.packageName to it.appLabel } }
    var showDisplaySettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("お気に入り", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.ArrowBack, "戻る")
                    }
                },
                actions = {
                    // 設定アイコン
                    IconButton(onClick = { showDisplaySettings = true }) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = "表示設定",
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== 警告バナー =====
            if (!settings.favoriteWarningDismissed) {
                item {
                    FavoriteWarningBanner(onDismiss = {
                        mediaPlayerViewModel.updateSetting(SettingsKeys.FAVORITE_WARNING_DISMISSED, true)
                    })
                }
            }

            if (groups.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.StarBorder, null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                            Spacer(Modifier.height(8.dp))
                            Text("まだお気に入りがありません",
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                        }
                    }
                }
            } else {
                groupsByApp.forEach { (appKey, appGroups) ->
                    val (packageName, appLabel) = appKey
                    item(key = "app_$packageName") {
                        AppSectionHeader(appLabel = appLabel, packageName = packageName)
                    }
                    item(key = "grid_$packageName") {
                        if (settings.favoriteDisplayMode == "grid") {
                            FavoriteAlbumGrid(
                                groups = appGroups,
                                columns = settings.favoriteGridColumns,
                                cardStyle = settings.favoriteCardStyle,
                                colorFill = settings.favoriteColorFill,
                                onSelectGroup = onSelectGroup,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedContentScope = animatedContentScope
                            )
                        } else {
                            FavoriteAlbumList(
                                groups = appGroups,
                                cardStyle = settings.favoriteCardStyle,
                                colorFill = settings.favoriteColorFill,
                                onSelectGroup = onSelectGroup,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedContentScope = animatedContentScope
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    // 表示設定ダイアログ
    if (showDisplaySettings) {
        FavoriteDisplaySettingsDialog(
            settings = settings,
            mediaPlayerViewModel = mediaPlayerViewModel,
            onDismiss = { showDisplaySettings = false }
        )
    }
}

// ===== 表示設定ダイアログ =====
@Composable
private fun FavoriteDisplaySettingsDialog(
    settings: AppSettings,
    mediaPlayerViewModel: MediaPlayerViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // ヘッダー
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "表示設定",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.Close, "閉じる",
                            modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
                Spacer(Modifier.height(16.dp))

                // ===== 表示する項目 =====
                SettingGroupLabel("表示する項目")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "artist" to "アーティスト",
                        "album"  to "アルバム",
                        "all"    to "曲全体"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = settings.favoriteShowItem == value,
                            onClick = { mediaPlayerViewModel.updateSetting(SettingsKeys.FAVORITE_SHOW_ITEM, value) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                Spacer(Modifier.height(16.dp))

                // ===== 表示方法 =====
                SettingGroupLabel("表示方法")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("list" to "リスト", "grid" to "グリッド").forEach { (value, label) ->
                        FilterChip(
                            selected = settings.favoriteDisplayMode == value,
                            onClick = { mediaPlayerViewModel.updateSetting(SettingsKeys.FAVORITE_DISPLAY_MODE, value) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                // グリッド選択時のみ列数スライダー
                if (settings.favoriteDisplayMode == "grid") {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "列数: ${settings.favoriteGridColumns}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                            modifier = Modifier.width(60.dp)
                        )
                        Slider(
                            value = settings.favoriteGridColumns.toFloat(),
                            onValueChange = {
                                mediaPlayerViewModel.updateSetting(SettingsKeys.FAVORITE_GRID_COLUMNS, it.toInt())
                            },
                            valueRange = 1f..5f,
                            steps = 3,
                            modifier = Modifier.weight(1f)
                        )
                        // 列数ボタン（タップで即変更）
                        Row(
                            modifier = Modifier.padding(start = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            (1..5).forEach { n ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (settings.favoriteGridColumns == n)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            mediaPlayerViewModel.updateSetting(SettingsKeys.FAVORITE_GRID_COLUMNS, n)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$n",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (settings.favoriteGridColumns == n)
                                            Color.White
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                Spacer(Modifier.height(16.dp))

                // ===== 表示スタイル =====
                SettingGroupLabel("表示スタイル")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "border" to "縁取り",
                        "card"   to "カード",
                        "shadow" to "影"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = settings.favoriteCardStyle == value,
                            onClick = { mediaPlayerViewModel.updateSetting(SettingsKeys.FAVORITE_CARD_STYLE, value) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                Spacer(Modifier.height(12.dp))

                // ===== アート色塗りつぶし =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "アートに合わせた色で塗りつぶし",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "アルバムアートの色調をカードに反映します",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    }
                    Switch(
                        checked = settings.favoriteColorFill,
                        onCheckedChange = {
                            mediaPlayerViewModel.updateSetting(SettingsKeys.FAVORITE_COLOR_FILL, it)
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SettingGroupLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

// ===== 警告バナー =====
@Composable
private fun FavoriteWarningBanner(onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFA9A0).copy(alpha = 0.85f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "一部のアプリではこの機能は使えません。",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4A0E00)
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "以後表示しない",
                    fontSize = 11.sp,
                    color = Color(0xFF4A0E00).copy(alpha = 0.75f),
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
        }
    }
}

// ===== アプリごとのセクションヘッダー =====
@Composable
private fun AppSectionHeader(appLabel: String, packageName: String) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        try { context.packageManager.getApplicationIcon(packageName) } catch (e: Exception) { null }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                androidx.compose.foundation.Image(
                    bitmap = icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(appLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
    }
}

// ===== カードスタイルに応じた Modifier を返すユーティリティ =====
// colorFill=true のとき、albumArtUri から dominantColor を計算しようとすると
// 非同期処理が必要で複雑になるため、ここでは albumArtUri の有無をヒントにして
// surfaceVariant の薄い色を背景色として使う（シンプルかつ安全な実装）
@Composable
private fun cardModifier(
    baseModifier: Modifier,
    cardStyle: String,
    colorFill: Boolean,
    fillColor: Color
): Modifier {
    val shape = RoundedCornerShape(14.dp)
    return when (cardStyle) {
        "card" -> baseModifier
            .shadow(2.dp, shape)
            .clip(shape)
            .background(
                if (colorFill) fillColor
                else MaterialTheme.colorScheme.surfaceVariant.copy(0.45f)
            )
        "shadow" -> baseModifier
            .shadow(8.dp, shape, spotColor = MaterialTheme.colorScheme.primary.copy(0.3f))
            .clip(shape)
            .background(
                if (colorFill) fillColor
                else MaterialTheme.colorScheme.surface
            )
        else -> { // "border"
            if (colorFill) {
                baseModifier
                    .clip(shape)
                    .background(fillColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.25f), shape)
            } else {
                baseModifier
                    .clip(shape)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f), shape)
            }
        }
    }
}

// アルバムアートURIから塗りつぶし色を決める（簡易版）
// ダークテーマなら薄い暖色、ライトなら淡い暖色。アートがあれば少し強調する
@Composable
private fun artFillColor(albumArtUri: String?): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (albumArtUri != null) {
        if (isDark) Color(0xFF2A2020) else Color(0xFFF5EFEF)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
    }
}

// ===== アルバムカード（リスト形式）=====
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun FavoriteAlbumList(
    groups: List<FavoriteAlbumGroup>,
    cardStyle: String,
    colorFill: Boolean,
    onSelectGroup: (FavoriteAlbumGroup) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { group ->
            val fillColor = artFillColor(group.albumArtUri)
            val baseModifier = Modifier.fillMaxWidth()
            val styledModifier = cardModifier(baseModifier, cardStyle, colorFill, fillColor)
            Box(
                modifier = styledModifier.clickable { onSelectGroup(group) }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArtThumb(
                        uri = group.albumArtUri, size = 48.dp,
                        sharedKey = "albumArt_${group.groupKey}",
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            group.albumLabel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text("${group.tracks.size}曲", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                    }
                }
            }
        }
    }
}

// ===== アルバムカード（グリッド形式）=====
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun FavoriteAlbumGrid(
    groups: List<FavoriteAlbumGroup>,
    columns: Int,
    cardStyle: String,
    colorFill: Boolean,
    onSelectGroup: (FavoriteAlbumGroup) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {
    val rows = groups.chunked(columns)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { rowGroups ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowGroups.forEach { group ->
                    val fillColor = artFillColor(group.albumArtUri)
                    val baseModifier = Modifier.weight(1f)
                    val styledModifier = cardModifier(baseModifier, cardStyle, colorFill, fillColor)
                    Box(
                        modifier = styledModifier.clickable { onSelectGroup(group) }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            AlbumArtThumb(
                                uri = group.albumArtUri, size = 0.dp, fillMax = true,
                                sharedKey = "albumArt_${group.groupKey}",
                                sharedTransitionScope = sharedTransitionScope,
                                animatedContentScope = animatedContentScope
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                group.albumLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text("${group.tracks.size}曲", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                        }
                    }
                }
                // 奇数個の場合の空白埋め
                val remainder = rowGroups.size % columns
                if (remainder != 0) {
                    repeat(columns - remainder) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ===== アルバムアート（Shared Element対応版）=====
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AlbumArtThumb(
    uri: String?,
    size: androidx.compose.ui.unit.Dp,
    fillMax: Boolean = false,
    sharedKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    border: BorderStrokeOrNull = BorderStrokeOrNull.None
) {
    val shape = RoundedCornerShape(if (fillMax) 14.dp else 10.dp)
    var modifier = if (fillMax)
        Modifier.fillMaxWidth().aspectRatio(1f)
    else
        Modifier.size(size)

    if (sharedKey != null && sharedTransitionScope != null && animatedContentScope != null) {
        with(sharedTransitionScope) {
            modifier = modifier.sharedElement(
                rememberSharedContentState(key = sharedKey),
                animatedVisibilityScope = animatedContentScope
            )
        }
    }

    modifier = when (border) {
        is BorderStrokeOrNull.Some ->
            modifier.clip(shape).border(border.width, border.color, shape)
        BorderStrokeOrNull.None ->
            modifier.clip(shape)
    }

    if (uri != null) {
        AsyncImage(
            model = uri, contentDescription = null,
            modifier = modifier, contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Album, null,
                tint = MaterialTheme.colorScheme.onSurface.copy(0.35f))
        }
    }
}

// 縁取り指定用の簡易sealed class
sealed class BorderStrokeOrNull {
    object None : BorderStrokeOrNull()
    data class Some(val width: androidx.compose.ui.unit.Dp, val color: Color) : BorderStrokeOrNull()
}

// ===== アルバム詳細画面（曲一覧）=====
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun FavoriteAlbumDetailScreen(
    group: FavoriteAlbumGroup,
    mediaPlayerViewModel: MediaPlayerViewModel,
    onDismiss: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {
    var requestMessage by remember { mutableStateOf<String?>(null) }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                FavoriteAlbumHeader(
                    group = group,
                    onDismiss = onDismiss,
                    topPadding = statusBarPadding,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope
                )
            }

            item { Spacer(Modifier.height(16.dp)) }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outline.copy(0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column {
                        group.tracks.forEachIndexed { index, track ->
                            FavoriteTrackRow(
                                track = track,
                                onClick = {
                                    val result = mediaPlayerViewModel.playSmartForTrack(
                                        packageName = track.packageName,
                                        mediaId     = track.mediaId,
                                        title       = track.title,
                                        artist      = track.artist
                                    )
                                    requestMessage = when (result) {
                                        MediaPlayerViewModel.PlayRequestResult.SUCCESS    -> null
                                        MediaPlayerViewModel.PlayRequestResult.NO_SESSION ->
                                            if (track.mediaId.isEmpty())
                                                "メディアIDが記録されていないため再生できません"
                                            else
                                                "アプリが起動していないため再生できません"
                                        else -> "再生できませんでした"
                                    }
                                }
                            )
                            if (index < group.tracks.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(0.2f)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    requestMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { requestMessage = null },
            confirmButton = {
                TextButton(onClick = { requestMessage = null }) { Text("OK") }
            },
            text = { Text(msg) }
        )
    }
}

// ===== アルバム詳細ヘッダー =====
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun FavoriteAlbumHeader(
    group: FavoriteAlbumGroup,
    onDismiss: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {
    val hasArt = group.albumArtUri != null

    Box(modifier = Modifier.fillMaxWidth()) {
        if (hasArt) {
            AsyncImage(
                model = group.albumArtUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .blur(40.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(0.3f),
                        RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(topPadding))
            IconButton(onClick = onDismiss, modifier = Modifier.padding(start = 4.dp)) {
                Icon(
                    Icons.Rounded.ArrowBack, "戻る",
                    tint = if (hasArt) Color.White else MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArtThumb(
                    uri = group.albumArtUri,
                    size = 140.dp,
                    sharedKey = "albumArt_${group.groupKey}",
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    border = BorderStrokeOrNull.Some(4.dp, Color.White)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        group.albumLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = if (hasArt) Color.White else MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        group.tracks.firstOrNull()?.artist.orEmpty().ifEmpty { "アーティスト不明" },
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = (if (hasArt) Color.White else MaterialTheme.colorScheme.onBackground).copy(alpha = 0.75f)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${group.tracks.size}曲",
                        fontSize = 13.sp,
                        color = (if (hasArt) Color.White else MaterialTheme.colorScheme.onBackground).copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}



// ===== 曲行（タップで即再生）=====
@Composable
private fun FavoriteTrackRow(track: FavoriteTrackEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtThumb(uri = track.albumArtUri, size = 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title.ifEmpty { "タイトル不明" }, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist.ifEmpty { "アーティスト不明" }, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (track.durationMs > 0) {
            val totalSec = track.durationMs / 1000
            Text(
                "%d:%02d".format(totalSec / 60, totalSec % 60),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
        }
    }
}