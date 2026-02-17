package com.castor.app.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.core.ui.theme.TerminalColors

/**
 * Compact weather card for the HomeScreen grid (spans 2 columns).
 *
 * Styled as the output of `$ curl wttr.in/CityName` — terminal-native,
 * monospace, information-dense. Shows:
 * - City name as location header: `@ London, UK`
 * - Current temperature (large, Accent color) + feels-like + weather icon
 * - Humidity and wind speed line
 * - Mini horizontal scrolling hourly forecast (next 12 hours)
 *
 * States:
 * - Loading: `$ curl wttr.in/... [spinner]`
 * - Error:   `$ curl: (7) Failed to connect`
 * - Data:    Full weather display
 *
 * Tapping the card navigates to the full [WeatherDetailScreen].
 * The refresh icon button triggers a manual data refresh.
 */
@Composable
fun WeatherCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeatherViewModel = hiltViewModel()
) {
    val weather by viewModel.weather.collectAsState()
    val hourlyForecast by viewModel.hourlyForecast.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val cityName by viewModel.selectedCity.collectAsState()
    val country by viewModel.selectedCountry.collectAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            // ==================================================================
            // Header row: command prompt + refresh button
            // ==================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\$ curl wttr.in/",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalColors.Prompt
                    )
                )
                Text(
                    text = cityName,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Command
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.refreshWeather() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh weather",
                        tint = TerminalColors.Timestamp,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            when {
                // --- Loading state ---
                isLoading && weather == null -> {
                    LoadingState()
                }
                // --- Error state ---
                error != null && weather == null -> {
                    ErrorState(error = error!!)
                }
                // --- Data state ---
                weather != null -> {
                    WeatherContent(
                        weather = weather!!,
                        hourlyForecast = hourlyForecast,
                        country = country
                    )
                }
                // --- Initial state (no data yet, not loading) ---
                else -> {
                    LoadingState()
                }
            }
        }
    }
}

// =============================================================================
// Weather content (main data display)
// =============================================================================

@Composable
private fun WeatherContent(
    weather: WeatherData,
    hourlyForecast: List<HourlyForecast>,
    country: String
) {
    // Location header
    Text(
        text = "@ ${weather.cityName}${if (country.isNotBlank()) ", $country" else ""}",
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = TerminalColors.Info
        )
    )

    Spacer(modifier = Modifier.height(6.dp))

    // --- Main temperature row ---
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Weather icon
        Text(
            text = weather.icon,
            fontSize = 28.sp
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Current temperature (large, accent color)
        Text(
            text = "${weather.temperature.toInt()}\u00B0C",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Feels like + description
        Column {
            Text(
                text = "feels like ${weather.feelsLike.toInt()}\u00B0C",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Command
                )
            )
            Text(
                text = weather.description,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // --- Humidity + Wind line ---
    Text(
        text = "humidity: ${weather.humidity}%  wind: ${weather.windSpeed.toInt()} km/h",
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = TerminalColors.Timestamp
        )
    )

    // --- Hourly forecast scrollable row ---
    if (hourlyForecast.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))

        // Top border
        Text(
            text = "\u250C" + "\u2500".repeat(40) + "\u2510",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = TerminalColors.Selection
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Show next 12 hours
            hourlyForecast.take(12).forEach { hour ->
                HourlyForecastItem(hour)
            }
        }

        // Bottom border
        Text(
            text = "\u2514" + "\u2500".repeat(40) + "\u2518",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = TerminalColors.Selection
            )
        )
    }
}

// =============================================================================
// Hourly forecast item
// =============================================================================

@Composable
private fun HourlyForecastItem(forecast: HourlyForecast) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(36.dp)
    ) {
        // Time
        Text(
            text = forecast.time,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = TerminalColors.Timestamp
            )
        )
        // Icon
        Text(
            text = forecast.icon,
            fontSize = 12.sp
        )
        // Temperature
        Text(
            text = "${forecast.temperature.toInt()}\u00B0",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command
            )
        )
    }
}

// =============================================================================
// Loading state
// =============================================================================

@Composable
private fun LoadingState() {
    Column {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "\$ curl wttr.in/... \u23F3",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Warning
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Connecting to weather service...",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// =============================================================================
// Error state
// =============================================================================

@Composable
private fun ErrorState(error: String) {
    Column {
        Spacer(modifier = Modifier.height(8.dp))
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
            text = error,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Error.copy(alpha = 0.7f)
            ),
            maxLines = 2
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "tap to retry",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = TerminalColors.Timestamp
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}
