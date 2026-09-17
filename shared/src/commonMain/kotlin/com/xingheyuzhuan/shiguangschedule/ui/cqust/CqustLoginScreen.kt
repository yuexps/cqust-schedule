package com.xingheyuzhuan.shiguangschedule.ui.cqust

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.a11y_back
import shiguangschedule.shared.generated.resources.a11y_hide_password
import shiguangschedule.shared.generated.resources.a11y_show_password
import shiguangschedule.shared.generated.resources.action_logging_in
import shiguangschedule.shared.generated.resources.action_login_and_import
import shiguangschedule.shared.generated.resources.arrow_back_24px
import shiguangschedule.shared.generated.resources.info_24px
import shiguangschedule.shared.generated.resources.label_password
import shiguangschedule.shared.generated.resources.label_semester_start_date
import shiguangschedule.shared.generated.resources.label_student_id
import shiguangschedule.shared.generated.resources.lock_24px
import shiguangschedule.shared.generated.resources.login_tip_after_sync_prefix
import shiguangschedule.shared.generated.resources.login_tip_check_calendar_middle
import shiguangschedule.shared.generated.resources.login_tip_disclaimer_content
import shiguangschedule.shared.generated.resources.login_tip_disclaimer_title
import shiguangschedule.shared.generated.resources.login_tip_official_calendar
import shiguangschedule.shared.generated.resources.person_24px
import shiguangschedule.shared.generated.resources.placeholder_password
import shiguangschedule.shared.generated.resources.placeholder_student_id
import shiguangschedule.shared.generated.resources.school_24px
import shiguangschedule.shared.generated.resources.school_name
import shiguangschedule.shared.generated.resources.symbol_period
import shiguangschedule.shared.generated.resources.title_cqust_login
import shiguangschedule.shared.generated.resources.title_cqust_sync
import shiguangschedule.shared.generated.resources.visibility_24px
import shiguangschedule.shared.generated.resources.visibility_off_24px

/**
 * 重庆科技大学教务登录界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CqustLoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: CqustLoginViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.title_cqust_login)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = vectorResource(Res.drawable.arrow_back_24px),
                                contentDescription = stringResource(Res.string.a11y_back)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 品牌图标与标题
            Surface(
                modifier = Modifier.size(68.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.school_24px),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.school_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(Res.string.title_cqust_sync),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 登录表单卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // 学号
                    OutlinedTextField(
                        value = uiState.studentId,
                        onValueChange = { viewModel.onStudentIdChange(it) },
                        label = { Text(stringResource(Res.string.label_student_id)) },
                        placeholder = { Text(stringResource(Res.string.placeholder_student_id)) },
                        leadingIcon = {
                            Icon(
                                imageVector = vectorResource(Res.drawable.person_24px),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 密码
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = { viewModel.onPasswordChange(it) },
                        label = { Text(stringResource(Res.string.label_password)) },
                        placeholder = { Text(stringResource(Res.string.placeholder_password)) },
                        leadingIcon = {
                            Icon(
                                imageVector = vectorResource(Res.drawable.lock_24px),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            ) {
                                Icon(
                                    imageVector = vectorResource(
                                        if (passwordVisible) Res.drawable.visibility_24px
                                        else Res.drawable.visibility_off_24px
                                    ),
                                    contentDescription = stringResource(
                                        if (passwordVisible) Res.string.a11y_hide_password
                                        else Res.string.a11y_show_password
                                    ),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.login(onLoginSuccess)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    // 错误提示
                    AnimatedVisibility(
                        visible = uiState.errorMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        uiState.errorMessage?.let { errorMsg ->
                            Text(
                                text = errorMsg,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 登录按钮
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.login(onLoginSuccess)
                        },
                        enabled = !uiState.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(Res.string.action_logging_in))
                        } else {
                            Text(
                                text = stringResource(Res.string.action_login_and_import),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 底部安全、隐私与免责声明卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 行课起始日期配置提示
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.info_24px),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp)
                        )

                        val calendarUrl = "https://www.cqust.edu.cn/index/js/xl.htm"
                        val prefix = stringResource(Res.string.login_tip_after_sync_prefix)
                        val dateLabel = stringResource(Res.string.label_semester_start_date)
                        val middle = stringResource(Res.string.login_tip_check_calendar_middle)
                        val officialCalendar = stringResource(Res.string.login_tip_official_calendar)
                        val period = stringResource(Res.string.symbol_period)

                        val promptAnnotated = buildAnnotatedString {
                            append(prefix)
                            append(dateLabel)
                            append(middle)
                            withLink(
                                LinkAnnotation.Url(
                                    url = calendarUrl,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            textDecoration = TextDecoration.Underline
                                        )
                                    )
                                )
                            ) {
                                append(officialCalendar)
                            }
                            append(period)
                        }

                        Text(
                            text = promptAnnotated,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.8.dp
                    )

                    // 隐私与免责声明
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.login_tip_disclaimer_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                        Text(
                            text = stringResource(Res.string.login_tip_disclaimer_content),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            lineHeight = MaterialTheme.typography.labelSmall.lineHeight
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


