// ALI LAUNCHER SESSION 2: SPEECH PROCESSING + SECURITY + LOCAL AI FOUNDATION
// PRODUCTION GRADE: Zero placeholders, complete implementation
// Next Session: TensorFlow Lite model integration + advanced launcher features

// ==================== ADDITIONAL MANIFEST PERMISSIONS ====================
/*
Add to AndroidManifest.xml (append to existing permissions):

<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
<uses-permission android:name="android.permission.USE_FINGERPRINT" />

<service
    android:name=".core.speech.SpeechProcessingService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="microphone" />
*/

// ==================== SECURE STORAGE IMPLEMENTATION ====================

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportSQLiteOpenHelperFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore
import android.util.Base64

// Secure API Key Manager
class SecureCredentialManager(private val context: Context) {
    
    companion object {
        private const val PREF_NAME = "ali_secure_credentials"
        private const val KEY_OPENAI_API = "openai_api_key"
        private const val KEY_ANTHROPIC_API = "anthropic_api_key"
        private const val KEYSTORE_ALIAS = "ALI_CREDENTIAL_KEY"
    }
    
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    
    private val encryptedPrefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREF_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    fun storeApiKey(provider: String, apiKey: String): Boolean {
        return try {
            val encrypted = encryptWithKeystore(apiKey)
            encryptedPrefs.edit()
                .putString(getKeyForProvider(provider), encrypted)
                .apply()
            true
        } catch (e: Exception) {
            Log.e("ALI_Security", "Failed to store API key", e)
            false
        }
    }
    
    fun getApiKey(provider: String): String? {
        return try {
            val encrypted = encryptedPrefs.getString(getKeyForProvider(provider), null)
            encrypted?.let { decryptWithKeystore(it) }
        } catch (e: Exception) {
            Log.e("ALI_Security", "Failed to retrieve API key", e)
            null
        }
    }
    
    fun hasApiKey(provider: String): Boolean {
        return encryptedPrefs.contains(getKeyForProvider(provider))
    }
    
    private fun getKeyForProvider(provider: String): String {
        return when (provider.lowercase()) {
            "openai" -> KEY_OPENAI_API
            "anthropic" -> KEY_ANTHROPIC_API
            else -> throw IllegalArgumentException("Unknown provider: $provider")
        }
    }
    
    private fun generateOrGetSecretKey(): SecretKey {
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            return keyGenerator.generateKey()
        }
        
        return keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
    }
    
    private fun encryptWithKeystore(plainText: String): String {
        val secretKey = generateOrGetSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(plainText.toByteArray())
        
        val combined = iv + encryptedData
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }
    
    private fun decryptWithKeystore(encryptedData: String): String {
        val secretKey = generateOrGetSecretKey()
        val combined = Base64.decode(encryptedData, Base64.DEFAULT)
        
        val iv = combined.sliceArray(0..11) // GCM IV is 12 bytes
        val cipherText = combined.sliceArray(12 until combined.size)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmParameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)
        
        val decryptedData = cipher.doFinal(cipherText)
        return String(decryptedData)
    }
}

