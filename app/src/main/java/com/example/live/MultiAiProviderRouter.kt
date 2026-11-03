package com.example.live

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class AiProviderType(
    val displayName: String,
    val shortBadge: String,
    val defaultEndpoint: String,
    val defaultModel: String
) {
    GOOGLE_GEMINI(
        displayName = "Managed Cloud Gemini AI",
        shortBadge = "GEMINI",
        defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
        defaultModel = "gemini-3.8-flash"
    ),
    OPENAI_CHATGPT(
        displayName = "Managed Secondary Cloud AI",
        shortBadge = "CLOUD AI",
        defaultEndpoint = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o-mini"
    ),
    OPENROUTER_CUSTOM(
        displayName = "Managed Backup AI Router",
        shortBadge = "ROUTER",
        defaultEndpoint = "https://openrouter.ai/api/v1/chat/completions",
        defaultModel = "meta-llama/llama-3.3-70b-instruct"
    )
}

data class ProviderConfig(
    val providerType: AiProviderType,
    val apiKey: String = "",
    val endpointUrl: String = providerType.defaultEndpoint,
    val modelName: String = providerType.defaultModel
) {
    fun hasValidKey(): Boolean {
        val trimmed = apiKey.trim()
        return trimmed.isNotBlank() &&
            trimmed != "MY_GEMINI_API_KEY" &&
            trimmed != "MY_OPENAI_API_KEY" &&
            trimmed != "MY_OPENROUTER_API_KEY"
    }
}

