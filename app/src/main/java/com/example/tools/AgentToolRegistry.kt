package com.example.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AgentToolRegistry {

    private val tools = mutableMapOf<String, AgentTool>()

    init {
        register(OpenAppTool())
        register(OpenSettingsTool())
        register(MakeCallTool())
        register(SendMessageTool())
        register(SetReminderAlarmTool())
        register(CreateFileOrNoteTool())
        register(GetDeviceStatusTool())
        register(NavigateHomeTool())
        register(AnalyzeScreenTool())
        register(CodingAgentTool())
        register(OpenWhatsAppTool())
        register(BrowseUrlTool())
        register(ManageFilesTool())
    }

    fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): AgentTool? = tools[name]

    fun getAllTools(): List<AgentTool> = tools.values.toList()

    /**
     * Builds the JSON declaration for Gemini function declarations.
     */
    fun getFunctionDeclarationsJson(): JSONArray {
        val array = JSONArray()
        for (tool in tools.values) {
            val fnObj = JSONObject().apply {
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", tool.parameterSchema)
            }
            array.put(fnObj)
        }
        return array
    }

    suspend fun executeTool(context: Context, name: String, arguments: JSONObject): ToolExecutionResult {
        val tool = getTool(name) ?: return ToolExecutionResult(
            success = false,
            summary = "Tool $name not found"
        )
        return try {
            tool.execute(context, arguments)
        } catch (e: Exception) {
            ToolExecutionResult(
                success = false,
                summary = "Error executing $name: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }
}

class OpenAppTool : AgentTool {
    override val name: String = "open_app"
    override val description: String = "Open an installed Android application by name"
    override val parameterSchema: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("appName", JSONObject().apply {
                put("type", "STRING")
                put("description", "Name of the application to open, e.g., YouTube, Camera, Chrome, Maps, Calculator")
            })
        })
        put("required", JSONArray().apply { put("appName") })
    }

    override suspend fun execute(context: Context, arguments: JSONObject): ToolExecutionResult {
        val appName = arguments.optString("appName", "").trim().lowercase()
        if (appName.isEmpty()) {
            return ToolExecutionResult(false, "No app name provided.")
        }

        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(0)
        var targetPackage: String? = null

        for (info in installedApps) {
            val label = pm.getApplicationLabel(info).toString().lowercase()
            if (label.contains(appName) || info.packageName.lowercase().contains(appName)) {
                targetPackage = info.packageName
                break
            }
        }

        if (targetPackage == null) {
            when {
                appName.contains("camera") -> {
                    val intent = Intent("android.media.action.IMAGE_CAPTURE").apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (intent.resolveActivity(pm) != null) {
                        context.startActivity(intent)
                        return ToolExecutionResult(true, "Camera opened.")
                    }
                }
                appName.contains("browser") || appName.contains("chrome") -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return ToolExecutionResult(true, "Web browser opened.")
                }
            }
            return ToolExecutionResult(false, "App '$appName' was not found on this device.")
        }

        val launchIntent = pm.getLaunchIntentForPackage(targetPackage)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return if (launchIntent != null) {
            context.startActivity(launchIntent)
            ToolExecutionResult(true, "Opened $appName successfully.")
        } else {
            ToolExecutionResult(false, "Could not launch $appName.")
        }
    }
}

class OpenSettingsTool : AgentTool {
    override val name: String = "open_settings"
    override val description: String = "Open device settings or specific settings page"
    override val parameterSchema: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("settingType", JSONObject().apply {
                put("type", "STRING")
                put("description", "Type of setting: wifi, bluetooth, display, sound, battery, or general")
            })
        })
    }

    override suspend fun execute(context: Context, arguments: JSONObject): ToolExecutionResult {
        val settingType = arguments.optString("settingType", "general").lowercase()
        val action = when {
            settingType.contains("wifi") -> Settings.ACTION_WIFI_SETTINGS
            settingType.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS
            settingType.contains("display") -> Settings.ACTION_DISPLAY_SETTINGS
            settingType.contains("sound") || settingType.contains("audio") -> Settings.ACTION_SOUND_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return ToolExecutionResult(true, "Opened $settingType settings.")
    }
}

