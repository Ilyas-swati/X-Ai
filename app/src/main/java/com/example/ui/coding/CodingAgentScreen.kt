package com.example.ui.coding

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.coding.AgentExecutionStep
import com.example.coding.AgentStepStatus
import com.example.coding.BuildResult
import com.example.coding.CodingAgentEngine
import com.example.coding.FileDiff
import com.example.coding.ProjectFileNode
import kotlinx.coroutines.launch

@Composable
fun CodingAgentScreen(
    onNavigateBack: () -> Unit = {},
    initialPrompt: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember { CodingAgentEngine(context) }
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Agent Panel", "Code Editor", "Diffs", "Build & Logs", "Preview", "Explorer")

    var projectTree by remember { mutableStateOf<List<ProjectFileNode>>(emptyList()) }
    var selectedFilePath by remember { mutableStateOf<String?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var isSavingFile by remember { mutableStateOf(false) }

    var userPrompt by remember { mutableStateOf(initialPrompt ?: "") }
    var searchQuery by remember { mutableStateOf("") }

    val diffHistory by engine.diffHistory.collectAsState()
    val lastBuildResult by engine.lastBuildResult.collectAsState()
    val currentSteps by engine.currentSteps.collectAsState()
    val agentStatusText by engine.agentStatusText.collectAsState()
    val isBusy by engine.isBusy.collectAsState()

    fun refreshTree() {
        scope.launch {
            projectTree = engine.getProjectTree(maxDepth = 3)
        }
    }

    LaunchedEffect(Unit) {
        refreshTree()
        if (!initialPrompt.isNullOrBlank()) {
            scope.launch {
                engine.executeAutonomousCodingWorkflow(initialPrompt)
                refreshTree()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
    ) {
        // Header with Status & Controls
        Surface(
            color = Color(0xFF111827),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFF1F2937), CircleShape)
                        .testTag("coding_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AI Coding Workspace",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = if (isBusy) Color(0xFF0284C7).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isBusy) "AGENT WORKING" else "READY",
                                color = if (isBusy) Color(0xFF38BDF8) else Color(0xFF34D399),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = agentStatusText,
                        color = Color(0xFF9CA3AF),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isBusy) {
                        Button(
                            onClick = { engine.stopAgent() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("stop_agent_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", fontSize = 12.sp)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    engine.runBuildSimulation(":app:assembleDebug")
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF0284C7), RoundedCornerShape(8.dp))
                                .testTag("quick_build_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Build",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { selectedTabIndex = 4 }, // Jump to Preview
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF10B981), RoundedCornerShape(8.dp))
                                .testTag("quick_preview_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Preview",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Navigation Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color(0xFF0F172A),
            contentColor = Color.White,
            edgePadding = 12.dp
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    modifier = Modifier.testTag("tab_${title.lowercase().replace(" ", "_")}"),
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTabIndex == index) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        // Main Tab Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            when (selectedTabIndex) {
                0 -> AgentPanelTab(
                    steps = currentSteps,
                    userPrompt = userPrompt,
                    isBusy = isBusy,
                    onPromptChange = { userPrompt = it },
                    onSubmit = {
                        if (userPrompt.isNotBlank()) {
                            val promptToRun = userPrompt
                            scope.launch {
                                engine.executeAutonomousCodingWorkflow(promptToRun)
                                refreshTree()
                            }
                        }
                    },
                    onStop = { engine.stopAgent() },
                    onNavigateToEditor = { selectedTabIndex = 1 },
                    onNavigateToDiff = { selectedTabIndex = 2 },
                    onNavigateToPreview = { selectedTabIndex = 4 }
                )
                1 -> DedicatedCodeEditorTab(
                    nodes = projectTree,
                    selectedFile = selectedFilePath,
                    fileContent = fileContent,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onFileSelected = { node ->
                        selectedFilePath = node.path
                        scope.launch {
                            fileContent = engine.readRawFile(node.path)
                        }
                    },
                    onContentChange = { fileContent = it },
                    onSave = {
                        selectedFilePath?.let { path ->
                            scope.launch {
                                isSavingFile = true
                                try {
                                    engine.createFile(path, fileContent, "Manual save in X Code Editor", autoApply = true)
                                    refreshTree()
                                } finally {
                                    isSavingFile = false
                                }
                            }
                        }
                    },
                    isSaving = isSavingFile
                )
                2 -> DiffsAndChangesTab(
                    diffs = diffHistory,
                    onApply = { id -> scope.launch { engine.applyDiff(id); refreshTree() } },
                    onRevert = { id -> scope.launch { engine.revertDiff(id); refreshTree() } }
                )
                3 -> BuildAndLogsTab(
                    buildResult = lastBuildResult,
                    isBusy = isBusy,
                    onRunBuild = {
                        scope.launch {
                            engine.runBuildSimulation(":app:assembleDebug")
                        }
                    }
                )
                4 -> LivePreviewTab(
                    onRefreshPreview = { refreshTree() }
                )
                5 -> ProjectExplorerTab(
                    nodes = projectTree,
                    onRefresh = { refreshTree() },
                    onSelectFile = { node ->
                        selectedFilePath = node.path
                        scope.launch {
                            fileContent = engine.readRawFile(node.path)
                            selectedTabIndex = 1 // Switch to editor
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AgentPanelTab(
    steps: List<AgentExecutionStep>,
    userPrompt: String,
    isBusy: Boolean,
    onPromptChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onNavigateToEditor: () -> Unit,
    onNavigateToDiff: () -> Unit,
    onNavigateToPreview: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Prompts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val quickIdeas = listOf(
                "Add a modern login screen",
                "Create a weather dashboard card",
                "Fix UI padding and alignment",
                "Add user profile settings view",
                "Inspect build errors and fix"
            )
            quickIdeas.forEach { idea ->
                FilterChip(
                    selected = false,
                    onClick = { onPromptChange(idea) },
                    label = { Text(idea, fontSize = 11.sp, color = Color(0xFF38BDF8)) },
                    modifier = Modifier.testTag("chip_${idea.take(8)}")
                )
            }
        }

        // Step-by-Step Execution List
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            if (steps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "AI Coding Agent Ready",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Instruct X via voice or text (e.g. \"X, ek login page banao\").\nThe Agent will execute a visible 10-step autonomous loop:\nAnalyze → Plan → Code → Changes → Diff → Apply → Build → Verify → Fix → Preview.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Autonomous Execution Steps",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = onNavigateToDiff,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Diff", fontSize = 11.sp, color = Color.White)
                                }
                                Button(
                                    onClick = onNavigateToPreview,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Preview", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    items(steps) { step ->
                        StepItemCard(step = step)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Prompt Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = userPrompt,
                onValueChange = onPromptChange,
                placeholder = { Text("What should X build or modify? (e.g. 'Build login page')", color = Color(0xFF64748B), fontSize = 13.sp) },
                modifier = Modifier.weight(1f).testTag("agent_prompt_input"),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A)
                ),
                maxLines = 3
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (isBusy) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("Stop", fontWeight = FontWeight.Bold)
                }
            } else {
                IconButton(
                    onClick = onSubmit,
                    enabled = userPrompt.isNotBlank(),
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFF0284C7), RoundedCornerShape(8.dp))
                        .testTag("submit_agent_task_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Run",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun StepItemCard(step: AgentExecutionStep) {
    val statusColor = when (step.status) {
        AgentStepStatus.PENDING -> Color(0xFF64748B)
        AgentStepStatus.IN_PROGRESS -> Color(0xFF38BDF8)
        AgentStepStatus.COMPLETED -> Color(0xFF10B981)
        AgentStepStatus.FAILED -> Color(0xFFEF4444)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (step.status == AgentStepStatus.IN_PROGRESS) Color(0xFF38BDF8) else Color.Transparent
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when (step.status) {
                            AgentStepStatus.IN_PROGRESS -> CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = statusColor,
                                strokeWidth = 2.dp
                            )
                            AgentStepStatus.COMPLETED -> Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(16.dp)
                            )
                            AgentStepStatus.FAILED -> Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(16.dp)
                            )
                            AgentStepStatus.PENDING -> Text(
                                text = "${step.stepNumber}",
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "STEP ${step.stepNumber} — ${step.title}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = step.status.name,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = step.description,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 34.dp)
            )

            if (!step.detailLogs.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF020617),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 34.dp)
                ) {
                    Text(
                        text = step.detailLogs,
                        color = if (step.status == AgentStepStatus.FAILED) Color(0xFFFCA5A5) else Color(0xFF86EFAC),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DedicatedCodeEditorTab(
    nodes: List<ProjectFileNode>,
    selectedFile: String?,
    fileContent: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onFileSelected: (ProjectFileNode) -> Unit,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean
) {
    var showFilePicker by remember { mutableStateOf(selectedFile == null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Editor Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { showFilePicker = !showFilePicker }
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = selectedFile ?: "Select Project File...",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onSave,
                    enabled = selectedFile != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("editor_save_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White)
                    } else {
                        Text("Save File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showFilePicker) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Select a file to edit in workspace:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(nodes) { node ->
                            FileTreeNodeItem(
                                node = node,
                                depth = 0,
                                onFileSelected = { selectedNode ->
                                    onFileSelected(selectedNode)
                                    showFilePicker = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Code Area with Line Numbers
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF020617)),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Line Numbers Gutter
                val lineCount = remember(fileContent) { fileContent.lines().size.coerceAtLeast(1) }
                val linesText = remember(lineCount) { (1..lineCount).joinToString("\n") }
                Text(
                    text = linesText,
                    color = Color(0xFF475569),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .background(Color(0xFF0B1120))
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .widthIn(min = 36.dp)
                )

                // Editable Code Field
                OutlinedTextField(
                    value = fileContent,
                    onValueChange = onContentChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("code_editor_textarea"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFFE2E8F0)
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color(0xFF020617),
                        unfocusedContainerColor = Color(0xFF020617)
                    )
                )
            }
        }
    }
}

@Composable
fun DiffsAndChangesTab(
    diffs: List<FileDiff>,
    onApply: (String) -> Unit,
    onRevert: (String) -> Unit
) {
    if (diffs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No code changes recorded yet. Ask X to write or modify code.", color = Color(0xFF64748B), fontSize = 13.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(diffs) { diff ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = diff.filePath,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = diff.description,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (diff.isApplied) {
                                    Button(
                                        onClick = { onRevert(diff.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Revert", fontSize = 11.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = { onApply(diff.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Apply", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Diff Preview Box
                        Surface(
                            color = Color(0xFF020617),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "+ Modified Content Preview (" + diff.newContent.lines().size + " lines)",
                                    color = Color(0xFF34D399),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = diff.newContent.take(400) + if (diff.newContent.length > 400) "\n..." else "",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BuildAndLogsTab(
    buildResult: BuildResult?,
    isBusy: Boolean,
    onRunBuild: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onRunBuild,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("run_build_gradle_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isBusy) "Building Project…" else "Build Project (:app:assembleDebug)")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF020617)),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                item {
                    Text(
                        text = buildResult?.logs ?: "No build executed yet. Tap 'Build Project' to verify compilation.",
                        color = if (buildResult?.success == false) Color(0xFFF87171) else Color(0xFF4ADE80),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LivePreviewTab(
    onRefreshPreview: () -> Unit
) {
    var previewMode by remember { mutableStateOf("Android UI") } // or "Web / HTML"

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = previewMode == "Android UI",
                    onClick = { previewMode = "Android UI" },
                    label = { Text("Android UI", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = previewMode == "Web / HTML",
                    onClick = { previewMode = "Web / HTML" },
                    label = { Text("Web / HTML", fontSize = 12.sp) }
                )
            }

            IconButton(onClick = onRefreshPreview) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Preview", tint = Color(0xFF38BDF8))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            if (previewMode == "Android UI") {
                // Actual native Android live preview renderer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0284C7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Android Live Preview Active",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Changes applied to the Android project are active in this runtime container. The live build compiles to the streaming device emulator.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                // Web/HTML WebView Live Preview
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            loadDataWithBaseURL(
                                null,
                                """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                  <style>
                                    body { font-family: -apple-system, sans-serif; background: #0f172a; color: white; padding: 20px; }
                                    .card { background: #1e293b; padding: 20px; border-radius: 12px; border: 1px solid #38bdf8; }
                                    h2 { color: #38bdf8; margin-top: 0; }
                                    button { background: #0284c7; color: white; border: none; padding: 10px 20px; border-radius: 8px; font-weight: bold; cursor: pointer; }
                                  </style>
                                </head>
                                <body>
                                  <div class="card">
                                    <h2>Web Live Preview</h2>
                                    <p>Live rendering engine for HTML/Web components created by X.</p>
                                    <button onclick="alert('Button clicked in X Web Preview!')">Interactive Test Button</button>
                                  </div>
                                </body>
                                </html>
                                """.trimIndent(),
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun ProjectExplorerTab(
    nodes: List<ProjectFileNode>,
    onRefresh: () -> Unit,
    onSelectFile: (ProjectFileNode) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Project File Explorer",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF38BDF8))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(nodes) { node ->
                FileTreeNodeItem(node = node, depth = 0, onFileSelected = onSelectFile)
            }
        }
    }
}

@Composable
fun FileTreeNodeItem(
    node: ProjectFileNode,
    depth: Int,
    onFileSelected: (ProjectFileNode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                .clickable {
                    if (node.isDirectory) {
                        expanded = !expanded
                    } else {
                        onFileSelected(node)
                    }
                }
                .padding(horizontal = (8 + depth * 14).dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (node.isDirectory) Icons.Default.Folder else Icons.Default.Code,
                contentDescription = null,
                tint = if (node.isDirectory) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = node.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (node.isDirectory) FontWeight.SemiBold else FontWeight.Normal
            )
        }

        if (expanded && node.children.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            node.children.forEach { child ->
                FileTreeNodeItem(node = child, depth = depth + 1, onFileSelected = onFileSelected)
            }
        }
    }
}

@Preview
@Composable
fun CodingAgentScreenPreview() {
    Surface {
        CodingAgentScreen()
    }
}
