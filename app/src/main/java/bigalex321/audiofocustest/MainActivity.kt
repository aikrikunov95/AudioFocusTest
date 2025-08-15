package bigalex321.audiofocustest

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import bigalex321.audiofocustest.ui.theme.AudioFocusTestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudioFocusTestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        Button(
                            onClick = {
                                setupAlarm(applicationContext)
                            }
                        ) {
                            Text("Setup alarm")
                        }
                        Button(
                            onClick = {
                                doneAlarm()
                                finish()
                            }
                        ) {
                            Text("Done alarm")
                        }
                    }
                }
            }
        }
        addToLockScreen()
    }

    private fun addToLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    private fun setupAlarm(context: Context) {
        val systemAlarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !systemAlarmManager.canScheduleExactAlarms()) {
            Toast.makeText(context, "Need alarm permission", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(context, AlarmService::class.java)
        val pendingIntent = PendingIntent.getForegroundService(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("fromAlarmClockInfo", true)
        }

        val showPendingIntent = PendingIntent.getActivity(
            context,
            0,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        systemAlarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(System.currentTimeMillis()+10000, showPendingIntent),
            pendingIntent
        )

        Log.d("src", "MainActivity alarm setup")
    }

    private fun doneAlarm() {
        val doneIntent = Intent(this, AlarmService::class.java).apply {
            action = "ACTION_DONE"
        }
        startService(doneIntent)
    }
}