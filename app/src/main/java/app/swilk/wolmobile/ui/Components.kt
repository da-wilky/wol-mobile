package app.swilk.wolmobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.swilk.wolmobile.R
import app.swilk.wolmobile.data.Action
import app.swilk.wolmobile.data.ActionType
import app.swilk.wolmobile.data.HostStatus
import app.swilk.wolmobile.run.formatAge

fun parsePort(s: String): Int? = s.toIntOrNull()?.takeIf { it in 1..65535 }

/** [deleteWarning] is shown in the delete confirmation; null hides the delete button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScaffold(
    title: String,
    onBack: () -> Unit,
    canSave: Boolean = false,
    onSave: (() -> Unit)? = null,
    deleteWarning: String? = null,
    onDelete: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), "Back") }
                },
                actions = {
                    if (deleteWarning != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(painterResource(R.drawable.ic_delete), "Delete")
                        }
                    }
                    if (onSave != null) {
                        Button(onClick = onSave, enabled = canSave, modifier = Modifier.padding(end = 8.dp)) { Text("Save") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete?") },
            text = { Text(deleteWarning.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun FormSection(title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            hint?.let { Hint(it) }
            content()
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    error: Boolean = false,
    supporting: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Accepts hex digits only and inserts the colons itself. */
@Composable
fun MacField(value: String, onChange: (String) -> Unit) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }
    val complete = value.isEmpty() || value.length == 17
    OutlinedTextField(
        value = field,
        onValueChange = { new ->
            val hex = new.text.filter { it.digitToIntOrNull(16) != null }.uppercase().take(12)
            val formatted = hex.chunked(2).joinToString(":")
            val cursor = if (new.selection.end >= new.text.length) formatted.length else new.selection.end.coerceAtMost(formatted.length)
            field = TextFieldValue(formatted, TextRange(cursor))
            onChange(formatted)
        },
        label = { Text("MAC address") },
        placeholder = { Text("AA:BB:CC:DD:EE:FF") },
        isError = !complete,
        supportingText = if (complete) null else ({ Text("12 hex digits") }),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Picker(
    label: String,
    options: List<Pair<T, String>>,
    selected: T?,
    noneLabel: String? = null,
    onSelect: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.find { it.first == selected }?.second ?: noneLabel.orEmpty()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            noneLabel?.let {
                DropdownMenuItem(text = { Text(it) }, onClick = { onSelect(null); expanded = false })
            }
            options.forEach { (value, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, description: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let { Hint(it) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun IconBadge(@DrawableRes icon: Int, container: Color = MaterialTheme.colorScheme.secondaryContainer, content: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(shape = CircleShape, color = container, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun StatusChip(status: HostStatus) {
    val color = if (status.up) upColor else MaterialTheme.colorScheme.outline
    val age = formatAge(System.currentTimeMillis() - status.checkedAt)
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(
                (if (status.up) "Up" else "Down") + " · $age",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

class EmptyAction(val label: String, val onClick: () -> Unit)

@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, text: String, vararg actions: EmptyAction) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(36.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        actions.forEachIndexed { i, action ->
            if (i == 0) Button(onClick = action.onClick) { Text(action.label) } else OutlinedButton(onClick = action.onClick) { Text(action.label) }
        }
    }
}

@Composable
fun ConfirmRunDialog(action: Action, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(if (action.type == ActionType.WAKE) R.drawable.ic_power else R.drawable.ic_terminal), null) },
        title = { Text("Run \"${action.label}\"?") },
        text = { if (action.type == ActionType.SSH) Text(action.command.orEmpty(), fontFamily = FontFamily.Monospace) },
        confirmButton = { Button(onClick = onConfirm) { Text("Run") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
