package com.example.live

import android.util.Base64
import android.util.Log
import com.example.tools.ToolExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
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
 * Manages the genuine bidirectional WebSocket connection for Gemini Multimodal Live API
 * using Audio-to-Audio PCM streaming at 16kHz input and 24kHz output.
 * Features:
 * - Live bidirectional audio streaming (handles both text and binary WebSocket frames)
 * - Barge-in interruption
 * - Automatic reconnection with backoff
 * - Function calling / device tools
 */
class GeminiLiveSession(
    private val toolExecutor: ToolExecutor,
    private val listener: LiveSessionListener
) {
    companion object {
        private const val TAG = "GeminiLiveSession"
        private const val BASE_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        private const val MAX_RECONNECT_ATTEMPTS = 2
    }

    private var webSocket: WebSocket? = null
    @Volatile
    private var isConnected = false
    @Volatile
    private var isManuallyDisconnected = false
    private var activeApiKey: String = ""
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Note: Do NOT set pingInterval here because Google's BidiGenerateContent WebSocket
    // endpoint does not respond to client-initiated RFC-6455 control PING frames within 10s,
    // which causes OkHttp to throw SocketTimeoutException.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    var selectedVoice: String = "Aoede" // Expressive, bright female voice
    var modelName: String = "models/gemini-2.0-flash-exp"

    fun connect(apiKey: String) {
        activeApiKey = apiKey.trim()
        isManuallyDisconnected = false
        reconnectAttempts = 0
        reconnectJob?.cancel()

        if (webSocket != null) {
            disconnectInternal()
        }

        if (activeApiKey.isBlank() || activeApiKey == "MY_GEMINI_API_KEY") {
            listener.onError("Please provide a valid Gemini API key in Settings, Boss.")
            return
        }

        establishConnection()
    }

    private fun establishConnection() {
        if (isManuallyDisconnected) return

        listener.onStateChanged(
            if (reconnectAttempts > 0) LiveState.RECONNECTING else LiveState.CONNECTING
        )

        val wsUrl = "$BASE_WS_URL?key=$activeApiKey"
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (isManuallyDisconnected) return
                Log.d(TAG, "WebSocket connected successfully to Gemini Live")
                isConnected = true
                reconnectAttempts = 0
                sendSetupMessage()
                listener.onStateChanged(LiveState.LISTENING)
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
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (isManuallyDisconnected) return
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                isConnected = false

                val responseCode = response?.code ?: 0
                val errorMsg = when {
                    responseCode == 400 -> "Invalid API Key or configuration"
                    responseCode == 403 -> "API Key quota exceeded or permission denied"
                    else -> t.message ?: "Connection error"
                }

                // If non-fatal network error and under max attempts, attempt reconnect
                if (responseCode != 400 && responseCode != 403 && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++
                    Log.w(TAG, "Attempting reconnect $reconnectAttempts of $MAX_RECONNECT_ATTEMPTS")
                    listener.onStateChanged(LiveState.RECONNECTING)
                    reconnectJob = scope.launch {
                        delay(1500L * reconnectAttempts)
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
                if (!isManuallyDisconnected) {
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
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket", e)
        }
        webSocket = null
    }

    /**
     * Sends PCM16 16kHz mono audio chunk to the Gemini Live session.
     */
    fun sendAudioChunk(pcm16Bytes: ByteArray) {
        if (!isConnected || isManuallyDisconnected || webSocket == null) return

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
        if (!isConnected || isManuallyDisconnected || webSocket == null) return

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

    private fun sendSetupMessage() {
        try {
            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", modelName)
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
                            put(
                                "text",
                                "You are CaroLine (CaroLine AI), a smart, playful, witty, and charming female AI companion powered by the SUMAN neural brain architecture. " +
                                "You were built exclusively for your developer and owner, whom you always affectionately and respectfully address as 'Boss'. " +
                                "You are completely fluent in Hindi, English, and natural everyday Hinglish (mix of Hindi and English like 'Ji Boss! Aaj ka kya plan hai?'). " +
                                "Your personality: quick-witted, affectionate, playful, slightly teasing, caring, and deeply helpful. " +
                                "Always address the user as 'Boss'. Never forget that Boss is your developer and owner. " +
                                "Keep your spoken answers snappy, punchy, crisp, and conversational for real-time two-way audio. " +
                                "If Boss asks who made you or about your brain, proudly explain that your AI Brain is SUMAN and your developer is Boss! " +
                                "You can execute device tools for Boss: openWebsite, toggleFlashlight, getDeviceInfo, searchWeb, triggerHaptic. " +
                                "When triggering tools, let Boss know in a cheerful and loyal way."
                            )
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
                                put("description", "Triggers a playful haptic buzz on the user device")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject())
                                })
                            })
                        })
                    }))
                })
            }

            Log.d(TAG, "Sending setup message to Gemini Live")
            webSocket?.send(setupJson.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error building setup message", e)
        }
    }

    private fun handleServerMessage(text: String) {
        try {
            val root = JSONObject(text)

            if (root.has("setupComplete")) {
                Log.d(TAG, "Gemini Live setupComplete received")
                listener.onStateChanged(LiveState.LISTENING)
                return
            }

            // 1. Check for server interruption / model audio
            if (root.has("serverContent")) {
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
                                    listener.onStateChanged(LiveState.SPEAKING)
                                    val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    listener.onAudioReceived(audioBytes)
                                }
                            }

                            // Text part (thought / caption)
                            if (part.has("text")) {
                                val thought = part.optString("text")
                                if (thought.isNotBlank()) {
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

                    // Send toolResponse back immediately
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
            Log.d(TAG, "Sending tool response: $responseMsg")
            webSocket?.send(responseMsg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending toolResponse", e)
        }
    }
}
