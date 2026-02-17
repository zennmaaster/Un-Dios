package com.castor.app.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the weather feature.
 *
 * Manages current weather data, hourly/daily forecasts, city search,
 * and auto-refresh every 30 minutes. Uses [WeatherRepository] for
 * data fetching and caching.
 *
 * State is exposed as [StateFlow]s consumed by [WeatherCard] and
 * [WeatherDetailScreen].
 */
@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repository: WeatherRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "WeatherViewModel"
        private const val AUTO_REFRESH_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes
    }

    // =========================================================================
    // State
    // =========================================================================

    private val _weather = MutableStateFlow<WeatherData?>(null)
    val weather: StateFlow<WeatherData?> = _weather.asStateFlow()

    private val _hourlyForecast = MutableStateFlow<List<HourlyForecast>>(emptyList())
    val hourlyForecast: StateFlow<List<HourlyForecast>> = _hourlyForecast.asStateFlow()

    private val _dailyForecast = MutableStateFlow<List<DailyForecast>>(emptyList())
    val dailyForecast: StateFlow<List<DailyForecast>> = _dailyForecast.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedCity = MutableStateFlow(repository.getSavedCityName())
    val selectedCity: StateFlow<String> = _selectedCity.asStateFlow()

    private val _selectedCountry = MutableStateFlow(repository.getSavedCountry())
    val selectedCountry: StateFlow<String> = _selectedCountry.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GeocodingResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodingResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var autoRefreshJob: Job? = null

    // =========================================================================
    // Init
    // =========================================================================

    init {
        // Load weather for the saved location on startup
        refreshWeather()
        startAutoRefresh()
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Refresh weather data for the currently selected city.
     * Forces a fresh API call, bypassing the cache.
     */
    fun refreshWeather(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val lat = repository.getSavedLatitude()
                val lon = repository.getSavedLongitude()
                val cityName = repository.getSavedCityName()
                val country = repository.getSavedCountry()

                val response = repository.fetchWeather(lat, lon, forceRefresh)

                _weather.value = repository.toWeatherData(response, cityName, country)
                _hourlyForecast.value = repository.toHourlyForecasts(response)
                _dailyForecast.value = repository.toDailyForecasts(response)
                _selectedCity.value = cityName
                _selectedCountry.value = country

                Log.d(TAG, "Weather loaded: ${_weather.value?.temperature}C in $cityName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch weather", e)
                _error.value = e.message ?: "Failed to fetch weather data"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Search for a city by name and set it as the selected location.
     *
     * Performs geocoding first, then uses the first result's coordinates
     * to fetch weather data. The selected city is persisted for next launch.
     *
     * @param name City name to search for
     */
    fun searchAndSetCity(name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val results = repository.searchCity(name)
                if (results.isEmpty()) {
                    _error.value = "City not found: $name"
                    return@launch
                }

                val city = results.first()
                val country = city.country ?: ""

                repository.saveCity(city.name, country, city.latitude, city.longitude)

                val response = repository.fetchWeather(city.latitude, city.longitude, forceRefresh = true)

                _weather.value = repository.toWeatherData(response, city.name, country)
                _hourlyForecast.value = repository.toHourlyForecasts(response)
                _dailyForecast.value = repository.toDailyForecasts(response)
                _selectedCity.value = city.name
                _selectedCountry.value = country
                _searchResults.value = emptyList()

                Log.d(TAG, "City set: ${city.name}, ${city.country}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to search city: $name", e)
                _error.value = e.message ?: "City search failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Search for cities (for the detail screen search UI).
     * Populates [searchResults] without changing the selected city.
     */
    fun searchCities(query: String) {
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            try {
                _searchResults.value = repository.searchCity(query)
            } catch (e: Exception) {
                Log.e(TAG, "City search failed", e)
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * Select a city from search results and fetch its weather.
     */
    fun selectCity(result: GeocodingResult) {
        val country = result.country ?: ""
        repository.saveCity(result.name, country, result.latitude, result.longitude)
        _selectedCity.value = result.name
        _selectedCountry.value = country
        _searchResults.value = emptyList()
        refreshWeather(forceRefresh = true)
    }

    /**
     * Use the device's last known GPS location (requires ACCESS_COARSE_LOCATION).
     *
     * Falls back to the saved location if GPS is unavailable.
     */
    @SuppressLint("MissingPermission")
    fun useDeviceLocation() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val locationManager =
                    context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

                val location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

                if (location != null) {
                    // Reverse geocode to get city name
                    val results = try {
                        repository.searchCity("${location.latitude},${location.longitude}")
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val cityName = results.firstOrNull()?.name ?: "Current Location"
                    val country = results.firstOrNull()?.country ?: ""

                    repository.saveCity(cityName, country, location.latitude, location.longitude)

                    val response = repository.fetchWeather(
                        location.latitude, location.longitude, forceRefresh = true
                    )

                    _weather.value = repository.toWeatherData(response, cityName, country)
                    _hourlyForecast.value = repository.toHourlyForecasts(response)
                    _dailyForecast.value = repository.toDailyForecasts(response)
                    _selectedCity.value = cityName
                    _selectedCountry.value = country

                    Log.d(TAG, "Using device location: $cityName ($country)")
                } else {
                    _error.value = "Location unavailable. Enable GPS or search for a city."
                }
            } catch (e: SecurityException) {
                _error.value = "Location permission denied"
                Log.w(TAG, "Location permission not granted", e)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to get device location"
                Log.e(TAG, "Device location failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Clear the error state. */
    fun clearError() {
        _error.value = null
    }

    // =========================================================================
    // Auto-refresh
    // =========================================================================

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                Log.d(TAG, "Auto-refreshing weather data")
                refreshWeather(forceRefresh = true)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }
}
