package com.sagapmo.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sagapmo.app.reading.Reading

@Composable internal fun LocationDescriptionEditor(reading: Reading, saving: Boolean, failed: Boolean, onChange: (String) -> Unit) {
    var draft by rememberSaveable(reading.id) { mutableStateOf(reading.locationDescription) }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = draft, onValueChange = { draft = it.take(200); onChange(draft) },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            label = { Text("Location description") }, placeholder = { Text("e.g. Outside CICS, beside the entrance") },
            supportingText = { Text("Optional - ${draft.length}/200 characters") })
        Text(if (failed) "Couldn't save description. Your text is still here." else if (saving) "Saving description?" else "Saved automatically",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (failed) TextButton({ onChange(draft) }) { Text("Retry") }
    }
}
