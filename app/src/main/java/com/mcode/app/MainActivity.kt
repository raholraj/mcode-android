package com.mcode.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppScreen()
                }
            }
        }
    }
}

@Composable
fun AppScreen(vm: AgentViewModel = viewModel()) {
    var showSettings by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lastDetail = vm.items.lastOrNull()?.detail

    LaunchedEffect(vm.items.size, lastDetail) {
        if (vm.items.isNotEmpty()) listState.animateScrollToItem(vm.items.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("mcode", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.clear() }) { Text("Clear") }
            TextButton(onClick = { showSettings = true }) { Text("Settings") }
        }
        HorizontalDivider()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (vm.items.isEmpty()) {
                Text(
                    if (vm.config.apiKey.isBlank() && !vm.config.baseUrl.contains("localhost"))
                        "Pehle Settings me provider aur API key daalo, phir apna kaam likho."
                    else "Kya banana hai? Apna task likho.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(vm.items, key = { it.id }) { ChatBubble(it) }
            }
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                maxLines = 5,
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (vm.busy) vm.stop() else {
                    vm.send(input)
                    input = ""
                }
            }) { Text(if (vm.busy) "Stop" else "Send") }
        }
    }

    vm.approval?.let { ap ->
        AlertDialog(
            onDismissRequest = { vm.answer(false) },
            title = { Text("Allow this action?") },
            text = {
                Text(
                    ap.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    maxLines = 15, overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = { TextButton(onClick = { vm.answer(true) }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { vm.answer(false) }) { Text("Deny") } },
        )
    }

    if (showSettings) {
        SettingsDialog(
            cfg = vm.config,
            onSave = { vm.saveConfig(it); showSettings = false },
            onTest = { vm.testTermux(); showSettings = false },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
fun ChatBubble(item: ChatItem) {
    val cs = MaterialTheme.colorScheme
    when (item.kind) {
        Kind.USER -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(color = cs.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                Text(item.text, Modifier.padding(12.dp))
            }
        }
        Kind.ASSISTANT -> Text(item.text)
        Kind.TOOL -> Surface(
            color = cs.surfaceVariant, shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(
                    item.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
                item.detail?.let {
                    Text(
                        it.take(700), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        maxLines = 12, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Kind.ERROR -> Text(item.text, color = cs.error)
    }
}

@Composable
fun SettingsDialog(cfg: Config, onSave: (Config) -> Unit, onTest: () -> Unit, onDismiss: () -> Unit) {
    var baseUrl by remember { mutableStateOf(cfg.baseUrl) }
    var apiKey by remember { mutableStateOf(cfg.apiKey) }
    var model by remember { mutableStateOf(cfg.model) }
    var workdir by remember { mutableStateOf(cfg.workdir) }
    var auto by remember { mutableStateOf(cfg.autoApprove) }
    var menu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text("Provider chuno") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        Presets.list.forEach { (n, u, m) ->
                            DropdownMenuItem(text = { Text(n) }, onClick = { baseUrl = u; model = m; menu = false })
                        }
                    }
                }
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, singleLine = true)
                OutlinedTextField(
                    apiKey, { apiKey = it }, label = { Text("API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true)
                OutlinedTextField(workdir, { workdir = it }, label = { Text("Project folder (Termux)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = auto, onCheckedChange = { auto = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Auto-approve (risky)")
                }
                TextButton(onClick = onTest) { Text("Test Termux connection") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(Config(baseUrl.trim(), apiKey.trim(), model.trim(), workdir.trim(), auto))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
