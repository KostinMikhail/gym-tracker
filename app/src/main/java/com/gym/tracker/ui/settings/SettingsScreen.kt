package com.gym.tracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gym.tracker.R
import com.gym.tracker.backup.GitHubBackup
import com.gym.tracker.backup.GitHubError
import com.gym.tracker.data.BackupPreferences
import com.gym.tracker.ui.components.GymCard
import com.gym.tracker.ui.theme.GymBackground
import com.gym.tracker.ui.theme.GymTheme
import com.gym.tracker.ui.theme.GymThemeId
import com.gym.tracker.ui.theme.GymThemePreviewProvider
import com.gym.tracker.ui.theme.HeaderDecor
import com.gym.tracker.ui.theme.LocalGymTokens
import com.gym.tracker.ui.theme.ScreenTitle
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val form by vm.form.collectAsStateWithLifecycle()
    val check by vm.check.collectAsStateWithLifecycle()
    val upload by vm.upload.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.export_share_title)
    var sharing by rememberSaveable { mutableStateOf(false) }
    SettingsContent(
        form = form,
        check = check,
        upload = upload,
        status = status,
        sharing = sharing,
        onRepo = vm::setRepo,
        onToken = vm::setToken,
        onCheck = vm::checkConnection,
        onUpload = vm::uploadNow,
        onShare = {
            if (!sharing) {
                sharing = true
                scope.launch {
                    try {
                        CsvShare.share(context, vm.csvSnapshot(), chooserTitle)
                    } finally {
                        sharing = false
                    }
                }
            }
        },
        onBack = onBack,
    )
}

/** Содержимое экрана без ViewModel (для @Preview). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    form: SettingsViewModel.Form?,
    check: SettingsViewModel.Check,
    upload: SettingsViewModel.Upload,
    status: BackupPreferences.Status?,
    sharing: Boolean,
    onRepo: (String) -> Unit,
    onToken: (String) -> Unit,
    onCheck: () -> Unit,
    onUpload: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = { ScreenTitle(stringResource(R.string.settings_title), fallback = MaterialTheme.typography.titleLarge, maxLines = 1) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderDecor(hudText = stringResource(R.string.settings_title))
            Section(stringResource(R.string.backup_section)) {
                BackupBody(form, check, upload, status, onRepo, onToken, onCheck, onUpload)
            }
            Section(stringResource(R.string.export_section)) {
                Text(
                    stringResource(R.string.export_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onShare,
                    enabled = !sharing,
                    shape = LocalGymTokens.current.buttonShape ?: ButtonDefaults.outlinedShape,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(stringResource(R.string.export_share), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val tokens = LocalGymTokens.current
    GymCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (tokens.groupUppercase) title.uppercase() else title,
                style = tokens.groupStyle ?: MaterialTheme.typography.titleMedium,
            )
            content()
        }
    }
}

@Composable
private fun BackupBody(
    form: SettingsViewModel.Form?,
    check: SettingsViewModel.Check,
    upload: SettingsViewModel.Upload,
    status: BackupPreferences.Status?,
    onRepo: (String) -> Unit,
    onToken: (String) -> Unit,
    onCheck: () -> Unit,
    onUpload: () -> Unit,
) {
    val tokens = LocalGymTokens.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodyMedium, color = secondary)
    if (form != null) BackupFields(form, onRepo, onToken)
    val configured = form != null && form.repo.isNotBlank() && form.token.isNotBlank()
    Button(
        onClick = onCheck,
        enabled = configured && check != SettingsViewModel.Check.Checking,
        shape = tokens.buttonShape ?: ButtonDefaults.shape,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(
            stringResource(if (check == SettingsViewModel.Check.Checking) R.string.backup_checking else R.string.backup_check),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    when (check) {
        is SettingsViewModel.Check.Connected -> {
            Text(stringResource(R.string.backup_connected), style = MaterialTheme.typography.titleMedium, color = tokens.doneIcon)
            expiryText(check.tokenExpires)?.let {
                Text(stringResource(R.string.backup_token_expires, it), style = MaterialTheme.typography.bodyMedium, color = secondary)
            }
        }
        is SettingsViewModel.Check.Failed -> Text(
            errorText(check.kind).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        else -> Unit
    }
    if (form != null && !configured) {
        Text(stringResource(R.string.backup_disabled), style = MaterialTheme.typography.bodyMedium, color = secondary)
    }
    if (status != null) {
        val last = status.lastUploadAt?.let { LAST_UPLOAD_FORMAT.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
        Text(
            if (last == null) stringResource(R.string.backup_never) else stringResource(R.string.backup_status, last, status.lastCount ?: 0),
            style = MaterialTheme.typography.bodyMedium,
            color = secondary,
        )
        status.errorCode?.let { code ->
            val kind = GitHubError.Kind.entries.firstOrNull { it.name == code }
            if (kind != null) {
                Text(
                    stringResource(R.string.backup_last_error, errorText(kind)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    OutlinedButton(
        onClick = onUpload,
        enabled = configured && upload != SettingsViewModel.Upload.Working,
        shape = tokens.buttonShape ?: ButtonDefaults.outlinedShape,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(
            stringResource(if (upload == SettingsViewModel.Upload.Working) R.string.backup_uploading else R.string.backup_upload),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    if (upload is SettingsViewModel.Upload.Done) {
        val text = when (upload.outcome) {
            GitHubBackup.Outcome.UPLOADED -> stringResource(R.string.backup_uploaded)
            GitHubBackup.Outcome.UNCHANGED -> stringResource(R.string.backup_unchanged)
            GitHubBackup.Outcome.NOT_CONFIGURED -> stringResource(R.string.backup_disabled)
            // Причина — в строке «Последняя ошибка» выше.
            GitHubBackup.Outcome.RETRY, GitHubBackup.Outcome.FAILED -> null
        }
        if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BackupFields(form: SettingsViewModel.Form, onRepo: (String) -> Unit, onToken: (String) -> Unit) {
    var showToken by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = form.repo,
        onValueChange = onRepo,
        label = { Text(stringResource(R.string.backup_repo)) },
        placeholder = { Text(BackupPreferences.DEFAULT_REPO) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.token,
        onValueChange = onToken,
        label = { Text(stringResource(R.string.backup_token)) },
        singleLine = true,
        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { showToken = !showToken }) {
                Icon(
                    if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(if (showToken) R.string.backup_token_hide else R.string.backup_token_show),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Понятное сообщение по виду ошибки GitHub (со строчной буквы — вставляется во фразы). */
