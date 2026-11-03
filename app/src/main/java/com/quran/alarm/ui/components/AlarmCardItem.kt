package com.quran.alarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.model.QuranStructure
import com.quran.alarm.ui.AlarmUiFormatters

/**
 * Modern, clean Card representing a single Quran Alarm item with its Wird details and settings.
 */
@Composable
fun AlarmCardItem(
    alarm: AlarmItem,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) Color(0xFF1E293B) else Color(0xFF1E293B).copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = AlarmUiFormatters.formatTime12Hour(alarm.hour, alarm.minute),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isEnabled) Color.White else Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (alarm.isSequential) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (alarm.isSequential) "ورد متسلسل" else "ورد ثابت",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (alarm.isSequential) Color(0xFF10B981) else Color(0xFF38BDF8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${AlarmUiFormatters.formatRepeatDays(alarm.repeatDays)} • ${alarm.label}",
                    fontSize = 13.sp,
                    color = if (alarm.isEnabled) Color(0xFF94A3B8) else Color(0xFF475569)
                )

                Spacer(modifier = Modifier.height(3.dp))

                val surahAtStart = QuranStructure.getSurahAtPage(alarm.startPage)
                Text(
                    text = "سورة ${surahAtStart.nameArabic} • من صفحة ${alarm.startPage} إلى ${alarm.endPage} (${alarm.pageCount} صفحات) • صوت ${alarm.volumeLevel}%${if (alarm.isVibrationEnabled) " • اهتزاز" else ""}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (alarm.isEnabled) Color(0xFF38BDF8) else Color(0xFF475569)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "تعديل المنبه",
                        tint = Color(0xFF94A3B8)
                    )
                }

                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981)
                    )
                )

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف المنبه",
                        tint = Color(0xFFEF4444)
                    )
                }
            }
        }
    }
}
