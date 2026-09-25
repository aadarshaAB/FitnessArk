package com.fitnessark.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitnessark.ui.theme.CyanPrimary
import com.fitnessark.util.DateUtils
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToCheckin: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onNavigateToPhotos: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showWeightDialog by remember { mutableStateOf(false) }
    var weightInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadDashboardData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Fitness Ark",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyanPrimary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Streak Card
            StreakCard(streak = state.streakDays)

            // Today's Weight Card
            TodayWeightCard(
                weight = state.todayWeight,
                onEditClick = {
                    weightInput = state.todayWeight?.toString() ?: ""
                    showWeightDialog = true
                }
            )

            // Progress Overview
            ProgressOverviewCard(
                weightChange = state.weightChangeLast7Days,
                onClick = onNavigateToProgress
            )

            // Latest Photo Preview
            if (state.latestPhoto != null) {
                LatestPhotoCard(
                    photoPath = state.latestPhoto!!.frontPhotoPath
                        ?: state.latestPhoto!!.sidePhotoPath
                        ?: state.latestPhoto!!.backPhotoPath,
                    date = state.latestPhoto!!.date,
                    onClick = onNavigateToPhotos
                )
            }

            // Quick Actions
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onNavigateToCheckin,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Text("Full Check-in", color = MaterialTheme.colorScheme.background)
                }
                OutlinedButton(
                    onClick = {
                        weightInput = state.todayWeight?.toString() ?: ""
                        showWeightDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Log Weight Only")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showWeightDialog) {
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            title = { Text("Log Weight") },
            text = {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text("Weight (kg)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    weightInput.toFloatOrNull()?.let { viewModel.updateWeight(it) }
                    showWeightDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun StreakCard(streak: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = "Streak",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "$streak Day Streak",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (streak > 0) "Keep it up!" else "Start logging to build your streak",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TodayWeightCard(weight: Float?, onEditClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Today's Weight",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = weight?.let { "%.1f kg".format(it) } ?: "Not logged",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (weight != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit weight")
            }
        }
    }
}

@Composable
fun ProgressOverviewCard(weightChange: Float?, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isPositive = (weightChange ?: 0f) > 0
            val trendColor = if (isPositive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary

            Icon(
                imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = "Trend",
                tint = trendColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Last 7 Days",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val changeText = when {
                    weightChange == null -> "—"
                    weightChange == 0f   -> "No change"
                    else                 -> "%+.1f kg".format(weightChange)
                }
                Text(
                    text = changeText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = trendColor
                )
            }
        }
    }
}

@Composable
fun LatestPhotoCard(photoPath: String?, date: Long, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            if (photoPath != null) {
                AsyncImage(
                    model = photoPath,
                    contentDescription = "Latest photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                )
                // Date overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f))
                            )
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = DateUtils.formatDate(date),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Latest Progress Photo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
