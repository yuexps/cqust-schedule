package com.xingheyuzhuan.shiguangschedule.ui.settings.coursemanagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseWithWeeks
import com.xingheyuzhuan.shiguangschedule.data.model.DualColor
import com.xingheyuzhuan.shiguangschedule.ui.theme.LocalIsDarkTheme
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.a11y_back
import shiguangschedule.shared.generated.resources.arrow_back_24px
import shiguangschedule.shared.generated.resources.course_time_day_section_details_tweak
import shiguangschedule.shared.generated.resources.course_time_day_time_details_tweak
import shiguangschedule.shared.generated.resources.label_weeks_format
import shiguangschedule.shared.generated.resources.week_days_full_names

/**
 * 课程排课实例只读总览页面 (Detail View)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseInstanceListScreen(
    courseName: String,
    onNavigateBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
    viewModel: CourseInstanceListViewModel = koinViewModel()
) {
    LaunchedEffect(courseName) {
        viewModel.initCourseName(courseName)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val courseInstances by viewModel.courseInstances.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(courseName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.a11y_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(courseInstances, key = { it.course.id }) { courseWithWeeks ->
                CourseInstanceCard(
                    courseWithWeeks = courseWithWeeks,
                    colorMaps = uiState.courseColorMaps
                )
            }
        }
    }
}

/**
 * 课程实例只读卡片
 */
@Composable
fun CourseInstanceCard(
    courseWithWeeks: CourseWithWeeks,
    colorMaps: List<DualColor>
) {
    val course = courseWithWeeks.course
    val isDarkTheme = LocalIsDarkTheme.current

    val fallbackColor = DualColor(
        light = MaterialTheme.colorScheme.surfaceVariant,
        dark = MaterialTheme.colorScheme.surfaceVariant
    )
    val courseColorDual = colorMaps.getOrNull(course.colorInt) ?: colorMaps.firstOrNull() ?: fallbackColor
    val courseBackgroundColor = if (isDarkTheme) courseColorDual.dark else courseColorDual.light

    val weekDays = stringArrayResource(Res.array.week_days_full_names)
    val dayName = weekDays.getOrElse(course.day - 1) { "?" }

    Card(
        modifier = Modifier
            .height(IntrinsicSize.Max)
            .clip(CardDefaults.shape),
        colors = CardDefaults.cardColors(
            containerColor = courseBackgroundColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            if (course.teacher.isNotBlank()) {
                Text(
                    text = course.teacher,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (course.position.isNotBlank()) {
                Text(
                    text = course.position,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            val timeText = if (course.isCustomTime) {
                stringResource(
                    Res.string.course_time_day_time_details_tweak,
                    dayName,
                    course.customStartTime ?: "?",
                    course.customEndTime ?: "?"
                )
            } else {
                stringResource(
                    Res.string.course_time_day_section_details_tweak,
                    dayName,
                    course.startSection ?: "?",
                    course.endSection ?: "?"
                )
            }

            Text(
                text = timeText,
                style = MaterialTheme.typography.bodyMedium
            )

            val formattedWeeks = courseWithWeeks.weeks.map { it.weekNumber }.sorted().joinToString(", ")
            Text(
                text = stringResource(
                    Res.string.label_weeks_format,
                    formattedWeeks
                ),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}