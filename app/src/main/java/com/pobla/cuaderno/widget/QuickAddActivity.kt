@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.pobla.cuaderno.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pobla.cuaderno.CuadernoApp
import com.pobla.cuaderno.R
import com.pobla.cuaderno.model.EntryShortcuts
import com.pobla.cuaderno.model.EntryType
import com.pobla.cuaderno.ui.theme.CuadernoTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Hoja pequeña sobre la pantalla de inicio para apuntar algo en hoy sin abrir la app.
 * Se abre desde el widget; tocar fuera o "atrás" la cierra.
 */
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CuadernoApp).container
        val initialType = intent.getStringExtra(EXTRA_TYPE)
            ?.let { name -> EntryType.entries.firstOrNull { it.name == name } }
            ?: EntryType.TASK

        setContent {
            val dynamicColor by container.settings.dynamicColor.collectAsStateWithLifecycle()
            CuadernoTheme(dynamicColor = dynamicColor) {
                QuickAddSheet(
                    initialType = initialType,
                    onDismiss = { finish() },
                    onSave = { type, text ->
                        container.appScope.launch { container.repository.addEntry(LocalDate.now(), type, text) }
                        Toast.makeText(this, R.string.quick_added, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_TYPE = "tipo"
    }
}

@Composable
private fun QuickAddSheet(
    initialType: EntryType,
    onDismiss: () -> Unit,
    onSave: (EntryType, String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(initialType) }
    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme

    fun submit() {
        val clean = text.trim()
        if (clean.isNotEmpty()) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onSave(type, clean)
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Fondo semitransparente: tocarlo cierra la hoja
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier
                .fillMaxWidth()
                // Evita que un toque dentro de la hoja la cierre
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Text(
                    stringResource(R.string.quick_add_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { type = type.next() }) {
                        AnimatedContent(
                            targetState = type,
                            transitionSpec = {
                                (scaleIn(motion.fastSpatialSpec(), initialScale = 0.3f) + fadeIn(motion.fastEffectsSpec()))
                                    .togetherWith(scaleOut(motion.fastSpatialSpec(), targetScale = 0.3f) + fadeOut(motion.fastEffectsSpec()))
                            },
                            label = "tipo",
                        ) { t ->
                            Text(t.symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TextField(
                        value = text,
                        onValueChange = { value ->
                            val match = EntryShortcuts.match(value)
                            if (match != null) {
                                type = match.type
                                text = match.rest
                            } else {
                                text = value
                            }
                        },
                        placeholder = {
                            Text(stringResource(R.string.input_placeholder, stringResource(type.labelRes)))
                        },
                        maxLines = 4,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.add))
                    }
                }
            }
        }
    }
}
