package com.quran.alarm.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.alarm.ui.AlarmUiFormatters
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ModernIslamicTimePickerCard(
    hour12: Int,
    minute: Int,
    isAm: Boolean,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onAmPmToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val hour24 = AlarmUiFormatters.toHour24(hour12, isAm)
            val timeBadge = when {
                hour24 in 0..2 || hour24 >= 22 -> "🌙 ثلث الليل الآخر (قيام الليل)"
                hour24 in 3..6 -> "🌅 وقت السحر وصلاة الفجر"
                hour24 in 7..11 -> "☀️ صباحاً"
                hour24 in 12..16 -> "☀️ ظهراً ومساءً"
                else -> "🌆 مساءً"
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = timeBadge,
                    fontSize = 11.sp,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            // 3 adjacent columns: Hours, Separator, Minutes, AM/PM forced to LTR layout
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), shape = RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    VerticalWheelPicker(
                        items = (1..12).toList(),
                        selectedItem = hour12,
                        onItemSelected = onHourChange,
                        isInfinite = true,
                        itemHeight = 44.dp,
                        fontSize = 28.sp,
                        showDividers = true,
                        itemLabel = { String.format(Locale.US, "%02d", it) },
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier.height(44.dp * 3),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ":",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    VerticalWheelPicker(
                        items = (0..59).toList(),
                        selectedItem = minute,
                        onItemSelected = onMinuteChange,
                        isInfinite = true,
                        itemHeight = 44.dp,
                        fontSize = 28.sp,
                        showDividers = true,
                        itemLabel = { String.format(Locale.US, "%02d", it) },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    VerticalWheelPicker(
                        items = listOf(true, false),
                        selectedItem = isAm,
                        onItemSelected = onAmPmToggle,
                        isInfinite = false,
                        itemHeight = 44.dp,
                        fontSize = 22.sp,
                        showDividers = true,
                        itemLabel = { if (it) "ص" else "م" },
                        modifier = Modifier.weight(0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Reusable vertical scroll wheel / drum picker:
 * - 3 visible items vertically (center is selected in large bold white; top and bottom in dim gray)
 * - 2 thin horizontal divider lines separating selected middle item
 * - Snap to center
 * - Infinite loop support for hours/minutes
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> VerticalWheelPicker(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    isInfinite: Boolean = false,
    itemHeight: Dp = 44.dp,
    visibleItemsCount: Int = 3,
    showDividers: Boolean = true,
    fontSize: TextUnit = 22.sp,
    itemLabel: (T) -> String = { it.toString() }
) {
    if (items.isEmpty()) return

    val currentSelectedItem by rememberUpdatedState(selectedItem)
    val currentOnItemSelected by rememberUpdatedState(onItemSelected)
    val coroutineScope = rememberCoroutineScope()
    val safeVisibleCount = remember(visibleItemsCount) {
        val base = visibleItemsCount.coerceAtLeast(1)
        if (base % 2 == 0) base + 1 else base
    }
    val centerOffset = safeVisibleCount / 2
    val isEffectivelyInfinite = isInfinite && items.size > 1
    val repeatCount = 10_000
    val middleBase = if (isEffectivelyInfinite) (repeatCount / 2) * items.size else 0

    val initialIndex = remember(items, selectedItem) {
        val idx = items.indexOf(selectedItem)
        if (idx >= 0) idx else 0
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (isEffectivelyInfinite) {
            (middleBase + initialIndex - centerOffset).coerceAtLeast(0)
        } else {
            initialIndex
        }
    )

    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val centerVisibleIndex by remember(centerOffset) {
        derivedStateOf { listState.firstVisibleItemIndex + centerOffset }
    }

    // Sync external programmatic updates to selectedItem
    LaunchedEffect(selectedItem) {
        val targetIdx = items.indexOf(selectedItem)
        if (targetIdx >= 0) {
            val currentCenter = if (isEffectivelyInfinite) {
                (listState.firstVisibleItemIndex + centerOffset) % items.size
            } else {
                listState.firstVisibleItemIndex.coerceIn(0, items.size - 1)
            }
            if (currentCenter != targetIdx) {
                if (isEffectivelyInfinite) {
                    val currentCenterAbsolute = listState.firstVisibleItemIndex + centerOffset
                    val cycleBase = (currentCenterAbsolute / items.size) * items.size
                    var targetFirst = cycleBase + targetIdx - centerOffset
                    val diff = targetIdx - currentCenter
                    if (diff > items.size / 2) {
                        targetFirst -= items.size
                    } else if (diff < -items.size / 2) {
                        targetFirst += items.size
                    }
                    listState.animateScrollToItem(targetFirst.coerceAtLeast(0))
                } else {
                    listState.animateScrollToItem(targetIdx)
                }
            }
        }
    }

    // Emit selection only when scrolling settles to prevent pixel-level churn and lag
    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.isScrollInProgress) }
            .collect { (firstIndex, isScrolling) ->
                if (!isScrolling) {
                    val centerIndex = if (isEffectivelyInfinite) {
                        (firstIndex + centerOffset) % items.size
                    } else {
                        firstIndex.coerceIn(0, items.size - 1)
                    }
                    val selected = items.getOrNull(centerIndex)
                    if (selected != null && selected != currentSelectedItem) {
                        currentOnItemSelected(selected)
                    }
                }
            }
    }

    Box(
        modifier = modifier.height(itemHeight * safeVisibleCount)
    ) {
        if (isEffectivelyInfinite) {
            val totalCount = items.size * repeatCount
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(totalCount) { index ->
                    val actualItem = items[index % items.size]
                    val isCenter = index == centerVisibleIndex
                    WheelPickerItemBox(
                        label = itemLabel(actualItem),
                        isCenter = isCenter,
                        itemHeight = itemHeight,
                        fontSize = fontSize,
                        onClick = {
                            coroutineScope.launch {
                                val targetScroll = (index - centerOffset).coerceAtLeast(0)
                                listState.animateScrollToItem(targetScroll)
                            }
                        }
                    )
                }
            }
        } else {
            val paddedItems = remember(items, centerOffset) {
                List(centerOffset) { null } + items + List(centerOffset) { null }
            }
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(paddedItems.size) { index ->
                    val item = paddedItems[index]
                    val isCenter = index == centerVisibleIndex
                    WheelPickerItemBox(
                        label = item?.let(itemLabel),
                        isCenter = isCenter,
                        itemHeight = itemHeight,
                        fontSize = fontSize,
                        onClick = if (item != null) {
                            {
                                coroutineScope.launch {
                                    val targetFirst = (index - centerOffset).coerceIn(0, items.size - 1)
                                    listState.animateScrollToItem(targetFirst)
                                }
                            }
                        } else null
                    )
                }
            }
        }

        if (showDividers) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = itemHeight * centerOffset)
                    .height(1.dp)
                    .background(Color(0xFF10B981).copy(alpha = 0.4f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = itemHeight * (centerOffset + 1))
                    .height(1.dp)
                    .background(Color(0xFF10B981).copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
private fun WheelPickerItemBox(
    label: String?,
    isCenter: Boolean,
    itemHeight: Dp,
    fontSize: TextUnit,
    onClick: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (label != null) {
            Text(
                text = label,
                fontSize = if (isCenter) fontSize else (fontSize.value * 0.78f).sp,
                fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                color = if (isCenter) Color.White else Color(0xFF94A3B8),
                modifier = Modifier.alpha(if (isCenter) 1f else 0.35f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

