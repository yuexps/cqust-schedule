package com.xingheyuzhuan.shiguangschedule.ui.settings.coursemanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseWithWeeks
import com.xingheyuzhuan.shiguangschedule.data.model.DualColor
import com.xingheyuzhuan.shiguangschedule.data.repository.AppSettingsRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.CourseTableRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.StyleSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel


@KoinViewModel
class CourseInstanceListViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository,
    private val styleSettingsRepository: StyleSettingsRepository
) : ViewModel() {

    private val _courseNameFlow = MutableStateFlow<String?>(null)

    fun initCourseName(name: String) {
        if (_courseNameFlow.value == name) return
        _courseNameFlow.value = name
    }

    private val currentTableIdFlow = appSettingsRepository.getAppSettings()
        .map { it.currentCourseTableId }

    /**
     * 课程实例列表流
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val courseInstances: StateFlow<List<CourseWithWeeks>> = combine(
        currentTableIdFlow,
        _courseNameFlow
    ) { tableId, name ->
        tableId to name
    }
        .flatMapLatest { (tableId, name) ->
            if (tableId.isEmpty() || name.isNullOrEmpty()) {
                flowOf(emptyList())
            } else {
                courseTableRepository.getCoursesWithWeeksByTableId(tableId)
                    .map { allCourses ->
                        allCourses.filter { it.course.name == name }
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * 封装 UI 状态流，包含动态颜色池
     */
    val uiState: StateFlow<CourseInstanceUiState> = styleSettingsRepository.styleFlow
        .map { currentStyle ->
            CourseInstanceUiState(
                courseColorMaps = currentStyle.courseColorMaps
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CourseInstanceUiState()
        )
}

/**
 * UI 状态包装类
 */
data class CourseInstanceUiState(
    val courseColorMaps: List<DualColor> = emptyList()
)