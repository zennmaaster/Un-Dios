package com.castor.app.weather

import org.json.JSONArray
import org.json.JSONObject

// =============================================================================
// API Response Models (parsed from Open-Meteo JSON)
// =============================================================================

/**
 * Top-level response from the Open-Meteo forecast API.
 *
 * Parsed manually from JSON rather than using kotlinx.serialization to avoid
 * adding the serialization plugin dependency to the :app module just for one
 * API. The Open-Meteo response shape is simple enough for manual parsing.
 */
data class WeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: CurrentWeatherResponse?,
    val hourly: HourlyWeatherResponse?,
    val daily: DailyWeatherResponse?
)

data class CurrentWeatherResponse(
    val temperature2m: Double,
    val relativeHumidity2m: Int,
    val apparentTemperature: Double,
    val weatherCode: Int,
    val windSpeed10m: Double
)

data class HourlyWeatherResponse(
    val time: List<String>,
    val temperature2m: List<Double>,
    val weatherCode: List<Int>
)

data class DailyWeatherResponse(
    val time: List<String>,
    val weatherCode: List<Int>,
    val temperature2mMax: List<Double>,
    val temperature2mMin: List<Double>
)

// =============================================================================
// Geocoding API Response
// =============================================================================

data class GeocodingResponse(
    val results: List<GeocodingResult>
)

data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val admin1: String? // State/province
)

// =============================================================================
// Domain Models (UI-facing)
// =============================================================================

/**
 * Processed weather data ready for display in the terminal-style UI.
 */
data class WeatherData(
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windSpeed: Double,
    val weatherCode: Int,
    val description: String,
    val icon: String,
    val cityName: String,
    val countryCode: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class HourlyForecast(
    val time: String,      // "8AM", "10AM", etc.
    val temperature: Double,
    val weatherCode: Int,
    val icon: String
)

data class DailyForecast(
    val dayName: String,   // "Mon", "Tue", etc.
    val date: String,      // "Feb 17"
    val weatherCode: Int,
    val icon: String,
    val description: String,
    val tempMax: Double,
    val tempMin: Double
)

// =============================================================================
// WMO Weather Code Mapping
// =============================================================================

/**
 * Maps WMO weather interpretation codes to human-readable descriptions
 * and terminal-style icon characters.
 *
 * Reference: https://open-meteo.com/en/docs#weathervariables
 */
object WeatherCodeMap {

    fun getDescription(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51 -> "Light drizzle"
        53 -> "Moderate drizzle"
        55 -> "Dense drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"
        63 -> "Moderate rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Light snow"
        73 -> "Moderate snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80 -> "Light showers"
        81 -> "Moderate showers"
        82 -> "Heavy showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm w/ hail"
        else -> "Unknown"
    }

    fun getIcon(code: Int): String = when (code) {
        0 -> "\u2600"           // Clear sky
        1, 2, 3 -> "\u26C5"    // Partly cloudy
        45, 48 -> "\uD83C\uDF2B" // Foggy
        in 51..55 -> "\uD83C\uDF26" // Drizzle
        56, 57 -> "\uD83C\uDF26"    // Freezing drizzle
        in 61..65 -> "\uD83C\uDF27" // Rain
        66, 67 -> "\uD83C\uDF27"    // Freezing rain
        in 71..77 -> "\u2744"       // Snow
        in 80..82 -> "\uD83C\uDF27" // Rain showers
        85, 86 -> "\u2744"          // Snow showers
        95 -> "\u26C8"              // Thunderstorm
        96, 99 -> "\u26C8"          // Thunderstorm w/ hail
        else -> "\u2601"            // Default: cloud
    }
}

// =============================================================================
// JSON Parsing Helpers
// =============================================================================

/**
 * Parse the Open-Meteo forecast JSON response into a [WeatherResponse].
 */
fun parseWeatherResponse(json: String): WeatherResponse {
    val obj = JSONObject(json)
    return WeatherResponse(
        latitude = obj.getDouble("latitude"),
        longitude = obj.getDouble("longitude"),
        timezone = obj.optString("timezone", "auto"),
        current = obj.optJSONObject("current")?.let { parseCurrentWeather(it) },
        hourly = obj.optJSONObject("hourly")?.let { parseHourlyWeather(it) },
        daily = obj.optJSONObject("daily")?.let { parseDailyWeather(it) }
    )
}

private fun parseCurrentWeather(obj: JSONObject): CurrentWeatherResponse {
    return CurrentWeatherResponse(
        temperature2m = obj.getDouble("temperature_2m"),
        relativeHumidity2m = obj.getInt("relative_humidity_2m"),
        apparentTemperature = obj.getDouble("apparent_temperature"),
        weatherCode = obj.getInt("weather_code"),
        windSpeed10m = obj.getDouble("wind_speed_10m")
    )
}

private fun parseHourlyWeather(obj: JSONObject): HourlyWeatherResponse {
    return HourlyWeatherResponse(
        time = obj.getJSONArray("time").toStringList(),
        temperature2m = obj.getJSONArray("temperature_2m").toDoubleList(),
        weatherCode = obj.getJSONArray("weather_code").toIntList()
    )
}

private fun parseDailyWeather(obj: JSONObject): DailyWeatherResponse {
    return DailyWeatherResponse(
        time = obj.getJSONArray("time").toStringList(),
        weatherCode = obj.getJSONArray("weather_code").toIntList(),
        temperature2mMax = obj.getJSONArray("temperature_2m_max").toDoubleList(),
        temperature2mMin = obj.getJSONArray("temperature_2m_min").toDoubleList()
    )
}

/**
 * Parse a geocoding search JSON response into a [GeocodingResponse].
 */
fun parseGeocodingResponse(json: String): GeocodingResponse {
    val obj = JSONObject(json)
    val results = obj.optJSONArray("results") ?: return GeocodingResponse(emptyList())
    val list = (0 until results.length()).map { i ->
        val item = results.getJSONObject(i)
        GeocodingResult(
            name = item.getString("name"),
            latitude = item.getDouble("latitude"),
            longitude = item.getDouble("longitude"),
            country = item.optString("country", null),
            admin1 = item.optString("admin1", null)
        )
    }
    return GeocodingResponse(list)
}

// =============================================================================
// JSONArray extension helpers
// =============================================================================

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).map { getString(it) }

private fun JSONArray.toDoubleList(): List<Double> =
    (0 until length()).map { getDouble(it) }

private fun JSONArray.toIntList(): List<Int> =
    (0 until length()).map { getInt(it) }
