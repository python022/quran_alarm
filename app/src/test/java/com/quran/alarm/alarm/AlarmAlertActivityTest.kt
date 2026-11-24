package com.quran.alarm.alarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import com.quran.alarm.alarm.data.storage.AlarmSessionStore
import com.quran.alarm.alarm.data.storage.PersistedAlarmSession
import com.quran.alarm.alarm.data.storage.SessionReadingProgress
import com.quran.alarm.alarm.service.AlarmAlertIntentFactory
import com.quran.alarm.alarm.ui.viewmodel.AlarmAlertStage
import com.quran.alarm.ui.AlarmAlertActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmAlertActivityTest {

    @Test
    fun verifyAlertActivityInManifest() {
        val manifestFile = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() }
        assertNotNull("AndroidManifest.xml must exist for verification", manifestFile)
        val content = manifestFile!!.readText()
        assertTrue("AlarmAlertActivity must be declared in AndroidManifest", content.contains("AlarmAlertActivity"))
        assertTrue("showWhenLocked must be enabled", content.contains("showWhenLocked=\"true\""))
        assertTrue("turnScreenOn must be enabled", content.contains("turnScreenOn=\"true\""))
        assertTrue("singleInstance must be set", content.contains("launchMode=\"singleInstance\""))
        assertTrue("directBootAware must be set on AlarmAlertActivity", content.contains("android:directBootAware=\"true\""))
    }

    @Test
    fun launchWithoutAlarmIdAndWithoutActiveSession_finishesGracefully() {
        val context: Context = RuntimeEnvironment.getApplication()
        AlarmSessionStore.fromContext(context).clearSession()

        val emptyIntent = Intent(context, AlarmAlertActivity::class.java)
        val controller = Robolectric.buildActivity(AlarmAlertActivity::class.java, emptyIntent).create()
        assertTrue(
            "Activity with missing EXTRA_ALARM_ID and no persisted session must finish cleanly",
            controller.get().isFinishing
        )
    }

    @Test
    fun processDeathRecreation_withNonNullSavedInstanceState_restoresSessionAndBlocksBypassKeys() {
        val context: Context = RuntimeEnvironment.getApplication()
        val store = AlarmSessionStore.fromContext(context)
        store.clearSession()
        store.saveSession(
            PersistedAlarmSession(
                active = true,
                stage = AlarmSessionStore.STAGE_MUSHAF_READING,
                alarmId = 55,
                label = "سورة الكهف",
                startPage = 293,
                pageCount = 5,
                volumeLevel = 90,
                isVibrate = true,
                soundUri = null,
                wirdSurahNumber = null,
                startAyah = null,
                dailyAyahCount = null
            )
        )
        store.saveProgress(
            SessionReadingProgress(
                alarmId = 55,
                stageName = AlarmAlertStage.MUSHAF_READING.name,
                currentPage = 295,
                completedPages = setOf(293, 294),
                visitedLineByPage = mapOf(295 to 6)
            )
        )

        val recreateIntent = AlarmAlertIntentFactory.buildAlertIntent(
            context = context,
            alarmId = 55,
            label = "سورة الكهف",
            targetPage = 293,
            startPage = 293,
            pageCount = 5,
            isFreshTrigger = true // even if original intent had fresh=true, savedInstanceState != null must restore!
        )
        val fakeSavedState = Bundle().apply { putBoolean("process_death_recreation", true) }
        val controller = Robolectric.buildActivity(AlarmAlertActivity::class.java, recreateIntent).create(fakeSavedState)
        val activity = controller.get()

        assertFalse("Recreated AlarmAlertActivity must stay open", activity.isFinishing)
        assertTrue(
            "Volume down key must remain blocked after recreation",
            activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN))
        )
        assertTrue(
            "Back key must remain blocked after recreation",
            activity.onKeyDown(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        )

        store.clearSession()
    }

    @Test
    fun onNewIntent_withFreshTriggerAfterDismissal_reopensAlertInsteadOfDroppingAlarm() {
        val context: Context = RuntimeEnvironment.getApplication()
        val firstIntent = AlarmAlertIntentFactory.buildAlertIntent(
            context = context,
            alarmId = 71,
            label = "الأول",
            targetPage = 1,
            startPage = 1,
            pageCount = 1,
            isFreshTrigger = true
        )
        val controller = Robolectric.buildActivity(AlarmAlertActivity::class.java, firstIntent).create().start().resume()
        val activity = controller.get()

        AlarmAlertActivity.isDismissedOrSnoozedGlobally = true

        val secondFreshIntent = AlarmAlertIntentFactory.buildAlertIntent(
            context = context,
            alarmId = 72,
            label = "الثاني",
            targetPage = 10,
            startPage = 10,
            pageCount = 2,
            isFreshTrigger = true
        )
        controller.newIntent(secondFreshIntent)

        assertFalse("Fresh alarm delivered via onNewIntent must not finish early", activity.isFinishing)
        assertFalse("Global dismissed flag must reset on fresh alarm trigger", AlarmAlertActivity.isDismissedOrSnoozedGlobally)
    }
}
