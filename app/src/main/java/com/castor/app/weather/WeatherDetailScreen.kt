package com.castor.app.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.core.ui.theme.TerminalColors

/**
 * Full weather detail screen styled as `$ weather --verbose`.
 *
 * Provides:
 * - Current conditions card (temp, feels like, humidity, wind, description)
 * - 24-hour hourly forecast as a horizontal scrollable row
 * - 7-day daily forecast as a vertical list with high/low temp bars
 * - City search: `$ locate city "..."` with autocomplete results
 * - "Use device location" button
 *
 * Each daily forecast row shows: day name + icon + low---bar---high.
 */
@Composable
fun WeatherDetailScreen(
    onBack: () -> Unit,
    viewModel: WeatherViewModel = hiltViewModel()
) {
    val weather by viewModel.weather.collectAsState()
    val hourlyForecast by viewModel.hourlyForecast.collectAsState()
    val dailyForecast by viewModel.dailyForecast.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val cityName by viewModel.selectedCity.collectAsState()
    val country by viewModel.selectedCountry.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .statusBarsPadding()
    ) {
        // ==================================================================
        // Top bar
        // ==================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalColors.StatusBar)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TerminalColors.Command
                )
            }

            Text(
                text = "\$ weather --verbose",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                ),
                modifier = Modifier.weight(1f)
            )

            // Refresh button
            IconButton(onClick = { viewModel.refreshWeather() }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = TerminalColors.Command
                )
            }
        }

        // ==================================================================
        // Main content
        // ==================================================================
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- City search ---
            item {
                CitySearchSection(
                    searchQuery = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        viewModel.searchCities(it)
                    },
                    onSearch = {
                        if (searchQuery.isNotBlank()) {
                            viewModel.searchAndSetCity(searchQuery)
                            searchQuery = ""
                            isSearchVisible = false
                        }
                    },
                    isSearchVisible = isSearchVisible,
                    onToggleSearch = { isSearchVisible = !isSearchVisible },
                    searchResults = searchResults,
                    isSearching = isSearching,
                    onSelectCity = { result ->
                        viewModel.selectCity(result)
                        searchQuery = ""
                        isSearchVisible = false
                    },
                    onUseDeviceLocation = {
                        viewModel.useDeviceLocation()
                        isSearchVisible = false
                    },
                    cityName = cityName,
                    country = country
                )
            }

            // --- Loading indicator ---
            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = TerminalColors.Warning,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fetching weather data...",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TerminalColors.Warning
                            )
                        )
                    }
                }
            }

            // --- Error display ---
            if (error != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TerminalColors.Error.copy(alpha = 0.1f))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "\$ curl: (7) Failed to connect",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TerminalColors.Error
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = error ?: "",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TerminalColors.Error.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }
            }

            // --- Current conditions card ---
            if (weather != null) {
                item {
                    CurrentConditionsCard(weather = weather!!, country = country)
                }
            }

            // --- Hourly forecast section ---
            if (hourlyForecast.isNotEmpty()) {
                item {
                    HourlyForecastSection(hourlyForecast = hourlyForecast)
                }
            }

            // --- Daily forecast section ---
            if (dailyForecast.isNotEmpty()) {
                item {
                    DailyForecastHeader()
                }

                items(dailyForecast) { day ->
                    DailyForecastRow(
                        forecast = day,
                        globalMin = dailyForecast.minOf { it.tempMin },
                        globalMax = dailyForecast.maxOf { it.tempMax }
                    )
                }
            }

            // Bottom padding
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// =============================================================================
// City search section
// =============================================================================

