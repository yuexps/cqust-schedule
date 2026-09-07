package com.xingheyuzhuan.shiguangschedule.ui.cqust

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.data.api.cqust.CqustSyncManager
import com.xingheyuzhuan.shiguangschedule.data.repository.AppSettingsRepository
import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class CqustLoginUiState(
    val studentId: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@KoinViewModel
class CqustLoginViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val cqustSyncManager: CqustSyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CqustLoginUiState())
    val uiState: StateFlow<CqustLoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = appSettingsRepository.getAppSettings().first()
            _uiState.update {
                it.copy(
                    studentId = settings.cqustStudentId,
                    password = settings.cqustPassword
                )
            }
        }
    }

    fun onStudentIdChange(newId: String) {
        _uiState.update { it.copy(studentId = newId, errorMessage = null) }
    }

    fun onPasswordChange(newPass: String) {
        _uiState.update { it.copy(password = newPass, errorMessage = null) }
    }

    /**
     * 登录
     */
    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        val sid = state.studentId.trim()
        val pwd = state.password.trim()

        if (sid.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请输入学号") }
            return
        }
        if (pwd.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请输入密码") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val syncResult = cqustSyncManager.syncCourses(
                studentId = sid,
                passwordRaw = pwd
            )

            syncResult.fold(
                onSuccess = { courseCount ->
                    _uiState.update { it.copy(isLoading = false) }
                    ToastManager.show("登录成功，已导入 $courseCount 门课程")
                    onSuccess()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "登录失败，请检查账号密码或校园网络"
                        )
                    }
                }
            )
        }
    }

    /**
     * 保存用户选定的开学日期
     */
    fun setSemesterStartDate(dateMillis: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            appSettingsRepository.setSemesterStartDate(dateMillis)
            onDone()
        }
    }
}
