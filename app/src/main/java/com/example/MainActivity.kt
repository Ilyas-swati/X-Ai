package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.screen.ScreenUnderstandingManager
import com.example.settings.SecureStorageManager
import com.example.ui.XViewModel
import com.example.ui.XVoiceScreen
import com.example.ui.coding.CodingAgentScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.setup.AiSetupScreen
import com.example.ui.theme.MyApplicationTheme

enum class AppScreen {
  VOICE, CODING, SETTINGS, SETUP
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ScreenUnderstandingManager.setCurrentActivity(this)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          XApp(modifier = Modifier.padding(innerPadding))
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    ScreenUnderstandingManager.setCurrentActivity(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    ScreenUnderstandingManager.setCurrentActivity(null)
  }
}

@Composable
fun XApp(
    modifier: Modifier = Modifier,
    viewModel: XViewModel = viewModel()
) {
  val context = LocalContext.current
  val secureStorage = remember { SecureStorageManager(context) }
  var currentScreen by remember {
    mutableStateOf(
      if (secureStorage.hasValidApiKey()) AppScreen.VOICE else AppScreen.SETUP
    )
  }

  BackHandler(enabled = currentScreen != AppScreen.VOICE && currentScreen != AppScreen.SETUP) {
    currentScreen = AppScreen.VOICE
  }

  var hasMicPermission by remember {
    mutableStateOf(
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    hasMicPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
  }

  LaunchedEffect(Unit) {
    val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
          != PackageManager.PERMISSION_GRANTED) {
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
    if (!hasMicPermission) {
      permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }
  }

  DisposableEffect(context) {
    viewModel.bindService(context)
    onDispose {
      viewModel.unbindService(context)
    }
  }

  val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
  val micAmplitude by viewModel.micAmplitude.collectAsStateWithLifecycle()
  val speakerAmplitude by viewModel.speakerAmplitude.collectAsStateWithLifecycle()
  val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
  val activeToolSummary by viewModel.activeToolSummary.collectAsStateWithLifecycle()
  val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

  when (currentScreen) {
    AppScreen.VOICE -> {
      XVoiceScreen(
          sessionState = sessionState,
          micAmplitude = micAmplitude,
          speakerAmplitude = speakerAmplitude,
          isMuted = isMuted,
          activeToolSummary = activeToolSummary,
          errorMessage = errorMessage,
          hasMicPermission = hasMicPermission,
          onRequestMicPermission = {
            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(permissions.toTypedArray())
          },
          onStartSession = {
            viewModel.startVoiceSession(context)
          },
          onStopSession = {
            viewModel.stopVoiceSession(context)
          },
          onToggleMute = {
            viewModel.toggleMute()
          },
          onInterrupt = {
            viewModel.interruptPlayback()
          },
          onOpenCodingAgent = {
            currentScreen = AppScreen.CODING
          },
          onOpenSettings = {
            currentScreen = AppScreen.SETTINGS
          },
          modifier = modifier
      )
    }
    AppScreen.CODING -> {
      CodingAgentScreen(
          onNavigateBack = {
            currentScreen = AppScreen.VOICE
          },
          modifier = modifier
      )
    }
    AppScreen.SETTINGS -> {
      SettingsScreen(
          onNavigateBack = {
            currentScreen = AppScreen.VOICE
          },
          modifier = modifier
      )
    }
    AppScreen.SETUP -> {
      AiSetupScreen(
          onSetupCompleted = {
            currentScreen = AppScreen.VOICE
            viewModel.startVoiceSession(context)
          },
          onSkipForNow = {
            currentScreen = AppScreen.VOICE
          },
          modifier = modifier
      )
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme { Greeting("Android") }
}

