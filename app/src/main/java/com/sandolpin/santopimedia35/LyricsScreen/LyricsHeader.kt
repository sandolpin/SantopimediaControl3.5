package com.sandolpin.santopimedia35.lyricsscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sandolpin.santopimedia35.*
import com.sandolpin.santopimedia35.database.MediaState
import com.sandolpin.santopimedia35.home.homescreena.NormalSeekBar

// 歌詞画面 縦画面用ミニプレーヤー（折りたたみ式ヘッダー）

@Composable
fun LyricsHeader(
    mediaState: MediaState,
    accentColor: Color,
    isBlurBg: Boolean = false,
    viewModel: MediaPlayerViewModel? = null,
    useOffsetAnim: Boolean = true,
    onToggleOffsetAnim: () -> Unit = {},
    onLoadFile: () -> Unit = {},
    onUnlinkFile: () -> Unit = {},
    onSearchApi: () -> Unit = {},
    onOpenLyricsSettings: () -> Unit = {},
    // ★ 縦画面のミニプレーヤーを画面の上部/下部どちらに表示するか。
    //   "top"=上部（デフォルト、従来通り） / "bottom"=下部
    miniPlayerPosition: String = "top",
    onChangePosition: (String) -> Unit = {}
) {
    val textColor    = if (isBlurBg) Color.White else MaterialTheme.colorScheme.onSurface
    val subColor     = textColor.copy(alpha = 0.6f)
    val surfaceColor = if (isBlurBg) Color.White.copy(0.12f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(0.85f)

    // 折りたたみ状態（初期は展開）
    var expanded by remember { mutableStateOf(true) }
    // 三点メニューの開閉状態
    var showMenu by remember { mutableStateOf(false) }

    // ★ 戻るボタンはこの関数の外（LyricsScreen側）で常に画面最上部に固定表示するため、
    //   ここでは持たない（以前はここにあったが、ミニプレーヤーの位置(上/下)設定に
    //   連動して戻るボタンごと下に移動してしまっていたのを解消するため分離した）。
    //   そのため、ここでのColumnの上端余白はステータスバー避けを目的とせず、
    //   単純な小さな余白のみにする（"top"配置時は戻るボタン行の直下、
    //   "bottom"配置時は歌詞エリアの直下に来るため、どちらもステータスバー分の
    //   余白は不要）。下端は"bottom"配置時のみナビゲーションバー分を確保する。
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .padding(
            top = 4.dp,
            bottom = if (miniPlayerPosition == "bottom")
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 4.dp
            else 4.dp
        )
    ) {        // ミニプレーヤーカード（タップで折りたたみ）
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = surfaceColor,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {

                // アルバムアート・タイトル・アーティスト＋☆ボタン（常時表示）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mediaState.albumArtUri != null) {
                        AsyncImage(
                            model = mediaState.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.MusicNote, null,
                                tint = subColor, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            mediaState.title.ifEmpty { "タイトル不明" },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1, fontSize = 14.sp, color = textColor,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            mediaState.artist.ifEmpty { "アーティスト不明" },
                            fontSize = 11.sp, color = subColor, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    // ☆/★ ボタン: offsetアニメモード切り替え
                    // ★=offsetアニメON（デフォルト）、☆=安定モード（offsetなし）
                    IconButton(
                        onClick = { onToggleOffsetAnim() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (useOffsetAnim)
                                Icons.Rounded.Star
                            else
                                Icons.Rounded.StarBorder,
                            contentDescription = if (useOffsetAnim) "安定モードに切り替え" else "アニメモードに切り替え",
                            tint = if (useOffsetAnim) accentColor else textColor.copy(0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // ===== 三点メニュー =====
                    // ・ファイルを読み込む
                    // ・歌詞ファイルの紐づけを解除
                    // ・歌詞をAPI検索する
                    // ・歌詞設定を開く
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Rounded.MoreVert,
                                contentDescription = "その他の操作",
                                tint = textColor.copy(0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("ファイルを読み込む") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.FileOpen,
                                        null, modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onLoadFile()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("歌詞ファイルの紐づけを解除") },
                                leadingIcon = {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Rounded.LinkOff,
                                        null, modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onUnlinkFile()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("歌詞をAPI検索する") },
                                leadingIcon = {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Rounded.Search,
                                        null, modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onSearchApi()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("歌詞設定を開く") },
                                leadingIcon = {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Rounded.Settings,
                                        null, modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onOpenLyricsSettings()
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            // ★ ミニプレーヤーの位置切り替え（上部⇔下部）。
                            //   現在位置と逆側に切り替える単純なトグル項目。
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (miniPlayerPosition == "bottom")
                                            "ミニプレーヤーを上部に表示"
                                        else
                                            "ミニプレーヤーを下部に表示"
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (miniPlayerPosition == "bottom")
                                            androidx.compose.material.icons.Icons.Rounded.KeyboardArrowUp
                                        else
                                            androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown,
                                        null, modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onChangePosition(if (miniPlayerPosition == "bottom") "top" else "bottom")
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // シークバー（常時表示・操作可能）
                // ★ 以前はLinearProgressIndicator（表示専用・タップ操作不可）だったため、
                //   ミニプレーヤーから直接シークできなかった。HomeScreenA.kt・横画面版の
                //   歌詞画面(LyricsLandscapeContent)と同じNormalSeekBarに統一し、
                //   タップ・ドラッグでシークできるようにする。
                NormalSeekBar(
                    progress = mediaState.progress,
                    onProgressChange = { viewModel?.seekTo(it) },
                    thickness = 3.dp,
                    color = accentColor,
                    durationMs = mediaState.durationMs
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(mediaState.currentPositionStr, fontSize = 10.sp, color = subColor)
                    Text(mediaState.remainingTimeStr,   fontSize = 10.sp, color = subColor)
                }

                // 展開時のみ: コントロールボタン
                androidx.compose.animation.AnimatedVisibility(
                    visible = expanded,
                    enter = androidx.compose.animation.expandVertically() +
                            androidx.compose.animation.fadeIn(),
                    exit  = androidx.compose.animation.shrinkVertically() +
                            androidx.compose.animation.fadeOut()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel?.skipToPrevious() },
                            modifier = Modifier.size(36.dp)) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.SkipPrevious,
                                null, tint = textColor, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { viewModel?.seekRelative(-10000L) },
                            modifier = Modifier.size(36.dp)) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.Replay10,
                                null, tint = textColor, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { viewModel?.togglePlayPause() },
                            modifier = Modifier.size(44.dp)) {
                            Icon(
                                if (mediaState.isPlaying)
                                    androidx.compose.material.icons.Icons.Rounded.Pause
                                else
                                    androidx.compose.material.icons.Icons.Rounded.PlayArrow,
                                null, tint = textColor, modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(onClick = { viewModel?.seekRelative(10000L) },
                            modifier = Modifier.size(36.dp)) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.Forward10,
                                null, tint = textColor, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { viewModel?.skipToNext() },
                            modifier = Modifier.size(36.dp)) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.SkipNext,
                                null, tint = textColor, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}