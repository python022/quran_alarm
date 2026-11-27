package com.quran.alarm

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quran.alarm.alarm.data.storage.DirectBootStorageHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuranAlarmInstrumentedTest {

    private val clearAlarmPrefsRule = object : ExternalResource() {
        override fun before() {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val storageCtx = DirectBootStorageHelper.resolveStorageContext(ctx)
            storageCtx.getSharedPreferences("quran_alarm_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(clearAlarmPrefsRule).around(composeRule)

    @Test
    fun offlineCairoFont_loadsOnAndroidDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.quran.alarm", context.packageName)
        assertNotNull(ResourcesCompat.getFont(context, R.font.cairo_regular))
        assertNotNull(ResourcesCompat.getFont(context, R.font.cairo_medium))
        assertNotNull(ResourcesCompat.getFont(context, R.font.cairo_semibold))
        assertNotNull(ResourcesCompat.getFont(context, R.font.cairo_bold))
    }

    @Test
    fun fullAddConfirmAndDeleteAlarmFlow_rendersSingleCardWithoutDuplicates() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("قائمة المنبهات (0)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("منبه القرآن الكريم").assertIsDisplayed()
        composeRule.onNodeWithText("قائمة المنبهات (0)").assertIsDisplayed()

        // Open full-screen alarm configuration
        composeRule.onNodeWithContentDescription("إضافة منبه").performClick()
        composeRule.onNodeWithText("ضبط منبه الورد القرآني").assertIsDisplayed()

        // Verify progressive disclosure rows exist
        composeRule.onNodeWithText("🔔 النغمة والصوت").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("🔁 التكرار").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("🏷️ تسمية المنبه").performScrollTo().assertIsDisplayed()

        // Tap Save -> triggers Wird Confirmation AlertDialog
        composeRule.onNodeWithText("حفظ المنبه").performClick()
        composeRule.onNodeWithText("تأكيد حجم الورد قبل الحفظ").assertIsDisplayed()
        composeRule.onNodeWithText("موافق وحفظ").performClick()

        // Verify exactly 1 card appears (no visual duplication)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("قائمة المنبهات (1)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("قائمة المنبهات (1)").assertIsDisplayed()
        composeRule.onNodeWithText("ورد متسلسل").assertIsDisplayed()

        // Open edit screen and delete from inside edit screen
        composeRule.onNodeWithText("سورة الفاتحة • صفحة 1", substring = true).performClick()
        composeRule.onNodeWithText("تعديل منبه الورد القرآني").assertIsDisplayed()
        composeRule.onNodeWithText("حذف المنبه").performScrollTo().performClick()
        composeRule.onNodeWithText("تأكيد حذف المنبه").assertIsDisplayed()
        composeRule.onNodeWithText("حذف").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("قائمة المنبهات (0)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("قائمة المنبهات (0)").assertIsDisplayed()
    }
}