class MakeCallTool : AgentTool {
    override val name: String = "make_phone_call"
    override val description: String = "Initiate a phone call to a given phone number"
    override val parameterSchema: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("phoneNumber", JSONObject().apply {
                put("type", "STRING")
                put("description", "The phone number to dial")
            })
            put("contactName", JSONObject().apply {
                put("type", "STRING")
                put("description", "Optional contact name")
            })
        })
        put("required", JSONArray().apply { put("phoneNumber") })
    }

    override suspend fun execute(context: Context, arguments: JSONObject): ToolExecutionResult {
        val number = arguments.optString("phoneNumber", "").trim()
        val contact = arguments.optString("contactName", number)
        if (number.isEmpty()) {
            return ToolExecutionResult(false, "Missing phone number.")
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return ToolExecutionResult(true, "Opening dialer for $contact ($number).")
    }
}

class SendMessageTool : AgentTool {
    override val name: String = "send_message"
    override val description: String = "Prepare and open SMS message to a phone number"
    override val parameterSchema: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("phoneNumber", JSONObject().apply {
                put("type", "STRING")
                put("description", "The phone number to message")
            })
            put("messageText", JSONObject().apply {
                put("type", "STRING")
                put("description", "The message content to send")
            })
        })
        put("required", JSONArray().apply {
            put("phoneNumber")
            put("messageText")
        })
    }

    override suspend fun execute(context: Context, arguments: JSONObject): ToolExecutionResult {
        val number = arguments.optString("phoneNumber", "").trim()
        val text = arguments.optString("messageText", "")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return ToolExecutionResult(true, "Prepared message for $number.")
    }
}

class SetReminderAlarmTool : AgentTool {
    override val name: String = "set_reminder_alarm"
    override val description: String = "Set a timer or alarm for a reminder"
    override val parameterSchema: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("title", JSONObject().apply {
                put("type", "STRING")
                put("description", "Label or message for the reminder")
            })
            put("minutes", JSONObject().apply {
                put("type", "INTEGER")
                put("description", "Number of minutes from now for timer")
            })
        })
        put("required", JSONArray().apply { put("title") })
    }

    override suspend fun execute(context: Context, arguments: JSONObject): ToolExecutionResult {
        val title = arguments.optString("title", "Reminder")
        val minutes = arguments.optInt("minutes", 5).coerceAtLeast(1)
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, title)
            putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            ToolExecutionResult(true, "Set timer for $minutes minutes: '$title'.")
        } catch (e: Exception) {
            ToolExecutionResult(false, "Could not set timer on this device.")
        }
    }
}

class CreateFileOrNoteTool : AgentTool {
    override val name: String = "create_note_or_file"
    override val description: String = "Save a note or text file to local storage"
    override val parameterSchema: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("fileName", JSONObject().apply {
                put("type", "STRING")
                put("description", "Name of note or file (e.g., meeting_notes.txt)")
            })
            put("content", JSONObject().apply {
                put("type", "STRING")
                put("description", "Content to write to the file")
            })
        })
        put("required", JSONArray().apply {
            put("fileName")
            put("content")
        })
    }

    override suspend fun execute(context: Context, arguments: JSONObject): ToolExecutionResult {
        val rawName = arguments.optString("fileName", "note.txt")
        val cleanName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val content = arguments.optString("content", "")

        val notesDir = File(context.filesDir, "X_Notes").apply { mkdirs() }
        val targetFile = File(notesDir, cleanName)
        targetFile.writeText(content)

        return ToolExecutionResult(
            true,
            "Saved note to ${targetFile.name} (${content.length} characters)."
        )
    }
}

class GetDeviceStatusTool : AgentTool {
    override val name: String = "get_device_status"
    override val description: String = "Get device battery level and power state"
    override val parameterSchema: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject())
    }

    override suspend fun execute(context: Context, arguments: JSONObject): ToolExecutionResult {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = bm?.isCharging == true

        val summary = "Battery is at $batteryPct% ${if (isCharging) "(charging)" else "(discharging)"}."
        val data = JSONObject().apply {
            put("batteryPercent", batteryPct)
            put("isCharging", isCharging)
        }
        return ToolExecutionResult(true, summary, data)
    }
}

