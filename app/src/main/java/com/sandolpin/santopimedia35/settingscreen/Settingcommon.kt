package com.sandolpin.santopimedia35.settingscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sandolpin.santopimedia35.*
import com.sandolpin.santopimedia35.lyricsscreen.*

// 設定画面 共通UIパーツ（行・セクション・ダイアログ・スライダー等）

// ===== 共通トップバー =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubPageScaffold(
    title: String,
    onBack: () -> Unit,
    // ★ トップバー右側に置く追加要素（今のプレーヤー背景のミニプレビュー等）。
    //   何も指定しなければ何も表示しない（従来通り）。
    headerActions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "戻る")
                    }
                },
                actions = { headerActions() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

// =============================================================================
// 背景プレビュー系ヘルパー（外観設定ヘッダー・背景スタイル/色の混ぜ方ダイアログで使用）
// =============================================================================

// ★ Modifier.blur()（ぼかし効果）は内部的にRenderEffectを使っており、
//   これはAndroid 12 (API31, S)以降でしか動作しない。API31未満の端末では
//   blur()を呼んでも何も起きず（ぼかしがかからない元画像のまま）、
//   「ぼかし」設定を選んでも見た目上ほぼ意味をなさない状態になってしまう。
//   そのため、ぼかしに依存する設定項目はこの判定を使って選択肢自体を
//   無効化・除外し、使えない機能をそもそも選べないようにする。
//   （「動的アニメーション」もAPI33未満でのフォールバック描画がぼかしに依存しているため、
//   同様にAPI31未満では実質機能しない扱いとする）
internal fun isBlurRenderSupported(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

@Composable
fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
        BorderedShadowBox(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), content = content)
        }
    }
}

// ===== 縁取り + 影付きボックス（設定画面共通）=====
// これまでは縁取り(border)だけのSurfaceだったが、影(shadow)を追加してより立体的に見せる。
//
// ★ shadowとclip/borderを同じModifierチェーンに乗せると、環境やアニメーションとの組み合わせ
//   によって影が切り取られてしまうことがある（AlbumArtCard@HomescreenA.kt で既に踏んだ問題と同じ）。
//   そのため「影だけを描く外側Box」と「clip+border+中身を描く内側Box」を分離する。
@Composable
fun BorderedShadowBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    clickable: Boolean = false,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(
        modifier = modifier.shadow(
            elevation = 3.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.18f),
            spotColor = Color.Black.copy(alpha = 0.22f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, borderColor, shape)
                .then(if (clickable) Modifier.clickable { onClick() } else Modifier)
        ) {
            content()
        }
    }
}

@Composable
fun SettingRow(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

// =============================================================================
// 汎用: プレビュー付き選択肢ダイアログ（プルダウン設定の共通実装）
// -----------------------------------------------------------------------------
// SettingDropdownRow（旧: DropdownMenuによるプルダウン）が内部で使う共通ダイアログ。
// 参考画像のデザイン（各選択肢を角丸の枠で囲み、左にプレビュー、選択中は右に
// チェックマーク、下部中央に「閉じる」ボタン）に合わせている。
// タップで選択は即座に反映されるが、ダイアログ自体は自動では閉じない
// （画像の例と同じく、選んだ結果を確認しながら他の選択肢も見比べられるように、
// 明示的に「閉じる」を押すまで開いたままにしている）。
//
// ★ preview引数について:
//   nullの場合は「選択肢を区別しやすくするための色付き四角」を自動生成する
//   （GraphScreen.ktの円グラフ用パレットPIE_COLORSを流用。意味のある色ではなく、
//   あくまで一覧性のための識別カラー）。
//   設定の意味と直接対応した見た目（実際のアクセントカラー、アイコン、
//   サイズ感など）を見せたい場合は、呼び出し側でpreviewを個別に指定する
//   （例: ナビゲーションバーの背景、色抽出パターン、ショートカットのアイコン等）。
// =============================================================================
@Composable
private fun <T> GenericOptionSelectDialog(
    title: String,
    options: List<Pair<T, String>>,
    selectedValue: T,
    preview: (@Composable (T) -> Unit)?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                options.forEachIndexed { index, (value, optionLabel) ->
                    val selected = value == selectedValue
                    Surface(
                        onClick = { onSelect(value) },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                if (preview != null) preview(value)
                                else DefaultOptionPreview(index)
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                optionLabel, fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(
                                    Icons.Rounded.CheckCircle, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(4.dp))

                // ===== 閉じるボタン（参考画像に合わせ、下部中央のピル型ボタン） =====
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        ),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "閉じる",
                            color = MaterialTheme.colorScheme.surface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// デフォルトプレビュー: 選択肢を見分けやすくするための色付き四角（意味のある色ではない）
@Composable
private fun DefaultOptionPreview(index: Int) {
    val palette = com.sandolpin.santopimedia35.remember.PIE_COLORS
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette[index % palette.size])
    )
}

@Composable
fun <T> SettingDropdownRow(
    label: String,
    selectedValue: T,
    options: List<Pair<T, String>>,
    // ★ 各選択肢の左側に表示するプレビュー。指定が無ければ自動で色付き四角になる
    //   （DefaultOptionPreview参照）。呼び出し順序を崩さないよう、
    //   末尾のonSelection(トレーリングラムダ)より前のオプション引数として置く。
    preview: (@Composable (T) -> Unit)? = null,
    onSelection: (T) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedValue }?.second
        ?: selectedValue.toString()

    SettingRow(label) {
        OutlinedButton(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedLabel, modifier = Modifier.weight(1f), fontSize = 13.sp)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "選択肢を開く")
        }
    }

    if (showDialog) {
        GenericOptionSelectDialog(
            title = label,
            options = options,
            selectedValue = selectedValue,
            preview = preview,
            onSelect = { onSelection(it) },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(0.4f))
        // Android 12+ ではアプリのテーマ自体がシステムのダイナミックカラーに追従するため
        // （MainActivity.kt の SantopimediaThemeWrapper 参照）、
        // ここは MaterialTheme.colorScheme に素直に従う標準の Switch でよい。
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors()
        )
    }
}

@Composable
fun SettingSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 14.sp)
        Slider(value = value, onValueChange = onValueChange,
            valueRange = valueRange, steps = steps, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun TextStyleSetting(
    sectionLabel: String,
    fontSize: Int,
    fontWeight: Int,
    onFontSizeChange: (Int) -> Unit,
    onFontWeightChange: (Int) -> Unit,
    onReset: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sectionLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconButton(onClick = onReset, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "リセット", modifier = Modifier.size(18.dp))
            }
        }
        Text("大きさ（$fontSize）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Slider(value = fontSize.toFloat(), onValueChange = { onFontSizeChange(it.toInt()) },
            valueRange = 8f..40f, modifier = Modifier.fillMaxWidth())
        Text("太さ（$fontWeight）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Slider(value = fontWeight.toFloat(), onValueChange = { onFontWeightChange((it / 100).toInt() * 100) },
            valueRange = 100f..900f, steps = 7, modifier = Modifier.fillMaxWidth())
    }
}