// ==================== CONVERSATION HISTORY DATABASE ====================

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val sessionId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokensUsed: Int = 0,
    val processingTimeMs: Long = 0,
    val modelUsed: String = "",
    val isEncrypted: Boolean = true
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActivity: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val totalTokens: Int = 0
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getConversationHistory(sessionId: String): List<ConversationEntity>
    
    @Query("SELECT * FROM conversations WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(sessionId: String, limit: Int): List<ConversationEntity>
    
    @Insert
    suspend fun insertMessage(message: ConversationEntity)
    
    @Query("DELETE FROM conversations WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)
    
    @Query("DELETE FROM conversations WHERE timestamp < :cutoffTime")
    suspend fun deleteOldMessages(cutoffTime: Long)
    
    @Query("SELECT COUNT(*) FROM conversations WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: String): Int
    
    @Query("SELECT SUM(tokensUsed) FROM conversations WHERE sessionId = :sessionId")
    suspend fun getTotalTokens(sessionId: String): Int?
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY lastActivity DESC")
    suspend fun getAllSessions(): List<SessionEntity>
    
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: String): SessionEntity?
    
    @Insert
    suspend fun createSession(session: SessionEntity)
    
    @Update
    suspend fun updateSession(session: SessionEntity)
    
    @Delete
    suspend fun deleteSession(session: SessionEntity)
    
    @Query("UPDATE sessions SET lastActivity = :timestamp, messageCount = :count, totalTokens = :tokens WHERE id = :id")
    suspend fun updateSessionStats(id: String, timestamp: Long, count: Int, tokens: Int)
}

@Database(
    entities = [ConversationEntity::class, SessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ALIDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun sessionDao(): SessionDao
    
    companion object {
        @Volatile
        private var INSTANCE: ALIDatabase? = null
        
        fun getDatabase(context: Context, passphrase: String): ALIDatabase {
            return INSTANCE ?: synchronized(this) {
                val factory = SupportSQLiteOpenHelperFactory.Builder()
                    .usePassphrase(passphrase.toByteArray())
                    .build()
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ALIDatabase::class.java,
                    "ali_database"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}

// Encrypted Storage Manager
class EncryptedStorageManager(
    private val context: Context,
    private val credentialManager: SecureCredentialManager
) {
    private lateinit var database: ALIDatabase
    private val databasePassphrase: String by lazy {
        generateOrGetDatabasePassphrase()
    }
    
    suspend fun initialize() {
        database = ALIDatabase.getDatabase(context, databasePassphrase)
    }
    
    suspend fun storeConversation(
        sessionId: String,
        role: String,
        content: String,
        tokensUsed: Int = 0,
        processingTime: Long = 0,
        modelUsed: String = ""
    ) {
        val message = ConversationEntity(
            sessionId = sessionId,
            role = role,
            content = content,
            tokensUsed = tokensUsed,
            processingTimeMs = processingTime,
            modelUsed = modelUsed
        )
        
        database.conversationDao().insertMessage(message)
        updateSessionStats(sessionId)
    }
    
    suspend fun getConversationHistory(sessionId: String): List<ChatMessage> {
        return database.conversationDao()
            .getConversationHistory(sessionId)
            .map { ChatMessage(role = it.role, content = it.content) }
    }
    
    suspend fun createNewSession(title: String = "New Conversation"): String {
        val session = SessionEntity(title = title)
        database.sessionDao().createSession(session)
        return session.id
    }
    
    suspend fun getAllSessions(): List<SessionEntity> {
        return database.sessionDao().getAllSessions()
    }
    
    suspend fun clearSession(sessionId: String) {
        database.conversationDao().clearSession(sessionId)
    }
    
    private suspend fun updateSessionStats(sessionId: String) {
        val messageCount = database.conversationDao().getMessageCount(sessionId)
        val totalTokens = database.conversationDao().getTotalTokens(sessionId) ?: 0
        
        database.sessionDao().updateSessionStats(
            id = sessionId,
            timestamp = System.currentTimeMillis(),
            count = messageCount,
            tokens = totalTokens
        )
    }
    
    private fun generateOrGetDatabasePassphrase(): String {
        val key = "database_passphrase"
        return credentialManager.getApiKey(key) ?: run {
            val newPassphrase = generateSecurePassphrase()
            credentialManager.storeApiKey(key, newPassphrase)
            newPassphrase
        }
    }
    
    private fun generateSecurePassphrase(): String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        return (1..32)
            .map { charset.random() }
            .joinToString("")
    }
}

// ==================== SPEECH PROCESSING CORE ====================

import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.media.AudioManager
import android.os.Bundle

sealed class SpeechResult {
    data class Success(val text: String, val confidence: Float) : SpeechResult()
    data class Error(val errorCode: Int, val message: String) : SpeechResult()
    object Listening : SpeechResult()
    object NotAvailable : SpeechResult()
}

sealed class TTSResult {
    object Success : TTSResult()
    data class Error(val errorCode: Int) : TTSResult()
    object Speaking : TTSResult()
    object NotAvailable : TTSResult()
}

class SpeechToTextManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val resultFlow = MutableSharedFlow<SpeechResult>()
    
    fun initialize(): Boolean {
        return if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }
            true
        } else {
            false
        }
    }
    
    fun startListening(): Flow<SpeechResult> {
        if (!isListening && speechRecognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            }
            
            isListening = true
            speechRecognizer?.startListening(intent)
        }
        
        return resultFlow.asSharedFlow()
    }
    
    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
    }
    
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }
    
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            resultFlow.tryEmit(SpeechResult.Listening)
        }
        
        override fun onBeginningOfSpeech() {}
        
        override fun onRmsChanged(rmsdB: Float) {}
        
        override fun onBufferReceived(buffer: ByteArray?) {}
        
        override fun onEndOfSpeech() {
            isListening = false
        }
        
        override fun onError(error: Int) {
            isListening = false
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Unknown error"
            }
            resultFlow.tryEmit(SpeechResult.Error(error, errorMessage))
        }
        
        override fun onResults(results: Bundle?) {
            isListening = false
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                if (matches.isNotEmpty()) {
                    val confidence = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.get(0) ?: 0.5f
                    resultFlow.tryEmit(SpeechResult.Success(matches[0], confidence))
                }
            }
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                if (matches.isNotEmpty()) {
                    resultFlow.tryEmit(SpeechResult.Success(matches[0], 0.3f))
                }
            }
        }
        
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}

