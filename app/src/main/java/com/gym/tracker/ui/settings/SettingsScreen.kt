package com.gym.tracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gym.tracker.R
import com.gym.tracker.sync.SheetError
import com.gym.tracker.sync.SheetSync
import com.gym.tracker.ui.components.GymCard
import com.gym.tracker.ui.theme.GymBackground
import com.gym.tracker.ui.theme.GymTheme
import com.gym.tracker.ui.theme.GymThemeId
import com.gym.tracker.ui.theme.GymThemePreviewProvider
import com.gym.tracker.ui.theme.HeaderDecor
import com.gym.tracker.ui.theme.LocalGymTokens
import com.gym.tracker.ui.theme.ScreenTitle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val form by vm.form.collectAsStateWithLifecycle()
    val check by vm.check.collectAsStateWithLifecycle()
    val export by vm.export.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    SettingsContent(
        form = form,
        check = check,
        export = export,
        status = status,
        onUrl = vm::setUrl,
        onToken = vm::setToken,
        onCheck = vm::checkConnection,
        onExport = vm::exportAll,
        onBack = onBack,
    )
}

/** Содержимое экрана без ViewModel (для @Preview). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    form: SettingsViewModel.Form?,
    check: SettingsViewModel.Check,
    export: SettingsViewModel.Export,
    status: SettingsViewModel.Status?,
    onUrl: (String) -> Unit,
    onToken: (String) -> Unit,
    onCheck: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
) {
    val tokens = LocalGymTokens.current
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
                .verticalScroll(rememberScrollState()),
        ) {
            HeaderDecor(hudText = stringResource(R.string.settings_title), modifier = Modifier.padding(bottom = 12.dp))
            GymCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.sync_section).let { if (tokens.groupUppercase) it.uppercase() else it },
                        style = tokens.groupStyle ?: MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.sync_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (form != null) {
                        SyncFields(form, onUrl, onToken)
                    }
                    Button(
                        onClick = onCheck,
                        enabled = form != null && check != SettingsViewModel.Check.Checking,
                        shape = tokens.buttonShape ?: ButtonDefaults.shape,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(
                            stringResource(if (check == SettingsViewModel.Check.Checking) R.string.sync_checking else R.string.sync_check),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    when (check) {
                        SettingsViewModel.Check.Connected -> Text(
                            stringResource(R.string.sync_connected),
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.doneIcon,
                        )
                        is SettingsViewModel.Check.Failed -> Text(
                            errorText(check.error.kind.name, check.error.detail).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> Unit
                    }
                    StatusLines(form, status)
                    OutlinedButton(
                        onClick = onExport,
                        enabled = export != SettingsViewModel.Export.Working,
                        shape = tokens.buttonShape ?: ButtonDefaults.outlinedShape,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(
                            stringResource(if (export == SettingsViewModel.Export.Working) R.string.sync_exporting else R.string.sync_export),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (export is SettingsViewModel.Export.Done) {
                        Text(
                            if (export.count == 0) stringResource(R.string.sync_export_empty) else stringResource(R.string.sync_export_done, export.count),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncFields(form: SettingsViewModel.Form, onUrl: (String) -> Unit, onToken: (String) -> Unit) {
    var showToken by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = form.url,
        onValueChange = onUrl,
        label = { Text(stringResource(R.string.sync_url)) },
        placeholder = { Text(stringResource(R.string.sync_url_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.token,
        onValueChange = onToken,
        label = { Text(stringResource(R.string.sync_token)) },
        singleLine = true,
        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { showToken = !showToken }) {
                Icon(
                    if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(if (showToken) R.string.sync_token_hide else R.string.sync_token_show),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StatusLines(form: SettingsViewModel.Form?, status: SettingsViewModel.Status?) {
    if (status == null) return
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    if (form != null && (form.url.isBlank() || form.token.isBlank())) {
        Text(stringResource(R.string.sync_disabled), style = MaterialTheme.typography.bodyMedium, color = secondary)
    }
    val last = status.lastSyncAt?.let { LAST_SYNC_FORMAT.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
        ?: stringResource(R.string.sync_never)
    Text(stringResource(R.string.sync_status, last, status.queued), style = MaterialTheme.typography.bodyMedium, color = secondary)
    if (status.errorCode != null) {
        Text(
            stringResource(R.string.sync_last_error, errorText(status.errorCode, status.errorDetail)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Понятное сообщение по коду ошибки ([SheetError.Kind]) и тексту от скрипта. */
@Composable
private fun errorText(code: String, detail: String?): String = when (code) {
    SheetError.Kind.NETWORK.name -> stringResource(R.string.sync_error_network)
    SheetError.Kind.UNAUTHORIZED.name -> stringResource(R.string.sync_error_unauthorized)
    SheetError.Kind.BAD_URL.name -> stringResource(R.string.sync_error_bad_url)
    else -> if (detail == SheetSync.NOT_APPLIED) {
        stringResource(R.string.sync_error_not_applied)
    } else {
        stringResource(R.string.sync_error_script, detail ?: code)
    }
}

private val LAST_SYNC_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")

@Preview(name = "Настройки — темы", widthDp = 360, heightDp = 760)
@Composable
private fun SettingsPreview(@PreviewParameter(GymThemePreviewProvider::class) themeId: GymThemeId) {
    GymTheme(themeId) {
        GymBackground {
            SettingsContent(
                form = SettingsViewModel.Form("https://script.google.com/macros/s/AKfy…/exec", "secret"),
                check = SettingsViewModel.Check.Connected,
                export = SettingsViewModel.Export.Done(42),
                status = SettingsViewModel.Status(1_763_640_000_000, SheetError.Kind.NETWORK.name, null, 3),
                onUrl = {}, onToken = {}, onCheck = {}, onExport = {}, onBack = {},
            )
        }
    }
}
