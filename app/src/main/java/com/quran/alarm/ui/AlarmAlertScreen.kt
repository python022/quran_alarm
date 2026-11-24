package com.quran.alarm.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.alarm.alarm.domain.model.QuranStructure
import com.quran.alarm.alarm.ui.viewmodel.AlarmAlertStage
import com.quran.alarm.alarm.ui.viewmodel.MushafReaderUiState
import com.quran.alarm.quran.ui.QuranPageView
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmAlertScreen(
    uiState: MushafReaderUiState,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onLineClicked: (Int) -> Unit,
    onNextStep: () -> Unit,
    onJumpToIncompletePage: () -> Unit = onPrevPage,
    onStartReading: () -> Unit,
    onSnooze: () -> Unit,
    onDismissAlarm: () -> Unit
) {
    val topSafeInsets = WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val bottomSafeInsets = WindowInsets.navigationBarsIgnoringVisibility

    if (uiState.stage == AlarmAlertStage.MUSHAF_READING) {
        // Fullscreen Madinah Mushaf Reader with authentic paper illumination
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFFDF5))
        ) {
            // Top Reader Bar
            Surface(
                color = Color(0xFF1E293B),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(topSafeInsets)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val canGoPrev = uiState.hasPrevPageInWird && !uiState.isLoading
                    OutlinedButton(
                        onClick = onPrevPage,
                        enabled = canGoPrev,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "السابقة",
                            tint = if (canGoPrev) Color.White else Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("السابقة", fontSize = 12.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (uiState.isAyahMode) {
                            val surah = QuranStructure.getSurahByNumber(uiState.wirdSurahNumber!!)
                            Text(
                                text = "سورة ${surah.nameArabic} • آية ${uiState.startAyah} - ${uiState.endAyah}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF38BDF8)
                            )
                        } else {
                            Text(
                                text = "صفحة ${uiState.currentPage} من 604",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF38BDF8)
                            )
                        }
                        Text(
                            text = "ص ${uiState.currentPage} • السطر ${uiState.activeLine} من ${uiState.lastReadableLineOnPage}",
                            fontSize = 12.sp,
                            color = Color(0xFF10B981)
                        )
                    }

                    val canGoNext = uiState.canNavigateNextInAlert && !uiState.isLoading
                    OutlinedButton(
                        onClick = onNextPage,
                        enabled = canGoNext,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("التالية", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "التالية",
                            tint = if (canGoNext) Color.White else Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Main Mushaf Page Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                QuranPageView(
                    pageNumber = uiState.currentPage,
                    pageBitmap = uiState.currentBitmap,
                    lines = uiState.currentLines,
                    activeLine = uiState.activeLine,
                    onLineClicked = { line ->
                        if (!uiState.isLoading) {
                            onLineClicked(line)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom Actions Control Bar (clears system 3-button Home & gesture bars even in immersive mode)
            Surface(
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(bottomSafeInsets)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val atEndWithoutFullCompletion = !uiState.isDismissEnabled &&
                        !uiState.hasNextPageInWird &&
                        uiState.activeLine >= uiState.lastReadableLineOnPage

                    when {
                        uiState.isDismissEnabled -> {
                            Button(
                                onClick = onDismissAlarm,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "إتمام الورد المبارك والخروج",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }
                        }

                        atEndWithoutFullCompletion -> {
                            Button(
                                onClick = onJumpToIncompletePage,
                                enabled = !uiState.isLoading,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E293B)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text(
                                    text = "إكمال الصفحات المتبقية ←",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFBBF24)
                                )
                            }
                        }

                        else -> {
                            Button(
                                onClick = onNextStep,
                                enabled = !uiState.isLoading,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E293B)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                val btnText = if (uiState.activeLine < uiState.lastReadableLineOnPage) {
                                    "السطر التالي"
                                } else if (uiState.hasNextPageInWird) {
                                    "الصفحة التالية ←"
                                } else {
                                    "السطر التالي"
                                }
                                Text(
                                    text = btnText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF38BDF8)
                                )
                                if (!btnText.contains("←")) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        RingingStageContent(
            uiState = uiState,
            topSafeInsets = topSafeInsets,
            bottomSafeInsets = bottomSafeInsets,
            onStartReading = onStartReading,
            onSnooze = onSnooze
        )
    }
}

@Composable
private fun RingingStageContent(
    uiState: MushafReaderUiState,
    topSafeInsets: WindowInsets,
    bottomSafeInsets: WindowInsets,
    onStartReading: () -> Unit,
    onSnooze: () -> Unit
) {
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Dedicated Ringing Stage Screen before entering Quran recitation
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(topSafeInsets)
            .windowInsetsPadding(bottomSafeInsets)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Text(
                text = AlarmUiFormatters.formatCurrentDate(currentTimeMillis),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = AlarmUiFormatters.formatCurrentDigitalTime(currentTimeMillis),
                fontSize = 68.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .scale(pulseScale)
                    .background(Color(0xFF10B981).copy(alpha = 0.2f), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = uiState.alarmLabel,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (uiState.isAyahMode) {
                    val surah = QuranStructure.getSurahByNumber(uiState.wirdSurahNumber!!)
                    Text(
                        text = "ورد اليوم: سورة ${surah.nameArabic} (الآيات ${uiState.startAyah} - ${uiState.endAyah})",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "(${uiState.dailyAyahCount} آية • صفحة ${uiState.startPage} إلى ${uiState.endPage})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF10B981)
                    )
                } else {
                    Text(
                        text = "ورد اليوم: صفحة ${uiState.startPage} إلى ${uiState.endPage}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "(${uiState.pageCount} صفحات متتالية)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Prominent Hero Action: Start Reading Wird (Silences sound & vibration, enters Mushaf)
            Button(
                onClick = onStartReading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text(
                    text = "📖 بدء قراءة الورد",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Snooze option (if enabled)
            if (uiState.isSnoozeEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSnooze,
                    enabled = !uiState.isDismissedOrSnoozed,
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF334155),
                        disabledContainerColor = Color(0xFF1E293B)
                    )
                ) {
                    Text(
                        text = if (uiState.isDismissedOrSnoozed) "جاري التأجيل..." else "تأجيل (5 دقائق)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (uiState.isDismissedOrSnoozed) Color(0xFF94A3B8) else Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}