@Composable
private fun CitySearchSection(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    isSearchVisible: Boolean,
    onToggleSearch: () -> Unit,
    searchResults: List<GeocodingResult>,
    isSearching: Boolean,
    onSelectCity: (GeocodingResult) -> Unit,
    onUseDeviceLocation: () -> Unit,
    cityName: String,
    country: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // Current location display + search toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = TerminalColors.Info,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "@ $cityName${if (country.isNotBlank()) ", $country" else ""}",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Info
                ),
                modifier = Modifier.weight(1f)
            )
            // GPS location button
            IconButton(
                onClick = onUseDeviceLocation,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Use device location",
                    tint = TerminalColors.Prompt,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            // Toggle search
            Text(
                text = if (isSearchVisible) "[close]" else "[search]",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Accent
                ),
                modifier = Modifier.clickable(onClick = onToggleSearch)
            )
        }

        // Search input
        if (isSearchVisible) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(TerminalColors.Surface)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\$ locate city \"",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Prompt
                    )
                )
                TextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = "city name",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TerminalColors.Subtext
                            )
                        )
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Command
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = TerminalColors.Cursor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "\"",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Prompt
                    )
                )
            }

            // Search results
            if (isSearching) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Searching...",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalColors.Warning
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )
            } else if (searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(TerminalColors.Surface)
                ) {
                    searchResults.forEachIndexed { index, result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCity(result) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}.",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TerminalColors.Timestamp
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = result.name,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TerminalColors.Command
                                )
                            )
                            if (result.admin1 != null) {
                                Text(
                                    text = ", ${result.admin1}",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = TerminalColors.Timestamp
                                    )
                                )
                            }
                            if (result.country != null) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = result.country,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = TerminalColors.Subtext
                                    )
                                )
                            }
                        }
                        // Separator
                        if (index < searchResults.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(TerminalColors.Selection)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Current conditions card
// =============================================================================

@Composable
private fun CurrentConditionsCard(
    weather: WeatherData,
    country: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface)
            .padding(16.dp)
    ) {
        // Section header
        Text(
            text = "# current conditions",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Timestamp
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Main temperature row
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Weather icon (large)
            Text(
                text = weather.icon,
                fontSize = 48.sp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                // Temperature
                Text(
                    text = "${weather.temperature.toInt()}\u00B0C",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Accent
                    )
                )
                Text(
                    text = weather.description,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = TerminalColors.Command
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Details grid (terminal key-value style)
        DetailRow(key = "feels_like", value = "${weather.feelsLike.toInt()}\u00B0C")
        DetailRow(key = "humidity", value = "${weather.humidity}%")
        DetailRow(key = "wind_speed", value = "${weather.windSpeed.toInt()} km/h")
    }
}

@Composable
private fun DetailRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$key:",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Prompt
            ),
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command
            )
        )
    }
}

// =============================================================================
// Hourly forecast section
// =============================================================================

@Composable
private fun HourlyForecastSection(hourlyForecast: List<HourlyForecast>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface)
            .padding(12.dp)
    ) {
        Text(
            text = "# hourly forecast (24h)",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Timestamp
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            hourlyForecast.take(24).forEach { hour ->
                DetailHourlyItem(hour)
            }
        }
    }
}

@Composable
private fun DetailHourlyItem(forecast: HourlyForecast) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (forecast.time == "Now")
                    TerminalColors.Accent.copy(alpha = 0.1f)
                else
                    Color.Transparent
            )
            .padding(vertical = 6.dp, horizontal = 2.dp)
    ) {
        Text(
            text = forecast.time,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = if (forecast.time == "Now") FontWeight.Bold else FontWeight.Normal,
                color = if (forecast.time == "Now") TerminalColors.Accent else TerminalColors.Timestamp
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = forecast.icon,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${forecast.temperature.toInt()}\u00B0",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command
            )
        )
    }
}

// =============================================================================
// Daily forecast section
// =============================================================================

@Composable
private fun DailyForecastHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = "# 7-day forecast",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Timestamp
            )
        )
    }
}

@Composable
private fun DailyForecastRow(
    forecast: DailyForecast,
    globalMin: Double,
    globalMax: Double
) {
    val range = (globalMax - globalMin).coerceAtLeast(1.0)
    val barWidthFraction = ((forecast.tempMax - forecast.tempMin) / range).toFloat()
    val barOffsetFraction = ((forecast.tempMin - globalMin) / range).toFloat()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalColors.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Day name
        Text(
            text = forecast.dayName,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = if (forecast.dayName == "Today") FontWeight.Bold else FontWeight.Normal,
                color = if (forecast.dayName == "Today") TerminalColors.Accent else TerminalColors.Command
            ),
            modifier = Modifier.width(48.dp)
        )

        // Weather icon
        Text(
            text = forecast.icon,
            fontSize = 16.sp,
            modifier = Modifier.width(28.dp)
        )

        // Low temp
        Text(
            text = "${forecast.tempMin.toInt()}\u00B0",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Info
            ),
            modifier = Modifier.width(32.dp)
        )

        // Temperature bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(TerminalColors.Selection)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barWidthFraction)
                    .height(6.dp)
                    .padding(start = (barOffsetFraction * 100).dp.coerceAtMost(80.dp))
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        temperatureBarColor(
                            (forecast.tempMax + forecast.tempMin) / 2
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // High temp
        Text(
            text = "${forecast.tempMax.toInt()}\u00B0",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Warning
            ),
            modifier = Modifier.width(32.dp)
        )
    }
}

/**
 * Return a temperature-appropriate bar color using the terminal palette.
 *
 * Cold (<5C) = Info (blue), Mild (5-20C) = Prompt (green),
 * Warm (20-30C) = Warning (orange), Hot (>30C) = Error (red).
 */
@Composable
private fun temperatureBarColor(avgTemp: Double): Color {
    return when {
        avgTemp < 5 -> TerminalColors.Info
        avgTemp < 20 -> TerminalColors.Prompt
        avgTemp < 30 -> TerminalColors.Warning
        else -> TerminalColors.Error
    }
}
