package com.pobla.cuaderno.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.pobla.cuaderno.R

enum class TranscribeStatus { NONE, PENDING, CHANGED, DONE }

fun transcribeStatus(hasContent: Boolean, transcribed: Boolean, lastTranscribedAt: Long?): TranscribeStatus =
    when {
        transcribed -> TranscribeStatus.DONE
        lastTranscribedAt != null -> TranscribeStatus.CHANGED
        hasContent -> TranscribeStatus.PENDING
        else -> TranscribeStatus.NONE
    }

/** Etiqueta pequeña con el estado "pasado al cuaderno". */
@Composable
fun StatusPill(status: TranscribeStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (text, container, content) = when (status) {
        TranscribeStatus.NONE -> return
        TranscribeStatus.DONE ->
            Triple(stringResource(R.string.pill_done), colors.secondaryContainer, colors.onSecondaryContainer)
        TranscribeStatus.CHANGED ->
            Triple(stringResource(R.string.pill_changed), colors.tertiaryContainer, colors.onTertiaryContainer)
        TranscribeStatus.PENDING ->
            Triple(stringResource(R.string.pill_pending), colors.surfaceVariant, colors.onSurfaceVariant)
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}
