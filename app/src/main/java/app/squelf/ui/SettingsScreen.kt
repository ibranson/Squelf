package app.squelf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.squelf.data.SettingsRepository
import app.squelf.data.SquelfSettings

@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initial = remember { repository.load() }
    var host by remember { mutableStateOf(initial.nasHost) }
    var port by remember { mutableStateOf(initial.nasPort.toString()) }
    var username by remember { mutableStateOf(initial.nasUsername) }
    var password by remember { mutableStateOf(initial.nasPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf(initial.nasFolder) }
    var autoDated by remember { mutableStateOf(initial.autoDatedFolder) }
    var wifiOnly by remember { mutableStateOf(initial.wifiOnlyUpload) }
    var isoAuto by remember { mutableStateOf(initial.isoAuto) }
    var showThumbnail by remember { mutableStateOf(initial.showThumbnail) }
    var burstFps by remember { mutableStateOf(initial.burstFps) }
    var burstCount by remember { mutableStateOf(initial.burstCount) }

    BackHandler { onDone() }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Text("Synology NAS", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host (e.g. myid.quickconnect.to)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = port,
            onValueChange = { new -> port = new.filter(Char::isDigit).take(5) },
            label = { Text("Port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "HIDE" else "SHOW")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = folder,
            onValueChange = { folder = it },
            label = { Text("Target folder (e.g. /home/Squelf)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Auto-dated subfolder (YYYY-MM-DD)",
                modifier = Modifier.weight(1f)
            )
            Switch(checked = autoDated, onCheckedChange = { autoDated = it })
        }

        HorizontalDivider()

        Text("Upload", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Wi-Fi only", modifier = Modifier.weight(1f))
            Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
        }

        HorizontalDivider()

        Text("Camera", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("ISO auto", modifier = Modifier.weight(1f))
            Switch(checked = isoAuto, onCheckedChange = { isoAuto = it })
        }

        HorizontalDivider()

        Text("Viewfinder", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Show thumbnail after capture", modifier = Modifier.weight(1f))
            Switch(checked = showThumbnail, onCheckedChange = { showThumbnail = it })
        }

        HorizontalDivider()

        Text("Remote burst", style = MaterialTheme.typography.titleMedium)
        Text(
            "Press B once to fire a burst at the count and rate below.",
            style = MaterialTheme.typography.bodySmall
        )
        Text("Rate", style = MaterialTheme.typography.labelMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(1, 2, 3, 5).forEach { fps ->
                val selected = burstFps == fps
                FilledTonalButton(
                    onClick = { burstFps = fps },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("${fps} fps")
                }
            }
        }
        Text("Count", style = MaterialTheme.typography.labelMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(2, 3, 5, 10).forEach { count ->
                val selected = burstCount == count
                FilledTonalButton(
                    onClick = { burstCount = count },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("$count")
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    repository.save(
                        SquelfSettings(
                            nasHost = host.trim(),
                            nasPort = port.toIntOrNull() ?: 5001,
                            nasUsername = username.trim(),
                            nasPassword = password,
                            nasFolder = folder.trim(),
                            autoDatedFolder = autoDated,
                            wifiOnlyUpload = wifiOnly,
                            isoAuto = isoAuto,
                            showThumbnail = showThumbnail,
                            burstFps = burstFps,
                            burstCount = burstCount
                        )
                    )
                    onDone()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
        }
    }
}
