package com.quran.alarm.ui.components

import android.view.MotionEvent
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.alarm.ui.AlarmUiFormatters
import com.quran.alarm.ui.theme.GoldBadgeSurface
import com.quran.alarm.ui.theme.QuranColors
import kotlinx.coroutines.flow.distinctUntilChanged
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = QuranColors.BgCard),
        border = BorderStroke(1.dp, QuranColors.BorderSubtle)
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

            GoldBadgeSurface(
                cornerRadius = 20,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = timeBadge,
                    fontSize = 12.sp,
                    color = QuranColors.GoldBadgeText,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // Keep outer row in RTL; lock ONLY the numeric digits container (05 : 00) to LTR
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(QuranColors.BgElevated, shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(2f)
                    ) {
                        VerticalWheelPicker(
                            items = (1..12).toList(),
                            selectedItem = hour12,
                            onItemSelected = onHourChange,
                            isInfinite = true,
                            itemHeight = 44.dp,
                            fontSize = 32.sp,
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
                                fontWeight = FontWeight.Bold,
                                color = QuranColors.CtaBg,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        }

                        VerticalWheelPicker(
                            items = (0..59).toList(),
                            selectedItem = minute,
                            onItemSelected = onMinuteChange,
                            isInfinite = true,
                            itemHeight = 44.dp,
                            fontSize = 32.sp,
                            showDividers = true,
                            itemLabel = { String.format(Locale.US, "%02d", it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                VerticalWheelPicker(
                    items = listOf(true, false),
                    selectedItem = isAm,
                    onItemSelected = onAmPmToggle,
                    isInfinite = false,
                    itemHeight = 44.dp,
                    fontSize = 20.sp,
                    showDividers = true,
                    itemLabel = { if (it) "ص" else "م" },
                    modifier = Modifier.weight(0.75f)
                )
            }
        }
    }
}

private class WheelEmissionTracker<T> {
    var hasValue: Boolean = false
    var value: T? = null
}

/**
 * Reusable vertical scroll wheel / drum picker:
 * - Blocks parent ScrollView/Compose verticalScroll interception during touch & nested fling
 * - Emits live selection updates while scrolling AND on idle so coupled Start/End wheels stay in sync
 * - Snap-to-center behavior with optional infinite loop for hours/minutes
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
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
    val hostView = LocalView.current

    val safeVisibleCount = remember(visibleItemsCount) {
        val base = visibleItemsCount.coerceAtLeast(1)
        if (base % 2 == 0) base + 1 else base
    }
    val centerOffset = safeVisibleCount / 2
    val isEffectivelyInfinite = isInfinite && items.size > 1
    val repeatCount = 10_000
    val middleBase = if (isEffectivelyInfinite) (repeatCount / 2) * items.size else 0

    val initialIndex = remember(items) {
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
        derivedStateOf { resolveCenteredItemIndex(listState, centerOffset) }
    }

    val lastEmittedFromWheel = remember(items) { WheelEmissionTracker<T>() }
    var programmaticScrollCount by remember { mutableIntStateOf(0) }

    val wheelNestedScrollBlocker = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = Offset(0f, available.y)

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity = Velocity(0f, available.y)
        }
    }

    // Sync external programmatic updates to selectedItem without fighting user drag/fling
    LaunchedEffect(selectedItem, items) {
        if (lastEmittedFromWheel.hasValue && lastEmittedFromWheel.value == selectedItem) {
            return@LaunchedEffect
        }
        val targetIdx = items.indexOf(selectedItem)
        if (targetIdx >= 0) {
            val centeredRaw = resolveCenteredItemIndex(listState, centerOffset)
            val currentCenter = if (isEffectivelyInfinite) {
                centeredRaw % items.size
            } else {
                (centeredRaw - centerOffset).coerceIn(0, items.size - 1)
            }
            if (currentCenter != targetIdx) {
                programmaticScrollCount++
                try {
                    if (isEffectivelyInfinite) {
                        val cycleBase = (centeredRaw / items.size) * items.size
                        var targetFirst = cycleBase + targetIdx - centerOffset
                        val diff = targetIdx - currentCenter
                        if (diff > items.size / 2) {
                            targetFirst -= items.size
                        } else if (diff < -items.size / 2) {
                            targetFirst += items.size
                        }
                        val safeTarget = targetFirst.coerceAtLeast(0)
                        if (listState.isScrollInProgress || kotlin.math.abs(currentCenter - targetIdx) > 15) {
                            listState.scrollToItem(safeTarget)
                        } else {
                            listState.animateScrollToItem(safeTarget)
                        }
                    } else {
                        if (listState.isScrollInProgress || kotlin.math.abs(currentCenter - targetIdx) > 15) {
                            listState.scrollToItem(targetIdx)
                        } else {
                            listState.animateScrollToItem(targetIdx)
                        }
                    }
                    lastEmittedFromWheel.hasValue = true
                    lastEmittedFromWheel.value = selectedItem
                } finally {
                    programmaticScrollCount--
                }
            } else {
                lastEmittedFromWheel.hasValue = true
                lastEmittedFromWheel.value = selectedItem
            }
        }
    }

    // Emit selection immediately on center item changes (both during fast scroll and on idle)
    LaunchedEffect(listState, items, isEffectivelyInfinite, centerOffset) {
        snapshotFlow {
            resolveCenteredItemIndex(listState, centerOffset) to listState.isScrollInProgress
        }
            .distinctUntilChanged()
            .collect { (centeredRaw, _) ->
                if (programmaticScrollCount > 0) return@collect
                val centerIndex = if (isEffectivelyInfinite) {
                    centeredRaw % items.size
                } else {
                    (centeredRaw - centerOffset).coerceIn(0, items.size - 1)
                }
                val selected = items.getOrNull(centerIndex)
                if (selected != null && selected != currentSelectedItem) {
                    lastEmittedFromWheel.hasValue = true
                    lastEmittedFromWheel.value = selected
                    currentOnItemSelected(selected)
                }
            }
    }

    Box(
        modifier = modifier
            .height(itemHeight * safeVisibleCount)
            .nestedScroll(wheelNestedScrollBlocker)
            .pointerInteropFilter { motionEvent ->
                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        hostView.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        hostView.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }
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
                    .background(QuranColors.ActivePillBorder.copy(alpha = 0.45f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = itemHeight * (centerOffset + 1))
                    .height(1.dp)
                    .background(QuranColors.ActivePillBorder.copy(alpha = 0.45f))
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
                fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Medium,
                color = if (isCenter) QuranColors.TextPrimary else QuranColors.TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

private fun resolveCenteredItemIndex(listState: LazyListState, centerOffset: Int): Int {
    val layoutInfo = listState.layoutInfo
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isNotEmpty() && layoutInfo.viewportEndOffset > layoutInfo.viewportStartOffset) {
        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
        return visible.minByOrNull { item ->
            kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
        }?.index ?: (listState.firstVisibleItemIndex + centerOffset)
    }
    return listState.firstVisibleItemIndex + centerOffset
}
