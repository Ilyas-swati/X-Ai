package com.example.tools

import android.content.Context
import org.json.JSONObject

/**
 * Modular interface for agent tools that X can invoke during live voice conversations.
 */
interface AgentTool {
    val name: String
    val description: String
    val parameterSchema: JSONObject

    suspend fun execute(context: Context, arguments: JSONObject): ToolExecutionResult
}

data class ToolExecutionResult(
    val success: Boolean,
    val summary: String,
    val data: JSONObject = JSONObject()
)
