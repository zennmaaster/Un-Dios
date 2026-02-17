package com.castor.app.weather

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP client for the Open-Meteo API (free, no API key needed).
 *
 * Uses OkHttp directly (consistent with [CloudInferenceEngine]) rather than
 * Retrofit, since the Open-Meteo responses are parsed manually via [JSONObject]
 * to avoid pulling in the kotlinx-serialization Retrofit converter for just
 * two endpoints.
 *
 * Endpoints:
 * - Forecast:  GET https://api.open-meteo.com/v1/forecast?...
 * - Geocoding: GET https://geocoding-api.open-meteo.com/v1/search?name=...&count=5
 */
@Singleton
class WeatherApiService @Inject constructor() {

    companion object {
        private const val FORECAST_BASE_URL = "https://api.open-meteo.com/v1/forecast"
        private const val GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/v1/search"

        private const val CURRENT_PARAMS =
            "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
        private const val HOURLY_PARAMS = "temperature_2m,weather_code"
        private const val DAILY_PARAMS = "temperature_2m_max,temperature_2m_min,weather_code"
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Fetch current weather, hourly, and daily forecasts for the given coordinates.
     *
     * @param latitude  Decimal latitude (e.g. 51.5074 for London)
     * @param longitude Decimal longitude (e.g. -0.1278 for London)
     * @return Parsed [WeatherResponse] containing current, hourly, and daily data
     * @throws WeatherApiException on network or parsing failure
     */
    suspend fun getWeather(latitude: Double, longitude: Double): WeatherResponse =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append(FORECAST_BASE_URL)
                append("?latitude=$latitude")
                append("&longitude=$longitude")
                append("&current=$CURRENT_PARAMS")
                append("&hourly=$HOURLY_PARAMS")
                append("&daily=$DAILY_PARAMS")
                append("&timezone=auto")
            }

            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw WeatherApiException("Weather API error: HTTP ${response.code}")
            }

            val body = response.body?.string()
                ?: throw WeatherApiException("Empty response body from weather API")

            try {
                parseWeatherResponse(body)
            } catch (e: Exception) {
                throw WeatherApiException("Failed to parse weather response: ${e.message}", e)
            }
        }

    /**
     * Search for cities by name using the Open-Meteo Geocoding API.
     *
     * @param name City name to search for
     * @param count Maximum number of results (default 5)
     * @return Parsed [GeocodingResponse] with matching cities
     * @throws WeatherApiException on network or parsing failure
     */
    suspend fun searchCity(name: String, count: Int = 5): GeocodingResponse =
        withContext(Dispatchers.IO) {
            val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
            val url = "$GEOCODING_BASE_URL?name=$encodedName&count=$count"

            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw WeatherApiException("Geocoding API error: HTTP ${response.code}")
            }

            val body = response.body?.string()
                ?: throw WeatherApiException("Empty response body from geocoding API")

            try {
                parseGeocodingResponse(body)
            } catch (e: Exception) {
                throw WeatherApiException("Failed to parse geocoding response: ${e.message}", e)
            }
        }
}

/**
 * Exception type for all weather API errors.
 */
class WeatherApiException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
