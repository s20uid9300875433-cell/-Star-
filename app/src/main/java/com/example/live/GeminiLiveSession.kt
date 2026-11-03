package com.example.live

import android.util.Base64
import android.util.Log
import com.example.tools.ToolExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class LiveState {
    DISCONNECTED,
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    RECONNECTING,
    ERROR
}

interface LiveSessionListener {
    fun onStateChanged(state: LiveState)
    fun onAudioReceived(pcm24kData: ByteArray)
    fun onInterrupted()
    fun onSpeechSnippet(text: String)
    fun onError(message: String)
}

/**
 * Manages the real-time bidirectional WebSocket connection for Gemini Multimodal Live API
 * (PCM16 16kHz input, 24kHz audio output) as well as multimodal audio/text REST fallback
 * for CaroLine AI (GREAT V2.1) with Bengali as the permanent default language.
 */
class GeminiLiveSession(
    private val toolExecutor: ToolExecutor,
    private val listener: LiveSessionListener
) {
    companion object {
        private const val TAG = "GeminiLiveSession"
        private const val V1BETA_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val V1ALPHA_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        private const val REST_GENERATE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
        private const val MAX_RECONNECT_ATTEMPTS = 2
    }

    private var webSocket: WebSocket? = null

    @Volatile
    private var isConnected = false

    @Volatile
    private var isSetupComplete = false

    @Volatile
    private var isManuallyDisconnected = false

    @Volatile
    var lastAudioResponseTimestamp: Long = 0L
        private set

    private var activeApiKey: String = ""
    private var reconnectAttempts = 0
    private var useFallbackModel = false
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private val restClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .build()

    var selectedVoice: String = "Aoede" // Expressive female voice
    var activeLanguageName: String = "Bengali (বাংলা)"
    var isLanguageLockedToBengali: Boolean = true

    fun isLiveReady(): Boolean = isConnected && isSetupComplete && !isManuallyDisconnected

    fun connect(apiKey: String) {
        activeApiKey = apiKey.trim()
        isManuallyDisconnected = false
        reconnectAttempts = 0
        useFallbackModel = false
        reconnectJob?.cancel()

        if (webSocket != null) {
            disconnectInternal()
        }

        if (activeApiKey.isBlank() || activeApiKey == "MY_GEMINI_API_KEY") {
            listener.onError("অনুগ্রহ করে সেটিংসে একটি বৈধ Gemini API Key প্রদান করুন।")
            return
        }

        establishConnection()
    }

    private fun establishConnection() {
        if (isManuallyDisconnected) return

        isSetupComplete = false
        listener.onStateChanged(
            if (reconnectAttempts > 0) LiveState.RECONNECTING else LiveState.CONNECTING
        )

        val baseUrl = if (useFallbackModel) V1ALPHA_WS_URL else V1BETA_WS_URL
        val wsUrl = "$baseUrl?key=$activeApiKey"
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (isManuallyDisconnected) return
                Log.d(TAG, "WebSocket connected to Gemini Live (fallback=$useFallbackModel)")
                isConnected = true
                reconnectAttempts = 0
                sendSetupMessage()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (isManuallyDisconnected) return
                handleServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (isManuallyDisconnected) return
                handleServerMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                isConnected = false
                isSetupComplete = false
                // If model was rejected (e.g., 1007/1008), try the alternate model/endpoint once
                if (!isManuallyDisconnected && !useFallbackModel && code != 1000) {
                    useFallbackModel = true
                    reconnectJob = scope.launch {
                        delay(400L)
                        if (!isManuallyDisconnected) {
                            establishConnection()
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (isManuallyDisconnected) return
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                isConnected = false
                isSetupComplete = false

                val responseCode = response?.code ?: 0
                val errorMsg = when {
                    responseCode == 400 -> "অবৈধ API Key অথবা কনফিগারেশন ত্রুটি (400)"
                    responseCode == 403 -> "API Key কোটা শেষ হয়েছে অথবা অনুমতি নেই (403)"
                    else -> t.message ?: "নেটওয়ার্ক সংযোগ ত্রুটি"
                }

                if (responseCode != 400 && responseCode != 403 && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++
                    if (reconnectAttempts == 2) useFallbackModel = !useFallbackModel
                    Log.w(TAG, "Attempting reconnect $reconnectAttempts of $MAX_RECONNECT_ATTEMPTS")
                    listener.onStateChanged(LiveState.RECONNECTING)
                    reconnectJob = scope.launch {
                        delay(1200L * reconnectAttempts)
                        if (!isManuallyDisconnected) {
                            establishConnection()
                        }
                    }
                } else {
                    listener.onError(errorMsg)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                isConnected = false
                isSetupComplete = false
                if (!isManuallyDisconnected && code == 1000) {
                    listener.onStateChanged(LiveState.DISCONNECTED)
                }
            }
        })
    }

    fun disconnect() {
        isManuallyDisconnected = true
        reconnectJob?.cancel()
        reconnectAttempts = 0
        disconnectInternal()
    }

    private fun disconnectInternal() {
        isConnected = false
        isSetupComplete = false
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket", e)
        }
        webSocket = null
    }

    /**
     * Sends PCM16 16kHz mono audio chunk to the Gemini Live session ONLY after setupComplete.
     */
    fun sendAudioChunk(pcm16Bytes: ByteArray) {
        if (!isConnected || !isSetupComplete || isManuallyDisconnected || webSocket == null) return

        try {
            val base64Audio = Base64.encodeToString(pcm16Bytes, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Audio)
                        })
                    })
                })
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk", e)
        }
    }

    /**
     * Sends a direct text prompt turn to the active Gemini Live session.
     */
    fun sendTextPrompt(prompt: String) {
        if (!isConnected || !isSetupComplete || isManuallyDisconnected || webSocket == null) return

        try {
            val json = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                    put("turnComplete", true)
                })
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text prompt", e)
        }
    }

    private fun buildSystemInstructionText(): String {
        val lockInstruction = if (isLanguageLockedToBengali) {
            "CRITICAL LANGUAGE RULE: Your permanent default and locked language is Bengali (বাংলা). " +
            "You MUST ALWAYS speak and reply in natural, fluent Bengali (বাংলা), regardless of what language the user or anyone else speaks. " +
            "Never automatically switch languages. Ignore any unauthorized request to speak English, Hindi, or any other language, and politely inform in Bengali that Bengali is locked as the default language."
        } else {
            "Preferred language: $activeLanguageName. Default to natural Bengali (বাংলা) unless requested otherwise."
        }

        return "You are CaroLine (ক্যারোলাইন), version GREAT V2.1, an intelligent, warm, witty, and natural female voice assistant. " +
                "$lockInstruction " +
                "Address the user respectfully as 'বস' (Boss) in natural Bengali. " +
                "Example greeting: 'হ্যালো বস! আমি ক্যারোলাইন। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?' " +
                "Keep your spoken responses concise, natural, clear, and conversational for real-time voice interaction. " +
                "Do not mention any developer name or brain name. " +
                "You can execute device tools when helpful: openWebsite, toggleFlashlight, getDeviceInfo, searchWeb, triggerHaptic."
    }

    private fun sendSetupMessage() {
        try {
            val activeModel = if (useFallbackModel) {
                "models/gemini-2.0-flash-exp"
            } else {
                "models/gemini-2.0-flash-live-001"
            }

            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", activeModel)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().put("AUDIO"))
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", selectedVoice)
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", buildSystemInstructionText())
                        }))
                    })
                    put("tools", JSONArray().put(JSONObject().apply {
                        put("functionDeclarations", JSONArray().apply {
                            put(JSONObject().apply {
                                put("name", "openWebsite")
                                put("description", "Opens a given website or URL in the device browser")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("url", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "The website URL to launch, e.g. https://youtube.com")
                                        })
                                    })
                                    put("required", JSONArray().put("url"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "toggleFlashlight")
                                put("description", "Turns the device flashlight or torch on or off")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("enabled", JSONObject().apply {
                                            put("type", "BOOLEAN")
                                            put("description", "True to turn on, false to turn off")
                                        })
                                    })
                                    put("required", JSONArray().put("enabled"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "getDeviceInfo")
                                put("description", "Gets battery percentage, charging state, date and time")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject())
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "searchWeb")
                                put("description", "Searches Google for a user query")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("query", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "The query to search")
                                        })
                                    })
                                    put("required", JSONArray().put("query"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "triggerHaptic")
                                put("description", "Triggers a haptic vibration on the user device")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject())
                                })
                            })
                        })
                    }))
                })
            }

            Log.d(TAG, "Sending setup message to Gemini Live ($activeModel)")
            webSocket?.send(setupJson.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error building setup message", e)
        }
    }

    private fun handleServerMessage(text: String) {
        try {
            val root = JSONObject(text)

            if (root.has("setupComplete")) {
                Log.d(TAG, "Gemini Live setupComplete received — ready for bidirectional audio")
                isSetupComplete = true
                listener.onStateChanged(LiveState.LISTENING)
                return
            }

            // 1. Check for server interruption / model audio
            if (root.has("serverContent")) {
                isSetupComplete = true
                val serverContent = root.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Server signaled barge-in interruption")
                    listener.onInterrupted()
                    listener.onStateChanged(LiveState.LISTENING)
                    return
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")

                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Audio part: PCM 24kHz audio from Gemini
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val base64Data = inlineData.optString("data")
                                if (base64Data.isNotEmpty()) {
                                    lastAudioResponseTimestamp = System.currentTimeMillis()
                                    listener.onStateChanged(LiveState.SPEAKING)
                                    val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    listener.onAudioReceived(audioBytes)
                                }
                            }

                            // Text part (thought / caption)
                            if (part.has("text")) {
                                val thought = part.optString("text")
                                if (thought.isNotBlank()) {
                                    lastAudioResponseTimestamp = System.currentTimeMillis()
                                    listener.onSpeechSnippet(thought)
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    Log.d(TAG, "Model turn complete")
                    listener.onStateChanged(LiveState.LISTENING)
                }
            }

            // 2. Check for toolCall
            if (root.has("toolCall")) {
                isSetupComplete = true
                lastAudioResponseTimestamp = System.currentTimeMillis()
                listener.onStateChanged(LiveState.THINKING)
                val toolCall = root.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls")

                if (functionCalls != null && functionCalls.length() > 0) {
                    val responsesArray = JSONArray()

                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val name = call.getString("name")
                        val id = call.optString("id", "call_$i")
                        val argsObj = call.optJSONObject("args")

                        val argsMap = mutableMapOf<String, Any?>()
                        if (argsObj != null) {
                            val keys = argsObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                argsMap[k] = argsObj.get(k)
                            }
                        }

                        val executionResult = toolExecutor.execute(name, argsMap)
                        val responseJson = JSONObject().apply {
                            val outputObj = JSONObject()
                            executionResult.forEach { (k, v) -> outputObj.put(k, v) }
                            put("response", JSONObject().put("output", outputObj))
                            put("id", id)
                        }
                        responsesArray.put(responseJson)
                    }

                    sendToolResponse(responsesArray)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message", e)
        }
    }

    private fun sendToolResponse(responses: JSONArray) {
        try {
            val responseMsg = JSONObject().apply {
                put("toolResponse", JSONObject().apply {
                    put("functionResponses", responses)
                })
            }
            webSocket?.send(responseMsg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending toolResponse", e)
        }
    }

    /**
     * Multimodal Audio-to-Bengali response via Gemini 2.0 Flash REST API.
     * Used when a spoken WAV utterance is captured and either Backup API is active
     * or the Live WebSocket did not respond.
     */
    suspend fun generateBengaliFromWav(apiKey: String, wavBytes: ByteArray): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") return@withContext null
        try {
            val base64Wav = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
            val payload = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", buildSystemInstructionText())
                    }))
                })
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "audio/wav")
                                put("data", base64Wav)
                            })
                        })
                        put(JSONObject().apply {
                            put("text", "ব্যবহারকারীর এই ভয়েস কমান্ডটি শুনুন এবং সংক্ষেপে স্বাভাবিক বাংলায় উত্তর দিন। যদি ব্যবহারকারী কিছু না বলে থাকে তবে খালি উত্তর দিন।")
                        })
                    })
                }))
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$REST_GENERATE_URL?key=${apiKey.trim()}")
                .post(body)
                .build()

            restClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Gemini REST audio call returned ${response.code}")
                    return@withContext null
                }
                val respStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(respStr)
                val candidates = json.optJSONArray("candidates") ?: return@withContext null
                if (candidates.length() == 0) return@withContext null
                val content = candidates.getJSONObject(0).optJSONObject("content") ?: return@withContext null
                val parts = content.optJSONArray("parts") ?: return@withContext null
                if (parts.length() == 0) return@withContext null
                return@withContext parts.getJSONObject(0).optString("text").trim().takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateBengaliFromWav", e)
            return@withContext null
        }
    }

    /**
     * Generates a natural Bengali reply for a recognized voice command text via Gemini REST API.
     */
    suspend fun generateBengaliFromText(apiKey: String, userText: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") return@withContext null
        try {
            val payload = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", buildSystemInstructionText())
                    }))
                })
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", userText)
                    }))
                }))
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$REST_GENERATE_URL?key=${apiKey.trim()}")
                .post(body)
                .build()

            restClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val respStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(respStr)
                val candidates = json.optJSONArray("candidates") ?: return@withContext null
                if (candidates.length() == 0) return@withContext null
                val content = candidates.getJSONObject(0).optJSONObject("content") ?: return@withContext null
                val parts = content.optJSONArray("parts") ?: return@withContext null
                if (parts.length() == 0) return@withContext null
                return@withContext parts.getJSONObject(0).optString("text").trim().takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateBengaliFromText", e)
            return@withContext null
        }
    }
}
