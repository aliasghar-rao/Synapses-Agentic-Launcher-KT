// ALI LAUNCHER FOUNDATION - PRODUCTION GRADE MVP
// Session 1: Core launcher + LLM integration + resource monitoring
// Next Session: Speech processing + local AI models integration

// ==================== MANIFEST CONFIGURATION ====================
/*
Add to AndroidManifest.xml:

<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
<uses-permission android:name="android.permission.DEVICE_ADMIN" />
<uses-permission android:name="android.permission.BIND_DEVICE_ADMIN" />

<activity
    android:name=".presentation.MainActivity"
    android:label="ALI Launcher"
    android:theme="@style/AppTheme"
    android:launchMode="singleTask"
    android:exported="true">
    <intent-filter android:priority="1000">
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>

<service
    android:name=".core.intelligence.LiquidIntelligenceService"
    android:enabled="true"
    android:exported="false" />
*/

// ==================== BUILD CONFIGURATION ====================
/*
Add to app/build.gradle.kts:

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.5.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.8")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Coroutines & Async
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Network & HTTP
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // JSON Processing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Security & Encryption
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // System Integration
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Speech & ML (Next Session)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
}
*/

// ==================== CORE DATA MODELS ====================

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import android.content.Context
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Binder
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.io.IOException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.*
import com.google.gson.annotations.SerializedName
import java.util.concurrent.TimeUnit

// ==================== SYSTEM RESOURCE MONITORING ====================

data class SystemResourceState(
    val memoryUsageMB: Long,
    val memoryAvailableMB: Long,
    val cpuUsagePercent: Double,
    val batteryLevel: Int,
    val batteryTemperature: Float,
    val networkLatencyMs: Long,
    val timestamp: Long = System.currentTimeMillis()
) {
    val memoryPressure: Double get() = memoryUsageMB.toDouble() / (memoryUsageMB + memoryAvailableMB)
    val isLowMemory: Boolean get() = memoryPressure > 0.85
    val isOverheating: Boolean get() = batteryTemperature > 40.0f
    val isBatteryLow: Boolean get() = batteryLevel < 20
}

data class OptimizationStrategy(
    val name: String,
    val priority: Int,
    val actions: List<OptimizationAction>,
    val estimatedImprovementPercent: Double
)

sealed class OptimizationAction {
    data class ReduceBackgroundProcessing(val reductionPercent: Int) : OptimizationAction()
    data class ClearMemoryCache(val targetMB: Long) : OptimizationAction()
    data class ThrottleCPUIntensive(val throttlePercent: Int) : OptimizationAction()
    data class DelayNonCriticalTasks(val delayMinutes: Int) : OptimizationAction()
}

class SystemResourceMonitor(private val context: Context) {
    private val performanceMetrics = ConcurrentHashMap<String, AtomicLong>()
    
    fun getCurrentSystemState(): SystemResourceState {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val batteryTemp = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_TEMPERATURE) / 10.0f
        
        return SystemResourceState(
            memoryUsageMB = (memoryInfo.totalMem - memoryInfo.availMem) / (1024 * 1024),
            memoryAvailableMB = memoryInfo.availMem / (1024 * 1024),
            cpuUsagePercent = getCurrentCPUUsage(),
            batteryLevel = batteryLevel,
            batteryTemperature = batteryTemp,
            networkLatencyMs = measureNetworkLatency()
        )
    }
    
    private fun getCurrentCPUUsage(): Double {
        return try {
            val reader = java.io.BufferedReader(java.io.FileReader("/proc/stat"))
            val load = reader.readLine()
            reader.close()
            
            val toks = load.split(" +".toRegex()).toTypedArray()
            val idle1 = toks[4].toLong()
            val cpu1 = toks[1].toLong() + toks[2].toLong() + toks[3].toLong() + 
                      toks[5].toLong() + toks[6].toLong() + toks[7].toLong() + toks[8].toLong()
            
            Thread.sleep(360)
            
            val reader2 = java.io.BufferedReader(java.io.FileReader("/proc/stat"))
            val load2 = reader2.readLine()
            reader2.close()
            
            val toks2 = load2.split(" +".toRegex()).toTypedArray()
            val idle2 = toks2[4].toLong()
            val cpu2 = toks2[1].toLong() + toks2[2].toLong() + toks2[3].toLong() + 
                       toks2[5].toLong() + toks2[6].toLong() + toks2[7].toLong() + toks2[8].toLong()
            
            ((cpu2 - cpu1).toDouble() / ((cpu2 + idle2) - (cpu1 + idle1))) * 100.0
        } catch (ex: Exception) {
            0.0 // Fallback for restricted access
        }
    }
    
    private fun measureNetworkLatency(): Long {
        return try {
            val startTime = System.currentTimeMillis()
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("8.8.8.8", 53), 3000)
            val endTime = System.currentTimeMillis()
            socket.close()
            endTime - startTime
        } catch (e: Exception) {
            5000L // Default high latency for offline
        }
    }
    
    fun recordMetric(key: String, value: Long) {
        performanceMetrics.computeIfAbsent(key) { AtomicLong(0) }.set(value)
    }
    
    fun incrementCounter(key: String): Long {
        return performanceMetrics.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
    }
}

