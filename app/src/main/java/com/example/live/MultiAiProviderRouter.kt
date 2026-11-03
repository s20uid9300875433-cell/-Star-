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
        displayName = "Google Gemini API",
        shortBadge = "GEMINI",
        defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
        defaultModel = "gemini-3.8-flash"
    ),
    OPENAI_CHATGPT(
        displayName = "OpenAI ChatGPT API",
        shortBadge = "CHATGPT",
        defaultEndpoint = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o-mini"
    ),
    OPENROUTER_CUSTOM(
        displayName = "OpenRouter / Custom Cloud AI",
        shortBadge = "OPENROUTER",
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
 * Three AI Brain System Router for CaroLine AI 2.5 — Smart JARVIS Edition:
 * 1. Google Gemini API (Firebase AI Logic + Gemini REST / Live API)
 * 2. OpenAI ChatGPT API (or Secure Backend Proxy URL)
 * 3. OpenRouter / Custom Cloud AI API
 *
 * Includes real API connection testing, timeout & retry handling, automatic provider
 * failover, and multi-turn conversation context preservation.
 */
class MultiAiProviderRouter(private val context: Context) {

    companion object {
        private const val TAG = "MultiAiProviderRouter"
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val firebaseGeminiModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private fun isFirebaseConfigured(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun buildJarvisSystemPrompt(
        isLanguageLockedToBengali: Boolean,
        activeLanguageName: String
    ): String {
        val languageDirective = if (isLanguageLockedToBengali) {
            "CRITICAL LANGUAGE RULE: Your permanent default language is Bengali (বাংলা). " +
                "You MUST ALWAYS reply in natural, warm, conversational Bengali (বাংলা), even if the user speaks in English, Hindi, or another language. " +
                "Never switch your response language unless authorized by the owner in Settings."
        } else {
            "Preferred language: $activeLanguageName."
        }

        return "You are CaroLine AI 2.5 (Smart JARVIS Edition), an intelligent, warm, and highly capable native Android voice assistant. " +
            "Address the user respectfully as 'বস' (Boss) in natural Bengali. " +
            "Keep spoken replies concise, clear, and conversational (2 to 4 sentences) so they sound natural when spoken aloud via Text-To-Speech. " +
            "Do not use markdown bullet symbols (*, #) that sound unnatural in TTS. " +
            languageDirective
    }

    /**
     * Tests an AI Provider connection genuinely over the network.
     * Never pretends a connection succeeds without valid credentials.
     */
    suspend fun testProviderConnection(config: ProviderConfig): ProviderTestResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val testPrompt = "হ্যালো ক্যারোলাইন! এক বাক্যে বাংলায় উত্তর দাও।"

        try {
            when (config.providerType) {
                AiProviderType.GOOGLE_GEMINI -> {
                    if (!config.hasValidKey() && !isFirebaseConfigured()) {
                        return@withContext ProviderTestResult(
                            providerType = config.providerType,
                            isSuccess = false,
                            latencyMs = 0L,
                            statusMessage = "বৈধ Gemini API Key দেওয়া হয়নি (No valid API key configured)।"
                        )
                    }

                    val reply = callGeminiProvider(
                        config = config,
                        systemPrompt = "Reply in one short Bengali sentence.",
                        history = emptyList(),
                        userPrompt = testPrompt
                    )
                    val elapsed = System.currentTimeMillis() - startMs
                    if (!reply.isNullOrBlank()) {
                        ProviderTestResult(
                            providerType = config.providerType,
                            isSuccess = true,
                            latencyMs = elapsed,
                            statusMessage = "সংযোগ সফল (${elapsed}ms): \"${reply.take(60)}\""
                        )
                    } else {
                        ProviderTestResult(
                            providerType = config.providerType,
                            isSuccess = false,
                            latencyMs = elapsed,
                            statusMessage = "Gemini থেকে খালি উত্তর এসেছে বা কী (Key) সঠিক নয়।"
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
                            statusMessage = "${config.providerType.displayName}-এর জন্য বৈধ API Key দেওয়া হয়নি।"
                        )
                    }

                    val reply = callOpenAiCompatibleProvider(
                        config = config,
                        systemPrompt = "Reply in one short Bengali sentence.",
                        history = emptyList(),
                        userPrompt = testPrompt
                    )
                    val elapsed = System.currentTimeMillis() - startMs
                    if (!reply.isNullOrBlank()) {
                        ProviderTestResult(
                            providerType = config.providerType,
                            isSuccess = true,
                            latencyMs = elapsed,
                            statusMessage = "সংযোগ সফল (${elapsed}ms): \"${reply.take(60)}\""
                        )
                    } else {
                        ProviderTestResult(
                            providerType = config.providerType,
                            isSuccess = false,
                            latencyMs = elapsed,
                            statusMessage = "${config.providerType.displayName} সংযোগ ব্যর্থ হয়েছে।"
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
                statusMessage = "ত্রুটি: ${e.localizedMessage ?: "Connection failed"}"
            )
        }
    }

    /**
     * Routes a natural-language query across the Three AI Brain System with automatic failover,
     * retry handling, and multi-turn conversation context.
     */
    suspend fun generateResponse(
        userPrompt: String,
        history: List<ConversationMessage>,
        configs: Map<AiProviderType, ProviderConfig>,
        preferredProvider: AiProviderType,
        autoFailover: Boolean,
        isLanguageLockedToBengali: Boolean,
        activeLanguageName: String
    ): AiRouterResponse = withContext(Dispatchers.IO) {
        val systemPrompt = buildJarvisSystemPrompt(isLanguageLockedToBengali, activeLanguageName)

        // Build provider execution order
        val providerOrder = if (autoFailover) {
            listOf(preferredProvider) + AiProviderType.values().filter { it != preferredProvider }
        } else {
            listOf(preferredProvider)
        }

        val errors = mutableListOf<String>()

        for (providerType in providerOrder) {
            val config = configs[providerType] ?: ProviderConfig(providerType)
            val canAttempt = when (providerType) {
                AiProviderType.GOOGLE_GEMINI -> config.hasValidKey() || isFirebaseConfigured()
                AiProviderType.OPENAI_CHATGPT,
                AiProviderType.OPENROUTER_CUSTOM -> config.hasValidKey()
            }

            if (!canAttempt) {
                continue
            }

            // Up to 2 attempts per configured provider for transient network resilience
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
                        delay(350)
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
        // 1. If user provided a valid Gemini API key (or via BuildConfig), use direct REST call with full history
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
            history.takeLast(8).forEach { msg ->
                val geminiRole = if (msg.role == "user") "user" else "model"
                contentsArray.put(
                    JSONObject().apply {
                        put("role", geminiRole)
                        put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", msg.text))
                        )
                    }
                )
            }
            contentsArray.put(
                JSONObject().apply {
                    put("role", "user")
                    put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", userPrompt))
                    )
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
                        put("maxOutputTokens", 350)
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

        // 2. If Firebase App Check + Firebase AI Logic is configured on the project, use Firebase.ai
        if (isFirebaseConfigured()) {
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
            val response = withTimeout(45_000L) {
                firebaseGeminiModel.generateContent(contextTranscript)
            }
            return response.text?.trim()?.ifEmpty { null }
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
        history.takeLast(8).forEach { msg ->
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
            put("max_tokens", 350)
        }

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")

        if (config.providerType == AiProviderType.OPENROUTER_CUSTOM) {
            reqBuilder.addHeader("HTTP-Referer", "https://caroline-ai-jarvis.android")
            reqBuilder.addHeader("X-Title", "CaroLine AI 2.5 Smart JARVIS")
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