@Composable
private fun errorText(kind: GitHubError.Kind): String = stringResource(
    when (kind) {
        GitHubError.Kind.NETWORK -> R.string.backup_error_network
        GitHubError.Kind.SERVER -> R.string.backup_error_server
        GitHubError.Kind.BAD_TOKEN -> R.string.backup_error_bad_token
        GitHubError.Kind.EXPIRED -> R.string.backup_error_expired
        GitHubError.Kind.NO_ACCESS -> R.string.backup_error_no_access
        GitHubError.Kind.NO_WRITE -> R.string.backup_error_no_write
        GitHubError.Kind.BAD_REPO -> R.string.backup_error_bad_repo
        GitHubError.Kind.CONFLICT -> R.string.backup_error_conflict
    },
)

/** «2027-11-20» → «20.11.2027». */
private fun expiryText(date: String?): String? =
    date?.let { runCatching { EXPIRY_FORMAT.format(LocalDate.parse(it)) }.getOrNull() }

private val LAST_UPLOAD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
private val EXPIRY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@Preview(name = "Настройки — темы", widthDp = 360, heightDp = 900)
@Composable
private fun SettingsPreview(@PreviewParameter(GymThemePreviewProvider::class) themeId: GymThemeId) {
    GymTheme(themeId) {
        GymBackground {
            SettingsContent(
                form = SettingsViewModel.Form(BackupPreferences.DEFAULT_REPO, "github_pat_example"),
                check = SettingsViewModel.Check.Connected("2027-11-20"),
                upload = SettingsViewModel.Upload.Done(GitHubBackup.Outcome.UPLOADED),
                status = BackupPreferences.Status(1_763_640_000_000, 154, GitHubError.Kind.NETWORK.name),
                sharing = false,
                onRepo = {}, onToken = {}, onCheck = {}, onUpload = {}, onShare = {}, onBack = {},
            )
        }
    }
}
