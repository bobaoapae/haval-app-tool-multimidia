package br.com.redesurftank.havalshisuku.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.redesurftank.havalshisuku.ui.theme.IbmPlexSans
import br.com.redesurftank.havalshisuku.ui.theme.Michroma
import kotlinx.coroutines.launch

/**
 * Redesign da tela de Configurações — Rodada 15 (handoff de design).
 *
 * IMPORTANTE: esta é apenas a CAMADA DE APRESENTAÇÃO. Ela consome os mesmos
 * [SettingItem] construídos hoje pela BasicSettingsTab (todas as lambdas de
 * onCheckedChange/onSliderChange/customContent e chaves ficam INTACTAS). O
 * único acréscimo ao modelo é o campo opcional [SettingItem.group].
 */

// ---- Tokens (Rodada 15) ----
object ImpTokens {
    val Ground = Color(0xFF0A0A0C)
    val Container = Color(0xFF12141A)
    val Accent = Color(0xFF4A9EFF)
    val OnAccent = Color(0xFF0A0A0C)
    val ExpandTint = Color(0x0F4A9EFF)     // rgba(74,158,255,.06)
    val Hairline = Color(0x0DFFFFFF)       // rgba(255,255,255,.05)
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFF8A93A3)
    val TextMuted = Color(0xFF4B5563)
    val TrackOff = Color(0xFF262A33)
    val ThumbOff = Color(0xFF6B7280)
    val Attention = Color(0xFFFBBF24)
    val Danger = Color(0xFFEF4444)
    val IndexActiveTint = Color(0x244A9EFF) // rgba(74,158,255,.14)
}

/** Ordem e nomes canônicos dos 7 grupos por domínio. */
object SettingsGroups {
    const val SHUTDOWN = "Ao desligar / retrovisor"
    const val SPEED = "Por velocidade"
    const val DRIVE = "Condução & energia"
    const val CLIMATE = "Climatização & conforto"
    const val SAFETY = "Segurança & avisos"
    const val DISPLAY = "Tela & som"
    const val FEATURES = "Recursos & integração"
    val ORDER = listOf(SHUTDOWN, SPEED, DRIVE, CLIMATE, SAFETY, DISPLAY, FEATURES)
}

