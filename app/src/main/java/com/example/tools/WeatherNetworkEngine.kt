package com.example.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class LiveWeatherReport(
    val cityName: String,
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val conditionEn: String,
    val conditionHi: String,
    val conditionBn: String,
    val rainProbabilityPercent: Int,
    val windSpeedKmh: Double,
    val isLiveFromApi: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun formatSpokenSummary(langCode: String): String {
        val tempInt = temperatureCelsius.toInt()
        val rainAdviceHi = if (rainProbabilityPercent >= 50) {
            "बारिश की संभावना $rainProbabilityPercent प्रतिशत है, इसलिए छाता साथ रखें।"
        } else {
            "बारिश की संभावना केवल $rainProbabilityPercent प्रतिशत है।"
        }
        val rainAdviceBn = if (rainProbabilityPercent >= 50) {
            "বৃষ্টির সম্ভাবনা $rainProbabilityPercent শতাংশ, তাই ছাতা সাথে রাখুন।"
        } else {
            "বৃষ্টির সম্ভাবনা মাত্র $rainProbabilityPercent শতাংশ।"
        }
        val rainAdviceEn = if (rainProbabilityPercent >= 50) {
            "Rain probability is $rainProbabilityPercent%, so carrying an umbrella is recommended."
        } else {
            "Rain probability is low at $rainProbabilityPercent%."
        }

        return when {
            langCode.startsWith("bn", ignoreCase = true) ->
                "বস, $cityName-এ বর্তমান তাপমাত্রা $tempInt ডিগ্রি সেলসিয়াস, আবহাওয়া $conditionBn এবং $rainAdviceBn"
            langCode.startsWith("en", ignoreCase = true) ->
                "Boss, the current temperature in $cityName is $tempInt°C with $conditionEn conditions. $rainAdviceEn"
            else ->
                "बॉस, $cityName में अभी तापमान $tempInt डिग्री सेल्सियस है और मौसम $conditionHi है। $rainAdviceHi"
        }
    }
}

data class NetworkAndDataReport(
    val isOnline: Boolean,
    val transportType: String,
    val mobileDataUsedMb: Double,
    val totalDataUsedMb: Double,
    val configuredDailyQuotaMb: Int,
    val estimatedRemainingMobileMb: Double?
) {
    fun formatSpokenSummary(langCode: String): String {
        val usedStr = String.format(Locale.US, "%.1f MB", mobileDataUsedMb)
        val totalStr = String.format(Locale.US, "%.1f MB", totalDataUsedMb)
        return when {
            !isOnline -> when {
                langCode.startsWith("bn", ignoreCase = true) ->
                    "বস, বর্তমানে ইন্টারনেট সংযোগ বন্ধ আছে। ডিভাইস বুট হওয়ার পর থেকে মোবাইল ডেটা ব্যবহার হয়েছে $usedStr।"
                langCode.startsWith("en", ignoreCase = true) ->
                    "Boss, internet connection is currently offline. Mobile data used since device boot is $usedStr."
                else ->
                    "बॉस, अभी इंटरनेट कनेक्शन बंद (Offline) है। फोन चालू होने के बाद से $usedStr मोबाइल डेटा उपयोग हुआ है।"
            }
            langCode.startsWith("bn", ignoreCase = true) ->
                "বস, আপনার ইন্টারনেট চালু আছে ($transportType)। মোবাইল ডেটা ব্যবহার: $usedStr (মোট ট্রাফিক: $totalStr)। অবশিষ্ট ক্যারিয়ার ডেটা ব্যালেন্স জানতে আপনার সিম অপারেটরের কোটা প্রয়োজন।"
            langCode.startsWith("en", ignoreCase = true) ->
                "Boss, your internet is online via $transportType. Mobile data used since boot: $usedStr (Total network usage: $totalStr). Note: Exact remaining carrier data balance requires carrier quota access or a user-configured plan limit."
            else ->
                "बॉस, आपका इंटरनेट चालू है और आप $transportType से जुड़े हैं। मोबाइल डेटा उपयोग: $usedStr (कुल डेटा: $totalStr)। ध्यान दें: सिम ऑपरेटर का बचा हुआ सटीक डेटा बैलेंस जानने के लिए टेलीकॉम कैरियर कोटा की आवश्यकता होती है।"
        }
    }
}