class NavigateHomeTool : AgentTool {
    override val name: String = "go_home"
    override val description: String = "Navigate to Android home screen"
    override val parameterSchema: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject())
    }

    override suspend fun execute(context: Context, arguments: JSONObject): ToolExecutionResult {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return ToolExecutionResult(true, "Navigated to home screen.")
    }
}

class AnalyzeScreenTool : AgentTool {
    override val name: String = "analyze_screen"
    override val description: String = "Capture and visually understand what is currently displayed on the user's Android screen"
    override val parameterSchema: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("query", JSONObject().apply {
                put("type", "STRING")
                put("description", "Specific question about the screen, e.g., 'What buttons are visible?' or 'Summarize this page'")
            })
        })
    }

    override suspend fun execute(context: Context, arguments: JSONObject): ToolExecutionResult {
        val query = arguments.optString("query", "What is visible on the screen?")
        val result = com.example.screen.ScreenUnderstandingManager.analyzeScreen(query)
        return ToolExecutionResult(
            success = result.success,
            summary = result.summary
        )
    }
}

class CodingAgentTool : AgentTool {
    override val name: String = "coding_agent_task"
    override val description: String = "Execute software development tasks on the Android codebase: inspect structure, search code, read/edit files, fix bugs, and run builds"
    override val parameterSchema: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("action", JSONObject().apply {
                put("type", "STRING")
                put("description", "Action to perform: 'inspect_structure', 'search_code', 'read_file', 'edit_file', 'create_file', 'run_build', 'solve_task'")
            })
            put("filePath", JSONObject().apply {
                put("type", "STRING")
                put("description", "Relative path to target file (e.g. app/src/main/java/com/example/MainActivity.kt)")
            })
            put("query", JSONObject().apply {
                put("type", "STRING")
                put("description", "Search query or high-level coding request")
            })
            put("targetContent", JSONObject().apply {
                put("type", "STRING")
                put("description", "Exact code block to replace when editing")
            })
            put("replacementContent", JSONObject().apply {
                put("type", "STRING")
                put("description", "Replacement code content")
            })
        })
        put("required", JSONArray().apply { put("action") })
    }

    override suspend fun execute(context: Context, arguments: JSONObject): ToolExecutionResult {
        val engine = com.example.coding.CodingAgentEngine(context)
        val action = arguments.optString("action", "inspect_structure")
        val filePath = arguments.optString("filePath", "")
        val query = arguments.optString("query", "")
        val targetContent = arguments.optString("targetContent", "")
        val replacementContent = arguments.optString("replacementContent", "")

        return when (action) {
            "inspect_structure" -> {
                val tree = engine.getProjectTree(maxDepth = 2)
                val summary = "Project contains ${tree.size} root entries: " + tree.take(10).joinToString { it.name }
                ToolExecutionResult(true, summary)
            }
            "search_code" -> {
                val matches = engine.searchCode(query)
                if (matches.isEmpty()) {
                    ToolExecutionResult(true, "No code matches found for '$query'.")
                } else {
                    ToolExecutionResult(true, "Found ${matches.size} matches: \n" + matches.take(5).joinToString("\n"))
                }
            }
            "read_file" -> {
                val content = engine.readFile(filePath)
                ToolExecutionResult(true, "Read $filePath:\n" + content.take(600))
            }
            "edit_file" -> {
                try {
                    val diff = engine.editFile(filePath, targetContent, replacementContent)
                    ToolExecutionResult(true, "Successfully updated $filePath.")
                } catch (e: Exception) {
                    ToolExecutionResult(false, "Failed to edit $filePath: ${e.message}")
                }
            }
            "create_file" -> {
                try {
                    engine.createFile(filePath, replacementContent)
                    ToolExecutionResult(true, "Created file $filePath successfully.")
                } catch (e: Exception) {
                    ToolExecutionResult(false, "Failed to create $filePath: ${e.message}")
                }
            }
            "run_build" -> {
                val res = engine.runBuildSimulation()
                ToolExecutionResult(res.success, if (res.success) "Build passed successfully." else "Build failed: ${res.errorSummary}")
            }
            "solve_task", "execute_workflow" -> {
                val promptToSolve = if (query.isNotBlank()) query else arguments.optString("prompt", "Analyze and improve project")
                val result = engine.executeAutonomousCodingWorkflow(promptToSolve)
                if (result.success) {
                    val summary = "Successfully finished autonomous coding workflow for: '$promptToSolve'.\n" +
                            "Plan: ${result.plan}\n" +
                            "Files touched: ${result.touchedFiles.joinToString()}\n" +
                            "Build verification: ${if (result.buildPassed) "PASSED" else "NEEDS FIX"}"
                    ToolExecutionResult(true, summary)
                } else {
                    ToolExecutionResult(false, "Coding workflow failed: ${result.message}")
                }
            }
            else -> ToolExecutionResult(false, "Unknown coding action: $action")
        }
    }
}

