package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.coding.CodingAgentEngine
import com.example.settings.XSettingsManager
import com.example.tools.AgentToolRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("X", appName)
  }

  @Test
  fun `verify agent tool registry has all tools registered`() {
    val tools = AgentToolRegistry.getAllTools()
    assertTrue(tools.size >= 13)
    assertNotNull(AgentToolRegistry.getTool("open_app"))
    assertNotNull(AgentToolRegistry.getTool("open_settings"))
    assertNotNull(AgentToolRegistry.getTool("make_phone_call"))
    assertNotNull(AgentToolRegistry.getTool("send_message"))
    assertNotNull(AgentToolRegistry.getTool("set_reminder_alarm"))
    assertNotNull(AgentToolRegistry.getTool("create_note_or_file"))
    assertNotNull(AgentToolRegistry.getTool("get_device_status"))
    assertNotNull(AgentToolRegistry.getTool("go_home"))
    assertNotNull(AgentToolRegistry.getTool("analyze_screen"))
    assertNotNull(AgentToolRegistry.getTool("coding_agent_task"))
    assertNotNull(AgentToolRegistry.getTool("open_whatsapp"))
    assertNotNull(AgentToolRegistry.getTool("browse_url"))
    assertNotNull(AgentToolRegistry.getTool("manage_files"))
  }

  @Test
  fun `execute device status tool successfully`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val result = AgentToolRegistry.executeTool(context, "get_device_status", JSONObject())
    assertTrue(result.success)
    assertTrue(result.summary.contains("Battery"))
  }

  @Test
  fun `execute coding agent file operations`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = CodingAgentEngine(context)
    val root = engine.getProjectRoot()
    assertTrue(root.exists())

    val diff = engine.createFile("test_sample.txt", "Hello X Coding Agent", "Unit test file")
    assertTrue(diff.isApplied)
    val content = engine.readFile("test_sample.txt")
    assertTrue(content.contains("Hello X Coding Agent"))

    val reverted = engine.revertDiff(diff.id)
    assertTrue(reverted)
  }

  @Test
  fun `verify settings manager persistence and female voice default`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val settingsManager = XSettingsManager(context)
    // Default voice is Aoede (Natural female voice)
    assertEquals("Aoede", settingsManager.settingsState.value.selectedVoice)
    assertTrue(settingsManager.settingsState.value.selectedLanguage.contains("Auto Detect"))
    assertTrue(settingsManager.settingsState.value.wakeWordEnabled)
    assertTrue(settingsManager.settingsState.value.backgroundListeningEnabled)

    settingsManager.updateSettings { it.copy(selectedVoice = "Kore") }
    assertEquals("Kore", settingsManager.settingsState.value.selectedVoice)
  }

  @Test
  fun `verify secure storage manager encrypts and masks api credentials`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val secureStorage = com.example.settings.SecureStorageManager(context)
    secureStorage.clearApiKey()
    assertTrue(!secureStorage.hasValidApiKey())

    secureStorage.saveApiKey("AIzaSyFakeKeyForTesting123456789")
    assertTrue(secureStorage.hasValidApiKey())
    val masked = secureStorage.getMaskedApiKey()
    assertTrue(masked.contains("••••"))
    assertTrue(masked.startsWith("AIza"))
    assertTrue(masked.endsWith("6789"))
  }
}