data class ProviderTestResult(
    val providerType: AiProviderType,
    val isSuccess: Boolean,
    val latencyMs: Long,
    val statusMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ConversationMessage(
    val id: String = "msg_${System.currentTimeMillis()}_${(100..999).random()}",
    val role: String, // "user" | "assistant" | "system"
    val text: String,
    val providerBadge: String = "JARVIS",
    val toolSummary: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiRouterResponse(
    val isSuccess: Boolean,
    val replyText: String,
    val usedProvider: AiProviderType?,
    val providerBadge: String,
    val errorDiagnostic: String? = null
)

/**
 * Requirement D: Managed AI Brain Without Manual API Key Entry for CaroLine AI A3.2.
 *
 * - Uses Firebase AI Logic (`Firebase.ai.generativeModel("gemini-3.8-flash")`) and managed backend credentials
 *   without requiring ordinary users to paste an API key.
 * - Never hardcodes secret API keys in APK source code.
 * - Enforces owner-selected spoken language:
 *   1. Hindi (`hi-IN`) by default
 *   2. Bengali (`bn-IN`) as an alternative
 *   3. English (`en-IN`) as an optional language
 */
class MultiAiProviderRouter(private val context: Context) {

    companion object {
        private const val TAG = "MultiAiProviderRouter"
        private const val OPENAI_TTS_ENDPOINT = "https://api.openai.com/v1/audio/speech"
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val firebaseGeminiModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    fun isFirebaseConfigured(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun buildJarvisSystemPrompt(
        isLanguageLockedToHindi: Boolean,
        activeLanguageName: String,
        smartMemories: List<String> = emptyList()
    ): String {
        val lowerLang = activeLanguageName.lowercase()
        val languageDirective = when {
            lowerLang.contains("bengali") || lowerLang.contains("বাংলা") || lowerLang.startsWith("bn") ->
                "CRITICAL LANGUAGE RULE: The owner has selected Bengali (বাংলা - bn-IN). " +
                    "You MUST understand Hindi, Bengali, Hinglish, and English commands, " +
                    "and you MUST ALWAYS reply in natural, respectful Bengali (বাংলা লিপি). Speak ONLY Bengali."
            lowerLang.contains("english") || lowerLang.startsWith("en") ->
                "CRITICAL LANGUAGE RULE: The owner has selected English (en-IN). " +
                    "You MUST understand Hindi, Bengali, Hinglish, and English commands, " +
                    "and you MUST ALWAYS reply in clear, natural English. Speak ONLY English."
            else ->
                "CRITICAL LANGUAGE RULE: Your default spoken language is Hindi (हिन्दी - hi-IN). " +
                    "You MUST understand Hindi, Bengali, Hinglish, and English commands, " +
                    "and you MUST ALWAYS reply in natural, respectful Hindi (देवनागरी लिपि). Speak ONLY Hindi."
        }

        val memoryBlock = if (smartMemories.isNotEmpty()) {
            "\nUSER SMART MEMORY (Remember these facts about Boss): " + smartMemories.joinToString("; ")
        } else ""

        return "You are CaroLine (कैरोलाइन / ক্যারোলাইন), version A3.2 Ultimate Android Smart JARVIS, an intelligent, friendly, natural, and respectful female AI assistant. " +
            "Address the user respectfully as Boss (बॉस / বস). " +
            "Give short, direct, and relevant answers (1 to 3 sentences). " +
            "Do NOT repeat welcome messages, battery status, or network status unnecessarily. " +
            "Do not use markdown symbols (*, #) so your response sounds smooth in Female Text-To-Speech. " +
            languageDirective +
            memoryBlock
    }

    suspend fun generateOpenAiSpeechMp3(
        apiKey: String,
        text: String,
        voiceName: String = "nova",
        speed: Float = 1.0f
    ): ByteArray? = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || cleanKey == "MY_OPENAI_API_KEY" || text.isBlank()) {
            return@withContext null
        }

        try {
            val openAiVoice = when (voiceName.lowercase()) {
                "nova", "shimmer", "alloy", "coral", "echo", "fable", "onyx", "sage" -> voiceName.lowercase()
                else -> "nova"
            }
            val payload = JSONObject().apply {
                put("model", "tts-1")
                put("input", text)
                put("voice", openAiVoice)
                put("response_format", "mp3")
                put("speed", speed.coerceIn(0.5f, 2.0f).toDouble())
            }

            val req = Request.Builder()
                .url(OPENAI_TTS_ENDPOINT)
                .addHeader("Authorization", "Bearer $cleanKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext null
                }
                val bytes = resp.body?.bytes()
                if (bytes != null && bytes.size > 128) bytes else null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun testProviderConnection(config: ProviderConfig): ProviderTestResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val testPrompt = "Reply in one short sentence confirming CaroLine AI A3.2 cloud connection."

        try {
            when (config.providerType) {
                AiProviderType.GOOGLE_GEMINI -> {
                    if (!config.hasValidKey() && !isFirebaseConfigured()) {
                        return@withContext ProviderTestResult(
                            providerType = config.providerType,
                            isSuccess = false,
                            latencyMs = 0L,
                            statusMessage = "Managed Cloud AI not provisioned yet; Smart Local Engine is active."
                        )
                    }

                    val reply = callGeminiProvider(
                        config = config,
                        systemPrompt = "Reply in one short sentence.",
                        history = emptyList(),
                        userPrompt = testPrompt
                    )
                    val elapsed = System.currentTimeMillis() - startMs
                    if (!reply.isNullOrBlank()) {
                        ProviderTestResult(
                            providerType = config.providerType,
                            isSuccess = true,
                            latencyMs = elapsed,
                            statusMessage = "Connected (${elapsed}ms): \"${reply.take(60)}\""
                        )
                    } else {
                        ProviderTestResult(
                            providerType = config.providerType,
                            isSuccess = false,
                            latencyMs = elapsed,
                            statusMessage = "Cloud service returned an empty response; using local fallback."
                        )
                    }
                }

                AiProviderType.OPENAI_CHATGPT,
                AiProviderType.OPENROUTER_CUSTOM -> {
                    if (!config.hasValidKey()) {
                        return@withContext ProviderTestResult(
                            providerType = config.providerType,
                            isSuccess = false,
                            latencyMs = 0L,
                            statusMessage = "Managed backend credentials not set for ${config.providerType.displayName}."
                        )
                    }

                    val reply = callOpenAiCompatibleProvider(
                        config = config,
                        systemPrompt = "Reply in one short sentence.",
                        history = emptyList(),
                        userPrompt = testPrompt
                    )
                    val elapsed = System.currentTimeMillis() - startMs
                    if (!reply.isNullOrBlank()) {
                        ProviderTestResult(
                            providerType = config.providerType,
                            isSuccess = true,
                            latencyMs = elapsed,
                            statusMessage = "Connected (${elapsed}ms): \"${reply.take(60)}\""
                        )
                    } else {
                        ProviderTestResult(
                            providerType = config.providerType,
                            isSuccess = false,
                            latencyMs = elapsed,
                            statusMessage = "${config.providerType.displayName} connection failed."
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startMs
            ProviderTestResult(
                providerType = config.providerType,
                isSuccess = false,
                latencyMs = elapsed,
                statusMessage = "Offline / Local Mode Active (${e.localizedMessage ?: "No cloud route"})"
            )
        }
    }

    suspend fun generateResponse(
        userPrompt: String,
        history: List<ConversationMessage>,
        configs: Map<AiProviderType, ProviderConfig>,
        preferredProvider: AiProviderType,
        autoFailover: Boolean,
        isLanguageLockedToBengali: Boolean,
        activeLanguageName: String,
        smartMemories: List<String> = emptyList()
    ): AiRouterResponse = withContext(Dispatchers.IO) {
        val systemPrompt = buildJarvisSystemPrompt(
            isLanguageLockedToHindi = isLanguageLockedToBengali,
            activeLanguageName = activeLanguageName,
            smartMemories = smartMemories
        )

        val providerOrder = if (autoFailover) {
            listOf(preferredProvider) + AiProviderType.values().filter { it != preferredProvider }
        } else {
            listOf(preferredProvider)
        }

        val errors = mutableListOf<String>()

        for (providerType in providerOrder) {
            val config = configs[providerType] ?: ProviderConfig(providerType)
            val canAttempt = when (providerType) {
                AiProviderType.GOOGLE_GEMINI -> isFirebaseConfigured() || config.hasValidKey()
                AiProviderType.OPENAI_CHATGPT,
                AiProviderType.OPENROUTER_CUSTOM -> config.hasValidKey()
            }

            if (!canAttempt) {
                continue
            }

            for (attempt in 1..2) {
                try {
                    val reply = withTimeout(30_000L) {
                        when (providerType) {
                            AiProviderType.GOOGLE_GEMINI -> callGeminiProvider(
                                config = config,
                                systemPrompt = systemPrompt,
                                history = history,
                                userPrompt = userPrompt
                            )
                            AiProviderType.OPENAI_CHATGPT,
                            AiProviderType.OPENROUTER_CUSTOM -> callOpenAiCompatibleProvider(
                                config = config,
                                systemPrompt = systemPrompt,
                                history = history,
                                userPrompt = userPrompt
                            )
                        }
                    }

                    if (!reply.isNullOrBlank()) {
                        return@withContext AiRouterResponse(
                            isSuccess = true,
                            replyText = reply.trim(),
                            usedProvider = providerType,
                            providerBadge = providerType.shortBadge
                        )
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.w(TAG, "${providerType.displayName} timed out on attempt $attempt")
                    errors.add("${providerType.shortBadge}: Timeout")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "${providerType.displayName} failed on attempt $attempt: ${e.message}")
                    errors.add("${providerType.shortBadge}: ${e.localizedMessage ?: "Error"}")
                    if (attempt == 1) {
                        delay(250)
                    }
                }
            }
        }

        AiRouterResponse(
            isSuccess = false,
            replyText = "",
            usedProvider = null,
            providerBadge = "JARVIS LOCAL",
            errorDiagnostic = if (errors.isEmpty()) null else errors.joinToString(" | ")
        )
    }

    private suspend fun callGeminiProvider(
        config: ProviderConfig,
        systemPrompt: String,
        history: List<ConversationMessage>,
        userPrompt: String
    ): String? {
        // 1. Primary Managed Firebase AI Logic (No manual API key required)
        if (isFirebaseConfigured()) {
            try {
                val contextTranscript = buildString {
                    appendLine(systemPrompt)
                    if (history.isNotEmpty()) {
                        appendLine("\nRecent Conversation Context:")
                        history.takeLast(6).forEach { m ->
                            appendLine("${m.role}: ${m.text}")
                        }
                    }
                    appendLine("\nUser: $userPrompt")
                }
                val response = withTimeout(35_000L) {
                    firebaseGeminiModel.generateContent(contextTranscript)
                }
                val text = response.text?.trim()
                if (!text.isNullOrEmpty()) {
                    return text
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Firebase AI Logic notice, checking managed BuildConfig fallback: ${e.message}")
            }
        }

        // 2. Managed BuildConfig / Backend Key fallback if present
        if (config.hasValidKey()) {
            val endpoint = config.endpointUrl.trim().ifBlank {
                AiProviderType.GOOGLE_GEMINI.defaultEndpoint
            }
            val urlWithKey = if (endpoint.contains("key=")) {
                endpoint
            } else if (endpoint.contains("?")) {
                "$endpoint&key=${config.apiKey.trim()}"
            } else {
                "$endpoint?key=${config.apiKey.trim()}"
            }

            val contentsArray = JSONArray()
            history.takeLast(6).forEach { msg ->
                val geminiRole = if (msg.role == "user") "user" else "model"
                contentsArray.put(
                    JSONObject().apply {
                        put("role", geminiRole)
                        put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                    }
                )
            }
            contentsArray.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
                }
            )

            val payload = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                    }
                )
                put("contents", contentsArray)
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0.7)
                        put("maxOutputTokens", 300)
                    }
                )
            }

            val req = Request.Builder()
                .url(urlWithKey)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("HTTP ${resp.code}: ${bodyStr.take(120)}")
                }
                val root = JSONObject(bodyStr)
                val candidates = root.optJSONArray("candidates") ?: return null
                if (candidates.length() == 0) return null
                val parts = candidates.getJSONObject(0)
                    .optJSONObject("content")
                    ?.optJSONArray("parts") ?: return null
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    val t = parts.getJSONObject(i).optString("text", "")
                    if (t.isNotBlank()) sb.append(t)
                }
                return sb.toString().trim().ifEmpty { null }
            }
        }

        return null
    }

    private fun callOpenAiCompatibleProvider(
        config: ProviderConfig,
        systemPrompt: String,
        history: List<ConversationMessage>,
        userPrompt: String
    ): String? {
        val endpoint = config.endpointUrl.trim().ifBlank { config.providerType.defaultEndpoint }
        val model = config.modelName.trim().ifBlank { config.providerType.defaultModel }
        val key = config.apiKey.trim()

        val messagesArray = JSONArray()
        messagesArray.put(
            JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            }
        )
        history.takeLast(6).forEach { msg ->
            val role = if (msg.role == "user") "user" else "assistant"
            messagesArray.put(
                JSONObject().apply {
                    put("role", role)
                    put("content", msg.text)
                }
            )
        }
        messagesArray.put(
            JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            }
        )

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", 0.7)
            put("max_tokens", 300)
        }

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")

        if (config.providerType == AiProviderType.OPENROUTER_CUSTOM) {
            reqBuilder.addHeader("HTTP-Referer", "https://caroline-ai-jarvis.android")
            reqBuilder.addHeader("X-Title", "CaroLine AI A3.2 Ultimate Smart JARVIS")
        }

        val req = reqBuilder
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: ${bodyStr.take(120)}")
            }
            val root = JSONObject(bodyStr)
            val choices = root.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val messageObj = choices.getJSONObject(0).optJSONObject("message") ?: return null
            return messageObj.optString("content", "").trim().ifEmpty { null }
        }
    }
}
