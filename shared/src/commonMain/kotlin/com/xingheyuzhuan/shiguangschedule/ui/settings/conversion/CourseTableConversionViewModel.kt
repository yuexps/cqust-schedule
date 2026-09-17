package com.xingheyuzhuan.shiguangschedule.ui.settings.conversion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.data.model.CourseImportExport
import com.xingheyuzhuan.shiguangschedule.data.repository.CourseConversionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.KoinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.*

/**
 * 课表导出界面的 ViewModel。
 * 负责处理课表数据的导出业务逻辑，并通过状态与一次性事件与 UI 交互。
 */
@KoinViewModel
class CourseTableConversionViewModel(
    private val courseConversionRepository: CourseConversionRepository
) : ViewModel() {

    // UI 状态流：维护界面加载状态及各类对话框的显隐控制
    private val _uiState = MutableStateFlow(ConversionUiState())
    val uiState = _uiState.asStateFlow()

    // UI 事件通道：用于向前端发送一次性副作用事件（如拉起文件保存器、弹出提示消息等）
    private val _events = Channel<ConversionEvent>()
    val events = _events.receiveAsFlow()

    /**
     * 点击导出 JSON 按钮：显示导出选择对话框并指定类型为 JSON
     */
    fun onExportClick() {
        _uiState.value = _uiState.value.copy(
            showExportTableDialog = true,
            exportType = ExportType.JSON
        )
    }

    /**
     * 点击导出 ICS 按钮：显示导出选择对话框并指定类型为 ICS
     */
    fun onExportIcsClick() {
        _uiState.value = _uiState.value.copy(
            showExportTableDialog = true,
            exportType = ExportType.ICS
        )
    }

    /**
     * 关闭所有弹窗对话框
     */
    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(
            showExportTableDialog = false
        )
    }

    /**
     * 当用户在弹窗中确认导出课表时触发（根据当前 exportType 区分 JSON 或 ICS）
     */
    fun onExportTableSelected(tableId: String, alarmMinutes: Int?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                if (_uiState.value.exportType == ExportType.JSON) {
                    val jsonModel = courseConversionRepository.exportCourseTableToJson(tableId)
                    if (jsonModel != null) {
                        val jsonString = CourseImportExport.json.encodeToString(
                            CourseImportExport.CourseTableExportModel.serializer(),
                            jsonModel
                        )
                        _events.send(ConversionEvent.LaunchExportFileCreator(jsonString))
                    } else {
                        val message = getString(Res.string.error_export_table_not_found)
                        _events.send(ConversionEvent.ShowMessage(message))
                    }
                } else if (_uiState.value.exportType == ExportType.ICS) {
                    val icsContent = courseConversionRepository.exportToIcsString(tableId, alarmMinutes)
                    if (icsContent != null) {
                        _events.send(ConversionEvent.LaunchExportIcsFileCreator(icsContent))
                    } else {
                        val message = getString(Res.string.error_ics_export_data_failed)
                        _events.send(ConversionEvent.ShowMessage(message))
                    }
                }
            } catch (e: Exception) {
                val errorMessage = e.message ?: ""
                val message = getString(Res.string.error_export_failed, errorMessage)
                _events.send(ConversionEvent.ShowMessage(message))
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
                dismissDialog()
            }
        }
    }
}

/**
 * 课表转换界面的 UI 状态数据类
 */
data class ConversionUiState(
    val isLoading: Boolean = false,
    val showExportTableDialog: Boolean = false,
    val exportType: ExportType = ExportType.NONE
)

/**
 * 导出类型枚举
 */
enum class ExportType {
    NONE,
    JSON,
    ICS
}

/**
 * 界面一次性副作用事件密封类
 */
sealed class ConversionEvent {
    data class LaunchExportFileCreator(val jsonContent: String) : ConversionEvent()
    data class LaunchExportIcsFileCreator(val icsContent: String) : ConversionEvent()
    data class ShowMessage(val message: String) : ConversionEvent()
}