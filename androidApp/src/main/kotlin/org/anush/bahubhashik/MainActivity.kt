package org.anush.bahubhashik

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import org.anush.bahubhashik.audio.AndroidContext

class MainActivity : ComponentActivity() {

    private val requestMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The shared module reaches for this the first time anyone records,
        // so it has to be set before any screen appears.
        AndroidContext.application = applicationContext

        // Asked up front rather than mid-recording: being interrupted by a
        // system dialog after you've started talking is worse than being
        // asked once at launch.
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMic.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent { App() }
    }
}
