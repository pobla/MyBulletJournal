package com.pobla.cuaderno.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.pobla.cuaderno.R
import com.pobla.cuaderno.model.EntryType

/**
 * Widget para apuntar en hoy sin abrir la app, con el mismo aspecto que la hoja rápida:
 * - Una fila (por defecto 4×1): círculo •, píldora "Tarea… (o evento · - nota)" y enviar.
 * - 1×1: un círculo con +, como un icono más.
 * Tocar cualquier parte abre la hoja rápida con el teclado.
 */
class TaskWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, ROW))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val placeholder = context.getString(
            R.string.input_placeholder,
            context.getString(EntryType.TASK.labelRes),
        )
        provideContent {
            GlanceTheme {
                if (LocalSize.current.width >= ROW.width) {
                    BarContent(context, placeholder)
                } else {
                    CircleContent(context)
                }
            }
        }
    }

    companion object {
        private val SMALL = DpSize(40.dp, 40.dp)
        private val ROW = DpSize(180.dp, 40.dp)
    }
}

/** Widget "Hoy": la barra de una fila. */
class TaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskWidget()
}

/** Widget "Añadir tarea": el mismo, colocado en 1×1 (círculo con +). */
class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskWidget()
}

private fun quickAddIntent(context: Context, type: EntryType = EntryType.TASK) =
    Intent(context, QuickAddActivity::class.java)
        .putExtra(QuickAddActivity.EXTRA_TYPE, type.name)
        // URI distinta por tipo para que Android no confunda los PendingIntent.
        .setData(Uri.parse("cuaderno://quickadd/${type.name.lowercase()}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

private val CONTROL = 48.dp

/** Igual que la fila de la hoja rápida: • | Tarea… (o evento · - nota) | enviar. Sin fondo propio. */
@Composable
private fun BarContent(context: Context, placeholder: String) {
    val colors = GlanceTheme.colors
    val open = actionStartActivity(quickAddIntent(context))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
            .clickable(open),
    ) {
        // Círculo con el símbolo de tarea
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .size(CONTROL)
                .cornerRadius(CONTROL / 2)
                .background(colors.primary)
                .clickable(open),
        ) {
            Text(
                text = EntryType.TASK.symbol,
                style = TextStyle(color = colors.onPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        // Píldora con el texto de ayuda
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = GlanceModifier
                .defaultWeight()
                .height(CONTROL)
                .cornerRadius(CONTROL / 2)
                .background(colors.surfaceVariant)
                .padding(horizontal = 18.dp)
                .clickable(open),
        ) {
            Text(
                text = placeholder,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 15.sp),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        // Botón de enviar (abre la hoja igualmente)
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .size(CONTROL)
                .cornerRadius(CONTROL / 2)
                .background(colors.surfaceVariant)
                .clickable(open),
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_send),
                contentDescription = context.getString(R.string.add),
                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
                modifier = GlanceModifier.size(22.dp),
            )
        }
    }
}

/** 1×1: un círculo de verdad (del lado más corto de la celda), centrado, con un +. */
@Composable
private fun CircleContent(context: Context) {
    val colors = GlanceTheme.colors
    val cell = LocalSize.current
    val diameter = (minOf(cell.width, cell.height) - 8.dp).coerceIn(36.dp, 64.dp)
    Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .size(diameter)
                .cornerRadius(diameter / 2)
                .background(colors.primaryContainer)
                .clickable(actionStartActivity(quickAddIntent(context))),
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_add),
                contentDescription = context.getString(R.string.add),
                colorFilter = ColorFilter.tint(colors.onPrimaryContainer),
                modifier = GlanceModifier.size(diameter * 0.5f),
            )
        }
    }
}