// ---------------------------------------------------------------------------
// Layout principal: índice de grupos (330dp) + conteúdo agrupado (scroll-spy)
// ---------------------------------------------------------------------------
@Composable
fun GroupedSettingsLayout(items: List<SettingItem>) {
    // Monta os grupos na ordem canônica; itens sem grupo conhecido caem em "Outros"
    // (garantia de que NENHUM controle some da tela, mesmo se o grupo não bater).
    val known = SettingsGroups.ORDER.toSet()
    val orderedGroups = SettingsGroups.ORDER.mapNotNull { key ->
        val its = items.filter { it.group == key }
        if (its.isEmpty()) null else key to its
    }
    val ungrouped = items.filter { it.group == null || it.group !in known }
    val allGroups = if (ungrouped.isEmpty()) orderedGroups else orderedGroups + ("Outros" to ungrouped)

    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val visibleGroups =
        if (q.isEmpty()) allGroups
        else allGroups.mapNotNull { (name, its) ->
            val filtered = its.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.description.contains(q, ignoreCase = true)
            }
            if (filtered.isEmpty()) null else name to filtered
        }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Chaveado pelo nº de grupos visíveis: ao filtrar na busca, o limite superior
    // do scroll-spy é recalculado (evita destacar um índice inexistente).
    val lastGroupIndex = (visibleGroups.size - 1).coerceAtLeast(0)
    val activeIndex by remember(lastGroupIndex) {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, lastGroupIndex) }
    }

    val total = items.size
    val onCount = items.count { it.checked }

    Row(modifier = Modifier.fillMaxSize()) {
        GroupIndexRail(
            groups = visibleGroups.map { it.first to it.second.size },
            activeIndex = activeIndex,
            totalCount = total,
            onCount = onCount,
            query = query,
            onQueryChange = { query = it },
            onGroupClick = { idx -> scope.launch { listState.animateScrollToItem(idx) } }
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp, end = 8.dp)
        ) {
            if (visibleGroups.isEmpty()) {
                item {
                    Text(
                        "Nenhum ajuste encontrado.",
                        color = ImpTokens.TextSecondary,
                        fontFamily = IbmPlexSans,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                visibleGroups.forEach { (name, groupItems) ->
                    item(key = name) { GroupBlock(header = name, items = groupItems) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Índice de grupos (coluna esquerda do conteúdo)
// ---------------------------------------------------------------------------
@Composable
private fun GroupIndexRail(
    groups: List<Pair<String, Int>>,
    activeIndex: Int,
    totalCount: Int,
    onCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    onGroupClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .width(330.dp)
            .fillMaxHeight()
            .drawBehind {
                drawLine(
                    color = ImpTokens.Hairline,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(end = 20.dp, top = 4.dp)
    ) {
        Text(
            text = "CONFIGURAÇÕES",
            fontFamily = Michroma,
            fontSize = 20.sp,
            color = ImpTokens.TextPrimary,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp, bottom = 18.dp)
        )
        SearchPill(query = query, onQueryChange = onQueryChange)
        Spacer(modifier = Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            groups.forEachIndexed { i, (name, count) ->
                val active = i == activeIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) ImpTokens.IndexActiveTint else Color.Transparent)
                        .clickable { onGroupClick(i) }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        fontFamily = IbmPlexSans,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 15.sp,
                        color = if (active) ImpTokens.Accent else ImpTokens.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = "$count",
                        fontFamily = IbmPlexSans,
                        fontSize = 13.sp,
                        color = if (active) ImpTokens.Accent else ImpTokens.TextMuted
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(top = 12.dp, start = 4.dp, bottom = 4.dp)) {
            Text(
                text = "$totalCount ajustes · $onCount ligados",
                fontFamily = IbmPlexSans,
                fontSize = 12.sp,
                color = ImpTokens.TextSecondary
            )
            Text(
                text = "sincronizado com o carro",
                fontFamily = IbmPlexSans,
                fontSize = 12.sp,
                color = ImpTokens.TextMuted
            )
        }
    }
}

@Composable
private fun SearchPill(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ImpTokens.Container)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val r = size.minDimension * 0.30f
            val sw = 1.6.dp.toPx()
            drawCircle(
                color = ImpTokens.TextMuted,
                radius = r,
                center = Offset(r + sw, r + sw),
                style = Stroke(width = sw)
            )
            drawLine(
                color = ImpTokens.TextMuted,
                start = Offset(r * 1.9f + sw, r * 1.9f + sw),
                end = Offset(size.width, size.height),
                strokeWidth = sw
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Buscar ajuste...",
                    color = ImpTokens.TextMuted,
                    fontFamily = IbmPlexSans,
                    fontSize = 15.sp
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = ImpTokens.TextPrimary,
                    fontFamily = IbmPlexSans,
                    fontSize = 15.sp
                ),
                cursorBrush = SolidColor(ImpTokens.Accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Container de grupo (sem borda) + header em Michroma
// ---------------------------------------------------------------------------
@Composable
private fun GroupBlock(header: String, items: List<SettingItem>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = header.uppercase(),
            fontFamily = Michroma,
            fontSize = 15.sp,
            letterSpacing = 1.8.sp,
            color = ImpTokens.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 14.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ImpTokens.Container)
        ) {
            items.forEachIndexed { i, item ->
                if (i > 0) {
                    HorizontalDivider(
                        color = ImpTokens.Hairline,
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 26.dp)
                    )
                }
                SettingsRow(item)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Linha de ajuste (toggle / toggle+expandir / slider inline). Mantém o mesmo
// contrato de clique do card antigo, mas com o alvo de toque na linha inteira.
// ---------------------------------------------------------------------------
@Composable
private fun SettingsRow(item: SettingItem) {
    val hasInlineSlider =
        item.sliderValue != null && item.sliderRange != null && item.onSliderChange != null
    val expandable = item.customContent != null || hasInlineSlider

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = item.enabled) {
                    if (!item.hideSwitch) item.onCheckedChange(!item.checked)
                }
                .heightIn(min = 88.dp)
                .padding(horizontal = 26.dp, vertical = 19.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = item.title,
                    fontFamily = IbmPlexSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    color = if (item.enabled) ImpTokens.TextPrimary else ImpTokens.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.description,
                        fontFamily = IbmPlexSans,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.5.sp,
                        color = if (item.enabled) ImpTokens.TextSecondary else ImpTokens.TextMuted,
                        lineHeight = 19.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!item.hideSwitch) {
                ImpSwitch(
                    checked = item.checked,
                    onCheckedChange = { item.onCheckedChange(it) },
                    enabled = item.enabled
                )
            } else {
                Text(
                    text = "›",
                    color = ImpTokens.Accent,
                    fontFamily = IbmPlexSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp
                )
            }
        }

        AnimatedVisibility(
            visible = item.checked && expandable,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(120))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ImpTokens.ExpandTint)
                    .drawBehind {
                        drawRect(
                            color = ImpTokens.Accent,
                            topLeft = Offset.Zero,
                            size = Size(3.dp.toPx(), size.height)
                        )
                    }
                    .padding(start = 26.dp, end = 26.dp, top = 6.dp, bottom = 20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (hasInlineSlider) ImpInlineSlider(item)
                    item.customContent?.invoke()
                }
            }
        }
    }
}

@Composable
private fun ImpInlineSlider(item: SettingItem) {
    val range = item.sliderRange!!
    val step = item.sliderStep ?: 1
    val computedSteps = ((range.last - range.first) / step) - 1
    val rounded = ((item.sliderValue!! / step) * step).toFloat()
    Column(modifier = Modifier.fillMaxWidth()) {
        item.sliderLabel?.let {
            Text(
                text = it,
                fontFamily = IbmPlexSans,
                color = ImpTokens.TextPrimary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Slider(
            value = rounded,
            onValueChange = { newValue ->
                item.onSliderChange!!(((newValue / step).toInt() * step))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = if (computedSteps < 0) 0 else computedSteps,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = ImpTokens.Accent,
                inactiveTrackColor = ImpTokens.TrackOff,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = Color.White,
                disabledActiveTrackColor = ImpTokens.Accent,
                disabledInactiveTrackColor = ImpTokens.TrackOff
            )
        )
    }
}

@Composable
private fun ImpSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.scale(1.05f),
        colors = SwitchDefaults.colors(
            checkedThumbColor = ImpTokens.OnAccent,
            checkedTrackColor = ImpTokens.Accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = ImpTokens.ThumbOff,
            uncheckedTrackColor = ImpTokens.TrackOff,
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = ImpTokens.OnAccent,
            disabledCheckedTrackColor = ImpTokens.Accent.copy(alpha = 0.5f),
            disabledUncheckedThumbColor = ImpTokens.ThumbOff,
            disabledUncheckedTrackColor = ImpTokens.TrackOff.copy(alpha = 0.5f)
        )
    )
}