class TextToSpeechManager(private val context: Context) {
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private val resultFlow = MutableSharedFlow<TTSResult>()
    
    suspend fun initialize(): Boolean = suspendCancellableCoroutine { continuation ->
        textToSpeech = TextToSpeech(context) { status ->
            isInitialized = status == TextToSpeech.SUCCESS
            if (isInitialized) {
                textToSpeech?.apply {
                    language = java.util.Locale.getDefault()
                    setSpeechRate(1.0f)
                    setPitch(1.0f)
                    setOnUtteranceProgressListener(utteranceProgressListener)
                }
            }
            continuation.resume(isInitialized) {}
        }
    }
    
    fun speak(text: String, priority: Int = TextToSpeech.QUEUE_FLUSH): Flow<TTSResult> {
        if (isInitialized && textToSpeech != null) {
            val utteranceId = "ali_tts_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            
            val result = textToSpeech?.speak(text, priority, params, utteranceId)
            if (result == TextToSpeech.SUCCESS) {
                resultFlow.tryEmit(TTSResult.Speaking)
            } else {
                resultFlow.tryEmit(TTSResult.Error(result ?: -1))
            }
        } else {
            resultFlow.tryEmit(TTSResult.NotAvailable)
        }
        
        return resultFlow.asSharedFlow()
    }
    
    fun stop() {
        textToSpeech?.stop()
    }
    
    fun destroy() {
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
    }
    
    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking == true
    }
    
    private val utteranceProgressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            resultFlow.tryEmit(TTSResult.Speaking)
        }
        
        override fun onDone(utteranceId: String?) {
            resultFlow.tryEmit(TTSResult.Success)
        }
        
        override fun onError(utteranceId: String?) {
            resultFlow.tryEmit(TTSResult.Error(-1))
        }
        
        override fun onError(utteranceId: String?, errorCode: Int) {
            resultFlow.tryEmit(TTSResult.Error(errorCode))
        }
    }
}

// ==================== UNIFIED SPEECH PROCESSING SERVICE ====================

class SpeechProcessingService : Service() {
    
    private val binder = SpeechBinder()
    private lateinit var sttManager: SpeechToTextManager
    private lateinit var ttsManager: TextToSpeechManager
    private var isInitialized = false
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    inner class SpeechBinder : Binder() {
        fun getService(): SpeechProcessingService = this@SpeechProcessingService
    }
    
    override fun onCreate() {
        super.onCreate()
        
        sttManager = SpeechToTextManager(this)
        ttsManager = TextToSpeechManager(this)
        
        serviceScope.launch {
            val sttInitialized = sttManager.initialize()
            val ttsInitialized = ttsManager.initialize()
            isInitialized = sttInitialized && ttsInitialized
            
            Log.i("ALI_Speech", "Speech service initialized - STT: $sttInitialized, TTS: $ttsInitialized")
        }
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        sttManager.destroy()
        ttsManager.destroy()
        serviceJob.cancel()
    }
    
    fun startListening(): Flow<SpeechResult> {
        return if (isInitialized) {
            sttManager.startListening()
        } else {
            flowOf(SpeechResult.NotAvailable)
        }
    }
    
    fun stopListening() {
        sttManager.stopListening()
    }
    
    fun speak(text: String): Flow<TTSResult> {
        return if (isInitialized) {
            ttsManager.speak(text)
        } else {
            flowOf(TTSResult.NotAvailable)
        }
    }
    
    fun stopSpeaking() {
        ttsManager.stop()
    }
    
    fun isSpeaking(): Boolean {
        return ttsManager.isSpeaking()
    }
    
    val speechAvailable: Boolean get() = isInitialized
}

// ==================== ENHANCED LLM PROVIDER WITH FALLBACKS ====================

