package com.rady.worktracker.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import com.rady.worktracker.BuildConfig
import com.rady.worktracker.R
import com.rady.worktracker.TrackerViewModel
import com.rady.worktracker.data.AppFont
import com.rady.worktracker.data.Settings
import com.rady.worktracker.data.TimerEvent
import com.rady.worktracker.timer.Sounds
import java.time.LocalDate
import java.time.LocalTime

// Android versions of the few things that differ between the phone and Windows apps.

fun appVersion(): String = BuildConfig.VERSION_NAME

/** The Ydar Track logo: a head built like a construction project. */
@Composable
fun LogoMark(size: Dp) {
    Image(painterResource(R.drawable.logo_mark), contentDescription = "Ydar Track logo",
        modifier = Modifier.height(size).aspectRatio(520f / 631f))
}

private fun family(r400: Int, r500: Int, r600: Int, r700: Int) = FontFamily(
    Font(r400, FontWeight.Normal),
    Font(r500, FontWeight.Medium),
    Font(r600, FontWeight.SemiBold),
    Font(r700, FontWeight.Bold),
)

fun fontFamilyFor(font: AppFont): FontFamily = when (font) {
    AppFont.GOOGLE_SANS -> family(R.font.googlesans_400, R.font.googlesans_500, R.font.googlesans_600, R.font.googlesans_700)
    AppFont.INTER -> family(R.font.inter_400, R.font.inter_500, R.font.inter_600, R.font.inter_700)
    AppFont.ROBOTO -> family(R.font.roboto_400, R.font.roboto_500, R.font.roboto_600, R.font.roboto_700)
    AppFont.MONTSERRAT -> family(R.font.montserrat_400, R.font.montserrat_500, R.font.montserrat_600, R.font.montserrat_700)
    AppFont.OPEN_SANS -> family(R.font.opensans_400, R.font.opensans_500, R.font.opensans_600, R.font.opensans_700)
    AppFont.WORK_SANS -> family(R.font.worksans_400, R.font.worksans_500, R.font.worksans_600, R.font.worksans_700)
    AppFont.LATO -> family(R.font.lato_400, R.font.lato_500, R.font.lato_600, R.font.lato_700)
    AppFont.JOSEFIN_SANS -> family(R.font.josefinsans_400, R.font.josefinsans_500, R.font.josefinsans_600, R.font.josefinsans_700)
}

/** Time formatter that follows the phone's 12/24-hour setting. */
@Composable
fun rememberTimeFormat(): java.text.DateFormat {
    val context = LocalContext.current
    return remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
}

/** Android's own date and time pickers. */
class Pickers(private val context: Context) {
    fun date(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            context,
            { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
            initial.year, initial.monthValue - 1, initial.dayOfMonth,
        ).show()
    }

    fun time(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
            initial.hour, initial.minute,
            android.text.format.DateFormat.is24HourFormat(context),
        ).show()
    }
}

@Composable
fun rememberPickers(): Pickers {
    val context = LocalContext.current
    return remember(context) { Pickers(context) }
}

class SoundFx(private val context: Context) {
    fun event(e: TimerEvent) = Sounds.play(context, Sounds.forEvent(e))
    fun rank(level: Int) = Sounds.play(context, Sounds.forRank(level))
}

@Composable
fun rememberSoundFx(): SoundFx {
    val context = LocalContext.current
    return remember(context) { SoundFx(context.applicationContext) }
}

/** After the first timer starts, ask once for permission to show the timer notification. */
@Composable
fun rememberOnTimerStarted(vm: TrackerViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshTimerNotification() }
    return {
        val prefs = context.getSharedPreferences("track", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("asked_notif", false)
        ) {
            prefs.edit().putBoolean("asked_notif", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Settings › backup: Google Drive on the phone. */
@Composable
fun BackupSection(settings: Settings, vm: TrackerViewModel) = DriveBackupCard(settings, vm)
