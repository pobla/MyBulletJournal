package com.pobla.cuaderno.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pobla.cuaderno.R
import com.pobla.cuaderno.update.ReleaseNote
import com.pobla.cuaderno.util.formatTimestamp

/** Lista de cambios: título en negrita, fecha y sus puntos. Con scroll propio. */
@Composable
fun ReleaseNotesList(notes: List<ReleaseNote>, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        notes.forEach { note ->
            Column {
                Text(note.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    formatTimestamp(note.time * 1000),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                note.bullets.forEach { bullet ->
                    Row(Modifier.padding(top = 2.dp)) {
                        Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(16.dp))
                        Text(bullet, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun WhatsNewDialog(versionName: String, notes: List<ReleaseNote>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new_in, versionName)) },
        text = { ReleaseNotesList(notes) },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.got_it)) } },
    )
}