class OpenWhatsAppTool : AgentTool {
    override val name: String = "open_whatsapp"
    override val description: String = "Open WhatsApp or start a chat with a specific phone number"
    override val parameterSchema: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("phoneNumber", JSONObject().apply {
                put("type", "STRING")
                put("description", "Phone number with country code (e.g. +923001234567)")
            })
            put("message", JSONObject().apply {
                put("type", "STRING")
                put("description", "Optional pre-filled message text")
            })
        })
    }

    override suspend fun execute(context: Context, arguments: JSONObject): ToolExecutionResult {
        val phone = arguments.optString("phoneNumber", "").replace(Regex("[^0-9]"), "")
        val message = Uri.encode(arguments.optString("message", ""))
        val uri = if (phone.isNotEmpty()) {
            Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=$message")
        } else {
            Uri.parse("whatsapp://send")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            ToolExecutionResult(true, "Opened WhatsApp" + if (phone.isNotEmpty()) " for $phone" else "")
        } catch (e: Exception) {
            ToolExecutionResult(false, "WhatsApp is not installed on this device.")
        }
    }
}

class BrowseUrlTool : AgentTool {
    override val name: String = "browse_url"
    override val description: String = "Open a webpage or web address in the device browser"
    override val parameterSchema: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("url", JSONObject().apply {
                put("type", "STRING")
                put("description", "URL to browse (e.g. https://github.com)")
            })
        })
        put("required", JSONArray().apply { put("url") })
    }

    override suspend fun execute(context: Context, arguments: JSONObject): ToolExecutionResult {
        var url = arguments.optString("url", "").trim()
        if (url.isEmpty()) return ToolExecutionResult(false, "No URL specified.")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return ToolExecutionResult(true, "Opened $url in browser.")
    }
}

class ManageFilesTool : AgentTool {
    override val name: String = "manage_files"
    override val description: String = "List, create, or read files in the application's workspace storage"
    override val parameterSchema: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("action", JSONObject().apply {
                put("type", "STRING")
                put("description", "Operation: 'list_files', 'read_file', 'create_folder'")
            })
            put("path", JSONObject().apply {
                put("type", "STRING")
                put("description", "Relative folder or file name")
            })
        })
        put("required", JSONArray().apply { put("action") })
    }

    override suspend fun execute(context: Context, arguments: JSONObject): ToolExecutionResult {
        val action = arguments.optString("action", "list_files")
        val targetPath = arguments.optString("path", "")
        val baseDir = File(context.filesDir, "workspace").apply { mkdirs() }
        val target = if (targetPath.isEmpty()) baseDir else File(baseDir, targetPath)

        return when (action) {
            "list_files" -> {
                val files = target.listFiles()?.map { (if (it.isDirectory) "[DIR] " else "[FILE] ") + it.name } ?: emptyList()
                ToolExecutionResult(true, "Files: " + if (files.isEmpty()) "Empty directory" else files.joinToString(", "))
            }
            "create_folder" -> {
                target.mkdirs()
                ToolExecutionResult(true, "Folder created at ${target.name}.")
            }
            "read_file" -> {
                if (target.exists()) {
                    ToolExecutionResult(true, "Content:\n" + target.readText().take(500))
                } else {
                    ToolExecutionResult(false, "File does not exist: $targetPath")
                }
            }
            else -> ToolExecutionResult(false, "Unsupported file operation: $action")
        }
    }
}