/**
 * Requirement K: Smart Weather and Internet Engine for CaroLine AI A3.2.
 *
 * - Fetches live weather from Open-Meteo HTTPS API (no manual API key required):
 *   Temperature (°C), Weather condition, and Rain forecast probability (%).
 * - Inspects real Android ConnectivityManager & TrafficStats for Internet connection status,
 *   Network type, and Data usage information.
 */
class WeatherNetworkEngine(private val context: Context) {

    companion object {
        private const val TAG = "WeatherNetworkEngine"

        private val CITY_COORDINATES: Map<String, Pair<Double, Double>> = mapOf(
            "delhi" to Pair(28.6139, 77.2090),
            "new delhi" to Pair(28.6139, 77.2090),
            "दिल्ली" to Pair(28.6139, 77.2090),
            "mumbai" to Pair(19.0760, 72.8777),
            "मुंबई" to Pair(19.0760, 72.8777),
            "kolkata" to Pair(22.5726, 88.3639),
            "कोलकाता" to Pair(22.5726, 88.3639),
            "কলকাতা" to Pair(22.5726, 88.3639),
            "bengaluru" to Pair(12.9716, 77.5946),
            "bangalore" to Pair(12.9716, 77.5946),
            "बेंगलुरु" to Pair(12.9716, 77.5946),
            "hyderabad" to Pair(17.3850, 78.4867),
            "हैदराबाद" to Pair(17.3850, 78.4867),
            "chennai" to Pair(13.0827, 80.2707),
            "चेन्नई" to Pair(13.0827, 80.2707),
            "pune" to Pair(18.5204, 73.8567),
            "पुणे" to Pair(18.5204, 73.8567),
            "jaipur" to Pair(26.9124, 75.7873),
            "जयपुर" to Pair(26.9124, 75.7873),
            "lucknow" to Pair(26.8467, 80.9462),
            "लखनऊ" to Pair(26.8467, 80.9462),
            "patna" to Pair(25.5941, 85.1376),
            "पटना" to Pair(25.5941, 85.1376),
            "dhaka" to Pair(23.8103, 90.4125),
            "ढाका" to Pair(23.8103, 90.4125),
            "ঢাকা" to Pair(23.8103, 90.4125)
        )

        fun mapWmoWeatherCode(code: Int): Triple<String, String, String> {
            return when (code) {
                0 -> Triple("Clear Sky", "साफ आसमान", "পরিষ্কার আকাশ")
                1, 2, 3 -> Triple("Partly Cloudy", "हल्के बादल", "আংশিক মেঘলা")
                45, 48 -> Triple("Foggy", "कोहरा", "কুয়াশাচ্ছন্ন")
                51, 53, 55, 56, 57 -> Triple("Light Drizzle", "हल्की बूंदाबांदी", "হালকা গুঁড়ি গুঁড়ি বৃষ্টি")
                61, 63, 65, 66, 67 -> Triple("Rain", "बारिश", "বৃষ্টি")
                71, 73, 75, 77 -> Triple("Snowfall", "बर्फबारी", "তুষারপাত")
                80, 81, 82 -> Triple("Rain Showers", "तेज़ बारिश की बौछारें", "বৃষ্টির ঝাপটা")
                95, 96, 99 -> Triple("Thunderstorm", "आंधी-तूफान और बारिश", "বজ্রসহ বৃষ্টি")
                else -> Triple("Pleasant", "सुहावना", "মনোরম")
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    var lastCachedWeather: LiveWeatherReport? = null
        private set

    suspend fun fetchLiveWeather(cityQuery: String = "New Delhi"): LiveWeatherReport = withContext(Dispatchers.IO) {
        val cleanCity = cityQuery.trim().ifEmpty { "New Delhi" }
        try {
            val coords = resolveCoordinates(cleanCity)
            val lat = coords.first
            val lon = coords.second
            val resolvedName = coords.third

            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,wind_speed_10m" +
                "&hourly=precipitation_probability" +
                "&forecast_days=1"

            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    val current = json.optJSONObject("current")
                    val temp = current?.optDouble("temperature_2m", 28.0) ?: 28.0
                    val code = current?.optInt("weather_code", 1) ?: 1
                    val wind = current?.optDouble("wind_speed_10m", 8.0) ?: 8.0

                    val hourly = json.optJSONObject("hourly")
                    val probs = hourly?.optJSONArray("precipitation_probability")
                    var maxRainProb = 10
                    if (probs != null && probs.length() > 0) {
                        for (i in 0 until minOf(12, probs.length())) {
                            maxRainProb = maxOf(maxRainProb, probs.optInt(i, 0))
                        }
                    }

                    val (en, hi, bn) = mapWmoWeatherCode(code)
                    val report = LiveWeatherReport(
                        cityName = resolvedName,
                        temperatureCelsius = temp,
                        weatherCode = code,
                        conditionEn = en,
                        conditionHi = hi,
                        conditionBn = bn,
                        rainProbabilityPercent = maxRainProb,
                        windSpeedKmh = wind,
                        isLiveFromApi = true
                    )
                    lastCachedWeather = report
                    return@withContext report
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Live weather fetch failed, checking cache: ${e.message}")
        }

        lastCachedWeather ?: LiveWeatherReport(
            cityName = cleanCity,
            temperatureCelsius = 28.0,
            weatherCode = 1,
            conditionEn = "Partly Cloudy (Offline Estimate)",
            conditionHi = "हल्के बादल (ऑफलाइन अनुमान)",
            conditionBn = "আংশিক মেঘলা (অফলাইন তথ্য)",
            rainProbabilityPercent = 15,
            windSpeedKmh = 9.0,
            isLiveFromApi = false
        )
    }

    private fun resolveCoordinates(cityQuery: String): Triple<Double, Double, String> {
        val lower = cityQuery.lowercase().trim()
        for ((key, pair) in CITY_COORDINATES) {
            if (lower.contains(key)) {
                val displayCity = key.replaceFirstChar { it.uppercase() }
                return Triple(pair.first, pair.second, displayCity)
            }
        }

        // Try Open-Meteo Geocoding API for any custom city name
        try {
            val encoded = URLEncoder.encode(cityQuery, "UTF-8")
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
            val req = Request.Builder().url(geoUrl).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string().orEmpty())
                    val results = root.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val first = results.getJSONObject(0)
                        val lat = first.getDouble("latitude")
                        val lon = first.getDouble("longitude")
                        val name = first.optString("name", cityQuery)
                        return Triple(lat, lon, name)
                    }
                }
            }
        } catch (_: Exception) {}