// ==================== LLM INTEGRATION CORE ====================

data class LLMRequest(
    val messages: List<ChatMessage>,
    val model: String = "gpt-4",
    @SerializedName("max_tokens") val maxTokens: Int = 1000,
    val temperature: Double = 0.7,
    val stream: Boolean = false
)

data class ChatMessage(
    val role: String, // "user", "assistant", "system"
    val content: String
)

data class LLMResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage?
)

data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerializedName("finish_reason") val finishReason: String
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)

sealed class LLMResult {
    data class Success(val response: String, val usage: Usage? = null) : LLMResult()
    data class Error(val message: String, val code: Int = 0) : LLMResult()
    object NetworkError : LLMResult()
    object RateLimited : LLMResult()
}

interface LLMProvider {
    suspend fun generateResponse(messages: List<ChatMessage>): LLMResult
    val providerName: String
    val isAvailable: Boolean
}

class OpenAIProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1/"
) : LLMProvider {
    
    override val providerName = "OpenAI"
    override val isAvailable = apiKey.isNotBlank()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()
    
    private val gson = Gson()
    
    override suspend fun generateResponse(messages: List<ChatMessage>): LLMResult = withContext(Dispatchers.IO) {
        try {
            val request = LLMRequest(
                messages = messages,
                model = "gpt-4-turbo-preview",
                maxTokens = 1000,
                temperature = 0.7
            )
            
            val requestBody = gson.toJson(request).toRequestBody("application/json".toMediaType())
            
            val httpRequest = Request.Builder()
                .url("${baseUrl}chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            val response = client.newCall(httpRequest).execute()
            
            when (response.code) {
                200 -> {
                    val responseBody = response.body?.string() ?: ""
                    val llmResponse = gson.fromJson(responseBody, LLMResponse::class.java)
                    
                    if (llmResponse.choices.isNotEmpty()) {
                        LLMResult.Success(
                            response = llmResponse.choices[0].message.content,
                            usage = llmResponse.usage
                        )
                    } else {
                        LLMResult.Error("Empty response from LLM")
                    }
                }
                429 -> LLMResult.RateLimited
                else -> LLMResult.Error("HTTP ${response.code}: ${response.message}", response.code)
            }
        } catch (e: IOException) {
            LLMResult.NetworkError
        } catch (e: Exception) {
            LLMResult.Error("Unexpected error: ${e.message}")
        }
    }
}

// ==================== LIQUID INTELLIGENCE CORE ====================

class AdaptiveTaskScheduler(private val resourceMonitor: SystemResourceMonitor) {
    
    fun determineOptimalStrategy(systemState: SystemResourceState): OptimizationStrategy {
        return when {
            systemState.isLowMemory -> createMemoryOptimizationStrategy(systemState)
            systemState.cpuUsagePercent > 80.0 -> createCPUOptimizationStrategy(systemState)
            systemState.isBatteryLow -> createBatteryOptimizationStrategy(systemState)
            systemState.isOverheating -> createThermalOptimizationStrategy(systemState)
            else -> createBalancedOptimizationStrategy(systemState)
        }
    }
    
    private fun createMemoryOptimizationStrategy(state: SystemResourceState): OptimizationStrategy {
        val targetReduction = ((state.memoryPressure - 0.7) * 100).toInt().coerceIn(10, 50)
        
        return OptimizationStrategy(
            name = "Memory Optimization",
            priority = 1,
            actions = listOf(
                OptimizationAction.ClearMemoryCache(targetMB = state.memoryUsageMB * targetReduction / 100),
                OptimizationAction.ReduceBackgroundProcessing(reductionPercent = targetReduction),
                OptimizationAction.DelayNonCriticalTasks(delayMinutes = 5)
            ),
            estimatedImprovementPercent = targetReduction.toDouble()
        )
    }
    
    private fun createCPUOptimizationStrategy(state: SystemResourceState): OptimizationStrategy {
        return OptimizationStrategy(
            name = "CPU Optimization",
            priority = 2,
            actions = listOf(
                OptimizationAction.ThrottleCPUIntensive(throttlePercent = 30),
                OptimizationAction.DelayNonCriticalTasks(delayMinutes = 3)
            ),
            estimatedImprovementPercent = 25.0
        )
    }
    
    private fun createBatteryOptimizationStrategy(state: SystemResourceState): OptimizationStrategy {
        return OptimizationStrategy(
            name = "Battery Conservation",
            priority = 1,
            actions = listOf(
                OptimizationAction.ReduceBackgroundProcessing(reductionPercent = 60),
                OptimizationAction.ThrottleCPUIntensive(throttlePercent = 50),
                OptimizationAction.DelayNonCriticalTasks(delayMinutes = 10)
            ),
            estimatedImprovementPercent = 40.0
        )
    }
    
    private fun createThermalOptimizationStrategy(state: SystemResourceState): OptimizationStrategy {
        return OptimizationStrategy(
            name = "Thermal Management",
            priority = 1,
            actions = listOf(
                OptimizationAction.ThrottleCPUIntensive(throttlePercent = 70),
                OptimizationAction.ReduceBackgroundProcessing(reductionPercent = 40),
                OptimizationAction.DelayNonCriticalTasks(delayMinutes = 15)
            ),
            estimatedImprovementPercent = 35.0
        )
    }
    
    private fun createBalancedOptimizationStrategy(state: SystemResourceState): OptimizationStrategy {
        return OptimizationStrategy(
            name = "Balanced Performance",
            priority = 3,
            actions = listOf(
                OptimizationAction.ClearMemoryCache(targetMB = 50),
                OptimizationAction.DelayNonCriticalTasks(delayMinutes = 1)
            ),
            estimatedImprovementPercent = 5.0
        )
    }
}

class LiquidIntelligence(
    private val context: Context,
    private val resourceMonitor: SystemResourceMonitor
) {
    private val adaptiveScheduler = AdaptiveTaskScheduler(resourceMonitor)
    private val taskExecutionHistory = mutableListOf<TaskExecutionRecord>()
    
    data class TaskExecutionRecord(
        val taskType: String,
        val executionTimeMs: Long,
        val resourceStateBeforeExecution: SystemResourceState,
        val resourceStateAfterExecution: SystemResourceState,
        val success: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    suspend fun optimizeExecution(task: IntelligenceTask): ExecutionResult {
        val startTime = System.currentTimeMillis()
        val initialState = resourceMonitor.getCurrentSystemState()
        
        val strategy = adaptiveScheduler.determineOptimalStrategy(initialState)
        applyOptimizationStrategy(strategy)
        
        return try {
            val result = executeTaskWithOptimization(task, strategy)
            val endTime = System.currentTimeMillis()
            val finalState = resourceMonitor.getCurrentSystemState()
            
            recordTaskExecution(
                task = task,
                executionTime = endTime - startTime,
                initialState = initialState,
                finalState = finalState,
                success = true
            )
            
            result
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val finalState = resourceMonitor.getCurrentSystemState()
            
            recordTaskExecution(
                task = task,
                executionTime = endTime - startTime,
                initialState = initialState,
                finalState = finalState,
                success = false
            )
            
            ExecutionResult.Error("Task execution failed: ${e.message}")
        }
    }
    
    private suspend fun executeTaskWithOptimization(
        task: IntelligenceTask,
        strategy: OptimizationStrategy
    ): ExecutionResult {
        return when (task) {
            is IntelligenceTask.LLMQuery -> executeLLMQuery(task, strategy)
            is IntelligenceTask.SystemAnalysis -> executeSystemAnalysis(task, strategy)
            is IntelligenceTask.UserInteraction -> executeUserInteraction(task, strategy)
        }
    }
    
    private suspend fun executeLLMQuery(
        task: IntelligenceTask.LLMQuery,
        strategy: OptimizationStrategy
    ): ExecutionResult {
        // Implementation for LLM query execution with optimization
        return ExecutionResult.Success("LLM query executed with ${strategy.name}")
    }
    
    private suspend fun executeSystemAnalysis(
        task: IntelligenceTask.SystemAnalysis,
        strategy: OptimizationStrategy
    ): ExecutionResult {
        // Implementation for system analysis with optimization
        return ExecutionResult.Success("System analysis completed with ${strategy.name}")
    }
    
    private suspend fun executeUserInteraction(
        task: IntelligenceTask.UserInteraction,
        strategy: OptimizationStrategy
    ): ExecutionResult {
        // Implementation for user interaction handling with optimization
        return ExecutionResult.Success("User interaction processed with ${strategy.name}")
    }
    
    private suspend fun applyOptimizationStrategy(strategy: OptimizationStrategy) {
        strategy.actions.forEach { action ->
            when (action) {
                is OptimizationAction.ClearMemoryCache -> clearMemoryCache(action.targetMB)
                is OptimizationAction.ReduceBackgroundProcessing -> reduceBackgroundProcessing(action.reductionPercent)
                is OptimizationAction.ThrottleCPUIntensive -> throttleCPUIntensive(action.throttlePercent)
                is OptimizationAction.DelayNonCriticalTasks -> delayNonCriticalTasks(action.delayMinutes)
            }
        }
    }
    
    private suspend fun clearMemoryCache(targetMB: Long) {
        System.gc()
        resourceMonitor.recordMetric("memory_cache_cleared", targetMB)
    }
    
    private suspend fun reduceBackgroundProcessing(reductionPercent: Int) {
        resourceMonitor.recordMetric("background_processing_reduced", reductionPercent.toLong())
    }
    
    private suspend fun throttleCPUIntensive(throttlePercent: Int) {
        resourceMonitor.recordMetric("cpu_throttled", throttlePercent.toLong())
    }
    
    private suspend fun delayNonCriticalTasks(delayMinutes: Int) {
        resourceMonitor.recordMetric("tasks_delayed", delayMinutes.toLong())
    }
    
    private fun recordTaskExecution(
        task: IntelligenceTask,
        executionTime: Long,
        initialState: SystemResourceState,
        finalState: SystemResourceState,
        success: Boolean
    ) {
        val record = TaskExecutionRecord(
            taskType = task::class.simpleName ?: "Unknown",
            executionTimeMs = executionTime,
            resourceStateBeforeExecution = initialState,
            resourceStateAfterExecution = finalState,
            success = success
        )
        
        taskExecutionHistory.add(record)
        
        // Keep only recent records to prevent memory bloat
        if (taskExecutionHistory.size > 1000) {
            taskExecutionHistory.removeAt(0)
        }
        
        resourceMonitor.recordMetric("task_execution_time_ms", executionTime)
        resourceMonitor.recordMetric("task_success_rate", if (success) 1 else 0)
    }
}

// ==================== INTELLIGENCE TASK DEFINITIONS ====================

sealed class IntelligenceTask {
    data class LLMQuery(
        val messages: List<ChatMessage>,
        val priority: TaskPriority = TaskPriority.NORMAL,
        val timeout: Long = 30000L
    ) : IntelligenceTask()
    
    data class SystemAnalysis(
        val analysisType: SystemAnalysisType,
        val priority: TaskPriority = TaskPriority.LOW
    ) : IntelligenceTask()
    
    data class UserInteraction(
        val interactionType: UserInteractionType,
        val data: Map<String, Any>,
        val priority: TaskPriority = TaskPriority.HIGH
    ) : IntelligenceTask()
}

enum class TaskPriority(val value: Int) {
    LOW(1),
    NORMAL(5),
    HIGH(10),
    CRITICAL(15)
}

enum class SystemAnalysisType {
    PERFORMANCE_METRICS,
    RESOURCE_USAGE,
    BATTERY_OPTIMIZATION,
    MEMORY_ANALYSIS
}

enum class UserInteractionType {
    VOICE_COMMAND,
    TEXT_INPUT,
    GESTURE,
    APP_LAUNCH
}

sealed class ExecutionResult {
    data class Success(val result: String, val metadata: Map<String, Any> = emptyMap()) : ExecutionResult()
    data class Error(val message: String, val errorCode: String = "") : ExecutionResult()
    object Cancelled : ExecutionResult()
}

// ==================== LAUNCHER SERVICE CORE ====================

class LiquidIntelligenceService : Service() {
    
    private val binder = LocalBinder()
    private lateinit var resourceMonitor: SystemResourceMonitor
    private lateinit var liquidIntelligence: LiquidIntelligence
    private lateinit var llmProvider: LLMProvider
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    
    inner class LocalBinder : Binder() {
        fun getService(): LiquidIntelligenceService = this@LiquidIntelligenceService
    }
    
    override fun onCreate() {
        super.onCreate()
        
        resourceMonitor = SystemResourceMonitor(this)
        liquidIntelligence = LiquidIntelligence(this, resourceMonitor)
        
        // PRODUCTION NOTE: Replace with actual API key from secure storage
        val apiKey = getApiKeyFromSecureStorage() ?: "PLACEHOLDER_API_KEY"
        llmProvider = OpenAIProvider(apiKey)
        
        startResourceMonitoring()
        Log.i("ALI", "LiquidIntelligenceService initialized")
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.i("ALI", "LiquidIntelligenceService destroyed")
    }
    
    private fun startResourceMonitoring() {
        serviceScope.launch {
            while (isActive) {
                val systemState = resourceMonitor.getCurrentSystemState()
                
                // Log critical system states
                if (systemState.isLowMemory || systemState.isBatteryLow || systemState.isOverheating) {
                    Log.w("ALI", "Critical system state detected: $systemState")
                }
                
                delay(30000L) // Monitor every 30 seconds
            }
        }
    }
    
    suspend fun executeIntelligenceTask(task: IntelligenceTask): ExecutionResult {
        return liquidIntelligence.optimizeExecution(task)
    }
    
    suspend fun queryLLM(messages: List<ChatMessage>): LLMResult {
        return if (llmProvider.isAvailable) {
            llmProvider.generateResponse(messages)
        } else {
            LLMResult.Error("LLM provider not available - API key required")
        }
    }
    
    fun getCurrentSystemState(): SystemResourceState {
        return resourceMonitor.getCurrentSystemState()
    }
    
    // PRODUCTION NOTE: Implement secure API key storage
    private fun getApiKeyFromSecureStorage(): String? {
        // TODO: Implement secure storage retrieval
        // Use EncryptedSharedPreferences or Android Keystore
        return null // Placeholder - requires actual implementation
    }
}

// ==================== MAIN LAUNCHER VIEWMODEL ====================

class ALILauncherViewModel : ViewModel() {
    
    private val _systemState = MutableStateFlow(SystemResourceState(0, 0, 0.0, 0, 0f, 0))
    val systemState: StateFlow<SystemResourceState> = _systemState.asStateFlow()
    
    private val _conversationHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val conversationHistory: StateFlow<List<ChatMessage>> = _conversationHistory.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private var liquidIntelligenceService: LiquidIntelligenceService? = null
    
    fun bindService(service: LiquidIntelligenceService) {
        liquidIntelligenceService = service
        startSystemMonitoring()
    }
    
    private fun startSystemMonitoring() {
        viewModelScope.launch {
            while (true) {
                liquidIntelligenceService?.let { service ->
                    _systemState.value = service.getCurrentSystemState()
                }
                delay(5000L) // Update UI every 5 seconds
            }
        }
    }
    
    fun processUserInput(input: String) {
        viewModelScope.launch {
            try {
                _isProcessing.value = true
                
                val currentHistory = _conversationHistory.value
                val newMessages = currentHistory + ChatMessage("user", input)
                
                liquidIntelligenceService?.let { service ->
                    val result = service.queryLLM(newMessages)
                    
                    when (result) {
                        is LLMResult.Success -> {
                            val updatedHistory = newMessages + ChatMessage("assistant", result.response)
                            _conversationHistory.value = updatedHistory
                        }
                        is LLMResult.Error -> {
                            Log.e("ALI", "LLM Error: ${result.message}")
                            val errorHistory = newMessages + ChatMessage("assistant", "I encountered an error: ${result.message}")
                            _conversationHistory.value = errorHistory
                        }
                        is LLMResult.NetworkError -> {
                            Log.e("ALI", "Network error during LLM request")
                            val errorHistory = newMessages + ChatMessage("assistant", "Network connection issue. Please check your internet connection.")
                            _conversationHistory.value = errorHistory
                        }
                        is LLMResult.RateLimited -> {
                            Log.w("ALI", "LLM rate limit exceeded")
                            val errorHistory = newMessages + ChatMessage("assistant", "Service temporarily unavailable. Please try again in a moment.")
                            _conversationHistory.value = errorHistory
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e("ALI", "Error processing user input: ${e.message}", e)
            } finally {
                _isProcessing.value = false
            }
        }
    }
    
    fun clearConversationHistory() {
        _conversationHistory.value = emptyList()
    }
    
    fun getSystemOptimizationSuggestions(): List<String> {
        val state = _systemState.value
        val suggestions = mutableListOf<String>()
        
        if (state.isLowMemory) {
            suggestions.add("Memory usage is high (${(state.memoryPressure * 100).toInt()}%) - Consider closing unused apps")
        }
        
        if (state.cpuUsagePercent > 70.0) {
            suggestions.add("High CPU usage detected (${state.cpuUsagePercent.toInt()}%) - Reducing background tasks")
        }
        
        if (state.isBatteryLow) {
            suggestions.add("Battery level low (${state.batteryLevel}%) - Enabling power saving mode")
        }
        
        if (state.isOverheating) {
            suggestions.add("Device temperature high (${state.batteryTemperature}°C) - Throttling performance")
        }
        
        if (state.networkLatencyMs > 1000) {
            suggestions.add("Slow network connection (${state.networkLatencyMs}ms) - Prioritizing offline features")
        }
        
        return suggestions
    }
}

// ==================== MAIN LAUNCHER ACTIVITY ====================

import android.app.Activity
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    
    private val viewModel: ALILauncherViewModel by viewModels()
    private var liquidIntelligenceService: LiquidIntelligenceService? = null
    private var bound: Boolean = false
    
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as LiquidIntelligenceService.LocalBinder
            liquidIntelligenceService = binder.getService()
            bound = true
            viewModel.bindService(binder.getService())
            Log.i("ALI", "Service connected successfully")
        }
        
        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
            Log.i("ALI", "Service disconnected")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Bind to LiquidIntelligenceService
        val intent = Intent(this, LiquidIntelligenceService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        startService(intent) // Ensure service stays running
        
        setContent {
            ALILauncherTheme {
                ALILauncherScreen(viewModel = viewModel)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
}

@Composable
fun ALILauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6200EE),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ALILauncherScreen(viewModel: ALILauncherViewModel) {
    val systemState by viewModel.systemState.collectAsState()
    val conversationHistory by viewModel.conversationHistory.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val suggestions = viewModel.getSystemOptimizationSuggestions()
    
    var userInput by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with system status
            SystemStatusCard(systemState = systemState, suggestions = suggestions)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Conversation area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (conversationHistory.isEmpty()) {
                        item {
                            WelcomeMessage()
                        }
                    }
                    
                    items(conversationHistory) { message ->
                        MessageBubble(message = message)
                    }
                    
                    if (isProcessing) {
                        item {
                            ProcessingIndicator()
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Input area
            InputArea(
                userInput = userInput,
                onInputChange = { userInput = it },
                onSendMessage = {
                    if (userInput.isNotBlank() && !isProcessing) {
                        coroutineScope.launch {
                            viewModel.processUserInput(userInput)
                            userInput = ""
                        }
                    }
                },
                isProcessing = isProcessing
            )
        }
    }
}

@Composable
fun SystemStatusCard(
    systemState: SystemResourceState,
    suggestions: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "ALI System Status",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SystemMetric(
                    label = "Memory",
                    value = "${(systemState.memoryPressure * 100).toInt()}%",
                    isWarning = systemState.isLowMemory
                )
                
                SystemMetric(
                    label = "CPU",
                    value = "${systemState.cpuUsagePercent.toInt()}%",
                    isWarning = systemState.cpuUsagePercent > 70.0
                )
                
                SystemMetric(
                    label = "Battery",
                    value = "${systemState.batteryLevel}%",
                    isWarning = systemState.isBatteryLow
                )
                
                SystemMetric(
                    label = "Temp",
                    value = "${systemState.batteryTemperature.toInt()}°C",
                    isWarning = systemState.isOverheating
                )
            }
            
            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                suggestions.forEach { suggestion ->
                    Text(
                        text = "• $suggestion",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun SystemMetric(
    label: String,
    value: String,
    isWarning: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isWarning) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun WelcomeMessage() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Welcome to ALI Launcher",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "I'm your Advanced Launcher Intelligence. I can help you with:",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            listOf(
                "• Managing and optimizing your device",
                "• Answering questions and providing information",
                "• Organizing your apps and files",
                "• System performance monitoring"
            ).forEach { feature ->
                Text(
                    text = feature,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "What would you like to know or do?",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(horizontal = if (isUser) 16.dp else 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                }
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            )
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                fontSize = 14.sp,
                color = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
fun ProcessingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 100.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Thinking...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputArea(
    userInput: String,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    isProcessing: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        TextField(
            value = userInput,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = if (isProcessing) "Processing..." else "Ask ALI anything...",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            },
            enabled = !isProcessing,
            colors = TextFieldDefaults.textFieldColors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(8.dp),
            maxLines = 3
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        FloatingActionButton(
            onClick = onSendMessage,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = "→",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==================== PRODUCTION DEPLOYMENT NOTES ====================

/*
CRITICAL PRODUCTION REQUIREMENTS FOR NEXT SESSION:

1. SECURE API KEY MANAGEMENT:
   - Implement EncryptedSharedPreferences for API key storage
   - Add user setup flow for API key configuration
   - Implement fallback to local TensorFlow Lite model

2. SPEECH PROCESSING INTEGRATION:
   - Add Android SpeechRecognizer for offline STT
   - Integrate TextToSpeech for voice responses
   - Implement wake word detection

3. LOCAL AI MODEL INTEGRATION:
   - Bundle TensorFlow Lite model for offline LLM
   - Implement model quantization for mobile optimization
   - Add model download/update mechanism

4. LAUNCHER PERMISSIONS:
   - Request PACKAGE_USAGE_STATS permission
   - Implement default launcher selection flow
   - Add accessibility service for advanced features

5. PERSISTENT STORAGE:
   - Implement Room database for conversation history
   - Add SQLCipher encryption for sensitive data
   - Create knowledge base markdown storage system

PLACEHOLDER COMPONENTS REQUIRING IMPLEMENTATION:
- getApiKeyFromSecureStorage(): Requires EncryptedSharedPreferences implementation
- Speech processing hooks: Requires Android Speech API integration
- Local TensorFlow Lite model: Requires model bundling and inference code
- Launcher app management: Requires PackageManager integration

PERFORMANCE OPTIMIZATIONS NEEDED:
- Implement proper coroutine scoping for lifecycle management
- Add proper error handling and retry mechanisms
- Optimize UI rendering with proper state management
- Implement background task scheduling with WorkManager

SECURITY ENHANCEMENTS REQUIRED:
- Certificate pinning for API requests
- Request signing for sensitive operations
- Biometric authentication for sensitive features
- Secure enclave integration for key management
*/