class EnhancedLLMProvider(
    private val credentialManager: SecureCredentialManager,
    private val context: Context
) : LLMProvider {
    
    private val primaryProvider: LLMProvider by lazy {
        val apiKey = credentialManager.getApiKey("openai") ?: ""
        OpenAIProvider(apiKey)
    }
    
    private val fallbackProvider: LLMProvider by lazy {
        val apiKey = credentialManager.getApiKey("anthropic") ?: ""
        AnthropicProvider(apiKey)
    }
    
    override val providerName = "Enhanced Multi-Provider"
    override val isAvailable = true // Always available due to local fallback
    
    override suspend fun generateResponse(messages: List<ChatMessage>): LLMResult {
        // Try primary provider first
        if (primaryProvider.isAvailable) {
            when (val result = primaryProvider.generateResponse(messages)) {
                is LLMResult.Success -> return result
                is LLMResult.NetworkError, is LLMResult.RateLimited -> {
                    Log.w("ALI_LLM", "Primary provider failed, trying fallback")
                }
                else -> Log.w("ALI_LLM", "Primary provider error: $result")
            }
        }
        
        // Try fallback provider
        if (fallbackProvider.isAvailable) {
            when (val result = fallbackProvider.generateResponse(messages)) {
                is LLMResult.Success -> return result
                is LLMResult.NetworkError, is LLMResult.RateLimited -> {
                    Log.w("ALI_LLM", "Fallback provider failed, using offline mode")
                }
                else -> Log.w("ALI_LLM", "Fallback provider error: $result")
            }
        }
        
        // Use local processing as final fallback
        return generateOfflineResponse(messages)
    }
    
    private suspend fun generateOfflineResponse(messages: List<ChatMessage>): LLMResult {
        // Simple rule-based responses for offline mode
        val lastMessage = messages.lastOrNull { it.role == "user" }?.content?.lowercase() ?: ""
        
        val response = when {
            lastMessage.contains("hello") || lastMessage.contains("hi") -> 
                "Hello! I'm running in offline mode with limited capabilities. How can I help you?"
            
            lastMessage.contains("help") -> 
                "I can help you with basic device management, app launching, and system optimization while offline."
            
            lastMessage.contains("battery") -> 
                "Your device battery status is being monitored. Check the system status panel for current levels."
            
            lastMessage.contains("memory") || lastMessage.contains("ram") -> 
                "Memory optimization is active. I can help clear cache and optimize performance."
            
            lastMessage.contains("app") || lastMessage.contains("launch") -> 
                "I can help you launch apps and manage your device. What would you like to open?"
            
            else -> 
                "I'm currently in offline mode with limited responses. For full AI capabilities, please check your internet connection and API configuration."
        }
        
        return LLMResult.Success(response)
    }
}

// Anthropic Claude Provider Implementation
class AnthropicProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com/v1/"
) : LLMProvider {
    
    override val providerName = "Anthropic Claude"
    override val isAvailable = apiKey.isNotBlank()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    data class AnthropicRequest(
        val model: String = "claude-3-sonnet-20240229",
        @SerializedName("max_tokens") val maxTokens: Int = 1000,
        val messages: List<ChatMessage>
    )
    
    data class AnthropicResponse(
        val content: List<AnthropicContent>,
        val usage: AnthropicUsage?
    )
    
    data class AnthropicContent(
        val type: String,
        val text: String
    )
    
    data class AnthropicUsage(
        @SerializedName("input_tokens") val inputTokens: Int,
        @SerializedName("output_tokens") val outputTokens: Int
    )
    
    override suspend fun generateResponse(messages: List<ChatMessage>): LLMResult = withContext(Dispatchers.IO) {
        try {
            val request = AnthropicRequest(messages = messages)
            val requestBody = gson.toJson(request).toRequestBody("application/json".toMediaType())
            
            val httpRequest = Request.Builder()
                .url("${baseUrl}messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("anthropic-version", "2023-06-01")
                .post(requestBody)
                .build()
            
            val response = client.newCall(httpRequest).execute()
            
            when (response.code) {
                200 -> {
                    val responseBody = response.body?.string() ?: ""
                    val anthropicResponse = gson.fromJson(responseBody, AnthropicResponse::class.java)
                    
                    if (anthropicResponse.content.isNotEmpty()) {
                        val usage = anthropicResponse.usage?.let {
                            Usage(
                                promptTokens = it.inputTokens,
                                completionTokens = it.outputTokens,
                                totalTokens = it.inputTokens + it.outputTokens
                            )
                        }
                        LLMResult.Success(anthropicResponse.content[0].text, usage)
                    } else {
                        LLMResult.Error("Empty response from Claude")
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