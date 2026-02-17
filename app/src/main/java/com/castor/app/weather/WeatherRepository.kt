package com.castor.app.weather

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for weather data with in-memory caching and location persistence.
 *
 * Caches the last successful weather response for 30 minutes to reduce API calls.
 * Persists the user's selected city (name + coordinates) in SharedPreferences
 * so the app remembers their location across restarts.
 *
 * Default location: London, UK (51.5074, -0.1278) — used before the user
 * selects a city or grants location permission.
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val apiService: WeatherApiService,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "WeatherRepository"
        private const val PREFS_NAME = "weather_prefs"
        private const val KEY_CITY_NAME = "city_name"
        private const val KEY_COUNTRY = "country"
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
        private const val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes

        // Default: London, UK
        private const val DEFAULT_CITY = "London"
        private const val DEFAULT_COUNTRY = "UK"
        private const val DEFAULT_LAT = 51.5074
        private const val DEFAULT_LON = -0.1278
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // In-memory cache
    private var cachedWeatherResponse: WeatherResponse? = null
    private var cacheTimestamp: Long = 0L
    private var cachedLat: Double = 0.0
    private var cachedLon: Double = 0.0

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Fetch weather data for the given coordinates.
     *
     * Returns a cached response if available and less than 30 minutes old
     * for the same coordinates. Otherwise makes a fresh API call.
     *
     * @param latitude  Decimal latitude
     * @param longitude Decimal longitude
     * @param forceRefresh If true, skip cache and fetch fresh data
     * @return [WeatherResponse] with current, hourly, and daily weather data
     */
    suspend fun fetchWeather(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean = false
    ): WeatherResponse {
        // Check cache validity
        if (!forceRefresh && isCacheValid(latitude, longitude)) {
            Log.d(TAG, "Returning cached weather data")
            return cachedWeatherResponse!!
        }

        Log.d(TAG, "Fetching fresh weather data for ($latitude, $longitude)")
        val response = apiService.getWeather(latitude, longitude)

        // Update cache
        cachedWeatherResponse = response
        cacheTimestamp = System.currentTimeMillis()
        cachedLat = latitude
        cachedLon = longitude

        return response
    }

    /**
     * Search for cities by name using the geocoding API.
     *
     * @param name City name to search for
     * @return List of matching [GeocodingResult]s
     */
    suspend fun searchCity(name: String): List<GeocodingResult> {
        val response = apiService.searchCity(name)
        return response.results
    }

    /**
     * Convert a raw [WeatherResponse] into UI-ready domain models.
     *
     * @param response The raw API response
     * @param cityName Display name for the city
     * @param country Country name/code
     * @return A [WeatherData] object for the current conditions, or null if no current data
     */
    fun toWeatherData(
        response: WeatherResponse,
        cityName: String,
        country: String
    ): WeatherData? {
        val current = response.current ?: return null
        return WeatherData(
            temperature = current.temperature2m,
            feelsLike = current.apparentTemperature,
            humidity = current.relativeHumidity2m,
            windSpeed = current.windSpeed10m,
            weatherCode = current.weatherCode,
            description = WeatherCodeMap.getDescription(current.weatherCode),
            icon = WeatherCodeMap.getIcon(current.weatherCode),
            cityName = cityName,
            countryCode = country
        )
    }

    /**
     * Extract hourly forecasts from the response, starting from the current hour.
     *
     * @param response The raw API response
     * @param count Number of hours to return (default 24)
     * @return List of [HourlyForecast] starting from now
     */
    fun toHourlyForecasts(response: WeatherResponse, count: Int = 24): List<HourlyForecast> {
        val hourly = response.hourly ?: return emptyList()

        // Find the index of the current hour
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
        val currentTimeStr = "${todayStr}T${String.format(Locale.US, "%02d", currentHour)}:00"

        val startIndex = hourly.time.indexOfFirst { it >= currentTimeStr }.coerceAtLeast(0)
        val endIndex = (startIndex + count).coerceAtMost(hourly.time.size)

        return (startIndex until endIndex).map { i ->
            val timeStr = hourly.time[i]
            val hour = timeStr.substringAfter("T").substringBefore(":").toIntOrNull() ?: 0
            val displayTime = when {
                i == startIndex -> "Now"
                hour == 0 -> "12AM"
                hour < 12 -> "${hour}AM"
                hour == 12 -> "12PM"
                else -> "${hour - 12}PM"
            }

            HourlyForecast(
                time = displayTime,
                temperature = hourly.temperature2m[i],
                weatherCode = hourly.weatherCode[i],
                icon = WeatherCodeMap.getIcon(hourly.weatherCode[i])
            )
        }
    }

    /**
     * Extract daily forecasts from the response.
     *
     * @param response The raw API response
     * @return List of [DailyForecast] for the next 7 days
     */
    fun toDailyForecasts(response: WeatherResponse): List<DailyForecast> {
        val daily = response.daily ?: return emptyList()
        val dayFormat = SimpleDateFormat("EEE", Locale.US)
        val dateFormat = SimpleDateFormat("MMM d", Locale.US)
        val parseFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        return daily.time.indices.map { i ->
            val date = try {
                parseFormat.parse(daily.time[i])
            } catch (_: Exception) {
                null
            }

            val isToday = i == 0

            DailyForecast(
                dayName = if (isToday) "Today" else (date?.let { dayFormat.format(it) } ?: "???"),
                date = date?.let { dateFormat.format(it) } ?: daily.time[i],
                weatherCode = daily.weatherCode[i],
                icon = WeatherCodeMap.getIcon(daily.weatherCode[i]),
                description = WeatherCodeMap.getDescription(daily.weatherCode[i]),
                tempMax = daily.temperature2mMax[i],
                tempMin = daily.temperature2mMin[i]
            )
        }
    }

    // =========================================================================
    // Location persistence
    // =========================================================================

    /** Save the selected city to SharedPreferences. */
    fun saveCity(name: String, country: String, latitude: Double, longitude: Double) {
        prefs.edit()
            .putString(KEY_CITY_NAME, name)
            .putString(KEY_COUNTRY, country)
            .putFloat(KEY_LATITUDE, latitude.toFloat())
            .putFloat(KEY_LONGITUDE, longitude.toFloat())
            .apply()
    }

    /** Get the persisted city name. */
    fun getSavedCityName(): String =
        prefs.getString(KEY_CITY_NAME, DEFAULT_CITY) ?: DEFAULT_CITY

    /** Get the persisted country code/name. */
    fun getSavedCountry(): String =
        prefs.getString(KEY_COUNTRY, DEFAULT_COUNTRY) ?: DEFAULT_COUNTRY

    /** Get the persisted latitude. */
    fun getSavedLatitude(): Double =
        prefs.getFloat(KEY_LATITUDE, DEFAULT_LAT.toFloat()).toDouble()

    /** Get the persisted longitude. */
    fun getSavedLongitude(): Double =
        prefs.getFloat(KEY_LONGITUDE, DEFAULT_LON.toFloat()).toDouble()

    // =========================================================================
    // Cache management
    // =========================================================================

    private fun isCacheValid(latitude: Double, longitude: Double): Boolean {
        if (cachedWeatherResponse == null) return false
        val age = System.currentTimeMillis() - cacheTimestamp
        if (age > CACHE_DURATION_MS) return false
        // Same location (within ~100m tolerance)
        val latMatch = Math.abs(cachedLat - latitude) < 0.001
        val lonMatch = Math.abs(cachedLon - longitude) < 0.001
        return latMatch && lonMatch
    }
}
