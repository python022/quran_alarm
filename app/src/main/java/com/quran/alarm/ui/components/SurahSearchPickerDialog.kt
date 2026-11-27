package com.quran.alarm.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.alarm.alarm.domain.model.QuranStructure
import com.quran.alarm.alarm.domain.model.SurahInfo
import com.quran.alarm.ui.AlarmUiFormatters
import com.quran.alarm.ui.theme.GoldBadgeSurface
import com.quran.alarm.ui.theme.QuranColors

internal fun normalizeArabicForSearch(input: String): String {
    val sb = StringBuilder(input.length)
    for (ch in input) {
        when (ch) {
            in '\u064B'..'\u065F', '\u0670', '\u0640' -> Unit
            'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
            'ة' -> sb.append('ه')
            'ى' -> sb.append('ي')
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahSearchPickerBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    onSelectSurah: (SurahInfo) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSurahs = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            QuranStructure.SURAHS
        } else {
            val q = normalizeArabicForSearch(searchQuery.trim())
            QuranStructure.SURAHS.filter {
                normalizeArabicForSearch(it.nameArabic).contains(q) ||
                    it.number.toString().contains(q) ||
                    it.startPage.toString().contains(q)
            }
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = QuranColors.BgCard,
        contentColor = QuranColors.TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.65f)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        color = QuranColors.TextPrimary,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = QuranColors.TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "ابحث باسم السورة أو رقمها أو رقم الصفحة...",
                            fontSize = 13.sp,
                            color = QuranColors.TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = QuranColors.ActivePillText
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "مسح البحث",
                                    tint = QuranColors.TextMuted
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = QuranColors.TextPrimary,
                        unfocusedTextColor = QuranColors.TextPrimary,
                        focusedContainerColor = QuranColors.BgElevated,
                        unfocusedContainerColor = QuranColors.BgElevated,
                        focusedBorderColor = QuranColors.ActivePillBorder,
                        unfocusedBorderColor = QuranColors.BorderSubtle,
                        cursorColor = QuranColors.ActivePillBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSurahs, key = { it.number }) { surah ->
                        Surface(
                            onClick = {
                                onSelectSurah(surah)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = QuranColors.BgElevated,
                            border = BorderStroke(1.dp, QuranColors.BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${surah.number}. سورة ${surah.nameArabic}",
                                        color = QuranColors.TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = AlarmUiFormatters.formatAyahsCount(surah.ayahCount),
                                        color = QuranColors.TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }

                                GoldBadgeSurface(cornerRadius = 8) {
                                    Text(
                                        text = "ص ${surah.startPage}",
                                        color = QuranColors.GoldBadgeText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SurahSearchPickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onSelectSurah: (SurahInfo) -> Unit
) {
    SurahSearchPickerBottomSheet(
        title = title,
        onDismiss = onDismiss,
        onSelectSurah = onSelectSurah
    )
}
