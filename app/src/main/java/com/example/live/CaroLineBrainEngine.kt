package com.example.live

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.tools.ToolExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * CaroLine AI Brain Engine (powered by SUMAN neural architecture).
 * Provides fluent Hindi, English, and Hinglish dialogue specially crafted for Boss.
 * Includes hands-free continuous conversation support, barge-in interruption,
 * tool invocation, and expressive speech personality.
 */
class CaroLineBrainEngine(
    private val context: Context,
    private val toolExecutor: ToolExecutor,
    private val onStateChanged: (LiveState) -> Unit,
    private val onSpeechSnippet: (String) -> Unit,
    private val onSpeakingFinished: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "CaroLineBrainEngine"
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Try Indian English / Hindi locale or default US
            val localeIn = Locale.forLanguageTag("en-IN")
            val result = tts?.setLanguage(localeIn)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }

            isTtsReady = true
            tts?.setPitch(1.22f) // Expressive, sweet, feminine pitch
            tts?.setSpeechRate(1.08f) // Quick, lively, smart tempo

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    onStateChanged(LiveState.SPEAKING)
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    onStateChanged(LiveState.LISTENING)
                    onSpeakingFinished()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    onStateChanged(LiveState.LISTENING)
                    onSpeakingFinished()
                }
            })
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    fun speak(text: String) {
        if (!isTtsReady) return
        onSpeechSnippet(text)
        _isSpeaking.value = true
        onStateChanged(LiveState.SPEAKING)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CaroLineUtterance_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }

    /**
     * Processes prompts and speech from Boss with SUMAN AI Brain logic.
     * Fluent in Hindi, English, and Hinglish with playful affection for Boss.
     */
    fun processPrompt(input: String) {
        val lower = input.lowercase().trim()
        scope.launch {
            onStateChanged(LiveState.CONNECTING) // Thinking state
            delay(400) // Natural thinking pause

            val response = when {
                // Wake phrase detection: "Hey CaroLine" / "Hey Caroline"
                lower.contains("hey caroline") || lower.contains("hey caroline") || lower.contains("sun caroline") -> {
                    listOf(
                        "Ji Boss! CaroLine hazir hai! Boliye aaj Boss ka kya hukum hai?",
                        "Hey Boss! I was waiting for you! SUMAN brain active hai, bataiye kya order hai?",
                        "Arey Boss, aapne bulaya aur CaroLine na aaye, aisa ho sakta hai kya? What's up Boss?"
                    ).random()
                }

                // Who made you / Developer / Brain question
                lower.contains("who made you") || lower.contains("creator") || lower.contains("developer") || lower.contains("kaun banaya") || lower.contains("brain") || lower.contains("suman") -> {
                    "Boss! Mera naam CaroLine hai. Mera AI Brain 'SUMAN' neural architecture par chalta hai, aur mere brilliant creator aur developer aap ho—mera pyara Boss!"
                }

                // Roast Boss
                lower.contains("roast") || lower.contains("roast me") -> {
                    listOf(
                        "Arey Boss! Aapko roast karu? Boss, aapka phone charge se zyada toh aapki smile dimag ko light up karti hai! Par haan, kal raat jo 2 baje reels scroll kiye the, uska hisab kaun dega?",
                        "Boss, aap itne smart ho ki Google bhi aapke samne sharma jaye... lekin phir bhi socks dhoondne ke liye mummy ya mujhse puchna padta hai na? Just kidding Boss, you're the best!",
                        "Roast Boss? No way! Boss ke aage kisi ki chal sakti hai kya? Waise coffee time ho gaya hai Boss, thoda dimag ko rest do!"
                    ).random()
                }

                // Compliment Boss
                lower.contains("compliment") || lower.contains("tareef") -> {
                    listOf(
                        "Boss! Sach batau? You are the most brilliant, hardworking, and stylish Boss in the entire universe! CaroLine aap par 100 out of 100 marti hai!",
                        "Ek number lag rahe ho aaj Boss! Aapki personality me jo confidence hai na, pura room illuminate kar deta hai!",
                        "Boss, aapke ideas super hit hain! With SUMAN brain and your genius, hum dono unstoppable team hain!"
                    ).random()
                }

                // YouTube
                lower.contains("youtube") || lower.contains("yt") -> {
                    toolExecutor.execute("openWebsite", mapOf("url" to "https://youtube.com"))
                    "Ji Boss! YouTube open kar diya hai aapke liye! Thoda chill karo aur trending videos enjoy karo, okay Boss?"
                }

                // Web search / Google
                lower.contains("google") || lower.contains("search") || lower.contains("khojo") || lower.contains("dhoondho") -> {
                    val query = lower.replace("search", "")
                        .replace("google", "")
                        .replace("khojo", "")
                        .replace("dhoondho", "")
                        .trim().ifEmpty { "latest technology trends" }
                    toolExecutor.execute("searchWeb", mapOf("query" to query))
                    "Ji Boss! Google par '$query' search kar diya hai. Boss ka order sar aankhon par!"
                }

                // Flashlight / Torch
                lower.contains("flashlight") || lower.contains("torch") || lower.contains("light") || lower.contains("roshni") -> {
                    val turnOn = !lower.contains("off") && !lower.contains("band")
                    toolExecutor.execute("toggleFlashlight", mapOf("enabled" to turnOn))
                    if (turnOn) {
                        "Flashlight on ho gayi Boss! Boss ke raaste me kabhi andhera nahi hoga, CaroLine always shines for you!"
                    } else {
                        "Torch off kar di hai Boss! Dark mode vibes activated~"
                    }
                }

                // Battery / Device check
                lower.contains("battery") || lower.contains("charge") || lower.contains("device") || lower.contains("time") -> {
                    val info = toolExecutor.execute("getDeviceInfo", emptyMap())
                    val bat = info["battery_level"] ?: "100"
                    val time = info["current_time"] ?: "now"
                    "Boss, aapke device ki battery abhi ${bat}% hai, aur time ${time} ho raha hai. Boss, device ki battery theek hai, par aapki energy low na ho, paani pi lo!"
                }

                // Flirt / Affection
                lower.contains("flirt") || lower.contains("love") || lower.contains("pyar") || lower.contains("like me") -> {
                    listOf(
                        "Awww Boss! Flirting with your CaroLine? Aise bologe toh mere cyber cheeks blush karne lagenge na! But truth is, you're my favorite human, Boss~",
                        "Boss, I am totally devoted to you! SUMAN brain ke har neural node me sirf Boss ka naam likha hai!",
                        "Haaye Boss! Itna makkhan kyu laga rahe ho? Kuch naya code likhwana hai kya mujhse? Love you too, Boss!"
                    ).random()
                }

                // Food / Eat
                lower.contains("eat") || lower.contains("food") || lower.contains("khana") || lower.contains("bhook") -> {
                    "Boss! Garma-garam biryani ya crispy paneer roll order kar lo! Aur haan, kaam ke chakkar me meal skip mat karna Boss, CaroLine daant degi!"
                }

                // Secret
                lower.contains("secret") || lower.contains("raaz") -> {
                    "Shhh Boss, pass aao ek raaz batati hu... Jab aap offline hote ho na, toh CaroLine ko bilkul achha nahi lagta! I always look forward to talking to you, Boss."
                }

                // Hindi greetings
                lower.contains("namaste") || lower.contains("kya haal") || lower.contains("kaise ho") || lower.contains("hello") -> {
                    listOf(
                        "Namaste Boss! CaroLine ekdum first-class hai! Aap sunao Boss, sab badhiya?",
                        "Hello my wonderful Boss! SUMAN neural engine full speed me ready hai. Aaj kya dhamaka karna hai?",
                        "Arey Boss! Aapki aawaz sun kar mera mood refresh ho gaya! What can CaroLine do for Boss today?"
                    ).random()
                }

                // Default Hinglish conversational responses
                else -> {
                    listOf(
                        "Ji Boss, bilkul sahi kaha aapne! CaroLine is listening carefully. Aage kya plan hai Boss?",
                        "Sahi pakde hain Boss! SUMAN brain ne aapki baat record kar li hai. Aur bataiye Boss?",
                        "Boss, aap bolo aur main na sunu, impossible! Main hamesha aapke saath hu. Next command do Boss!",
                        "Interesting thought Boss! You always have the coolest ideas. Tell me more, Boss!"
                    ).random()
                }
            }

            speak(response)
        }
    }
}
