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
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.KoinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.error_input_password
import shiguangschedule.shared.generated.resources.error_input_student_id
import shiguangschedule.shared.generated.resources.error_login_default
import shiguangschedule.shared.generated.resources.toast_login_success

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

        viewModelScope.launch {
            if (sid.isEmpty()) {
                _uiState.update { it.copy(errorMessage = getString(Res.string.error_input_student_id)) }
                return@launch
            }
            if (pwd.isEmpty()) {
                _uiState.update { it.copy(errorMessage = getString(Res.string.error_input_password)) }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val syncResult = cqustSyncManager.syncCourses(
                studentId = sid,
                passwordRaw = pwd,
                onProgress = { ToastManager.show(it) }
            )

            syncResult.fold(
                onSuccess = { courseCount ->
                    _uiState.update { it.copy(isLoading = false) }
                    ToastManager.show(getString(Res.string.toast_login_success, courseCount))
                    onSuccess()
                },
                onFailure = { error ->
                    val defaultMsg = getString(Res.string.error_login_default)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: defaultMsg
                        )
                    }
                }
            )
        }
    }
}