        return Triple(28.6139, 77.2090, "New Delhi")
    }

    fun getNetworkAndDataUsage(configuredDailyQuotaMb: Int = 2048): NetworkAndDataReport {
        var isConnected = false
        var transport = "Offline"
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork
            val caps = if (net != null) cm.getNetworkCapabilities(net) else null
            if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                isConnected = true
                transport = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "Online"
                }
            }
        } catch (_: Exception) {}

        val mobileBytes = try {
            val rx = TrafficStats.getMobileRxBytes().coerceAtLeast(0L)
            val tx = TrafficStats.getMobileTxBytes().coerceAtLeast(0L)
            rx + tx
        } catch (_: Exception) {
            0L
        }

        val totalBytes = try {
            val rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
            val tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
            rx + tx
        } catch (_: Exception) {
            0L
        }

        val mobileMb = mobileBytes.toDouble() / (1024.0 * 1024.0)
        val totalMb = totalBytes.toDouble() / (1024.0 * 1024.0)
        val estimatedRemaining = if (configuredDailyQuotaMb > 0) {
            (configuredDailyQuotaMb.toDouble() - mobileMb).coerceAtLeast(0.0)
        } else null

        return NetworkAndDataReport(
            isOnline = isConnected,
            transportType = transport,
            mobileDataUsedMb = mobileMb,
            totalDataUsedMb = totalMb,
            configuredDailyQuotaMb = configuredDailyQuotaMb,
            estimatedRemainingMobileMb = estimatedRemaining
        )
    }
}
