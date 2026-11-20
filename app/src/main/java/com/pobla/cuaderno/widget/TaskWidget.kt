package com.pobla.cuaderno.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.pobla.cuaderno.CuadernoApp
import com.pobla.cuaderno.MainActivity
import com.pobla.cuaderno.R
import com.pobla.cuaderno.model.EntryType
import com.pobla.cuaderno.model.TaskStatus
import com.pobla.cuaderno.model.symbolFor
import com.pobla.cuaderno.reminders.Notifications
import com.pobla.cuaderno.util.weekdayDayMonth
import java.time.LocalDate

private data class WidgetEntry(val id: Long, val symbol: String, val text: String)

/** Widget "Hoy": tareas y eventos de hoy, y botones para añadir sin abrir la app. */
class TaskWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val repository = (context.applicationContext as CuadernoApp).container.repository
        // Lo pendiente primero: tareas abiertas y eventos; lo hecho o migrado no ocupa sitio.
        val entries = repository.getEntries(today)
            .filter { it.type != EntryType.TASK || it.status == TaskStatus.OPEN }
            .map { WidgetEntry(it.id, symbolFor(it.type, it.status), it.text) }
        val title = today.weekdayDayMonth()

        provideContent {
            GlanceTheme {
                WidgetContent(context, title, entries)
            }
        }
    }
}

class TaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskWidget()
}

private fun quickAddIntent(context: Context, type: EntryType) =
    Intent(context, QuickAddActivity::class.java)
        .putExtra(QuickAddActivity.EXTRA_TYPE, type.name)
        // URI distinta por tipo para que Android no confunda los tres botones.
        .setData(Uri.parse("cuaderno://quickadd/${type.name.lowercase()}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

private fun openDayIntent(context: Context) =
    Intent(context, MainActivity::class.java)
        .putExtra(Notifications.EXTRA_TAB, Notifications.TAB_DAY)
        .setData(Uri.parse("cuaderno://day/today"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

@Composable
private fun WidgetContent(context: Context, title: String, entries: List<WidgetEntry>) {
    val colors = GlanceTheme.colors
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(14.dp),
    ) {
        // Cabecera: toca para abrir el día
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(openDayIntent(context))),
        ) {
            Text(
                text = title,
                style = TextStyle(color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (entries.isNotEmpty()) {
                Text(
                    text = "${entries.size}",
                    style = TextStyle(color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        Spacer(GlanceModifier.height(6.dp))

        if (entries.isEmpty()) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth()
                    .clickable(actionStartActivity(openDayIntent(context))),
            ) {
                Text(
                    text = context.getString(R.string.widget_empty),
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(entries, itemId = { it.id }) { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable(actionStartActivity(openDayIntent(context))),
                    ) {
                        Text(
                            text = entry.symbol,
                            style = TextStyle(color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.width(18.dp),
                        )
                        Text(
                            text = entry.text,
                            style = TextStyle(color = colors.onSurface, fontSize = 14.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(GlanceModifier.height(8.dp))
        // Botones: + Tarea grande, ○ evento y – nota
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(44.dp)
                    .cornerRadius(22.dp)
                    .background(colors.primary)
                    .clickable(actionStartActivity(quickAddIntent(context, EntryType.TASK))),
            ) {
                Text(
                    text = context.getString(R.string.widget_add_task),
                    style = TextStyle(color = colors.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            RoundButton(context, EntryType.EVENT)
            Spacer(GlanceModifier.width(8.dp))
            RoundButton(context, EntryType.NOTE)
        }
    }
}

@Composable
private fun RoundButton(context: Context, type: EntryType) {
    val colors = GlanceTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier
            .size(44.dp)
            .cornerRadius(22.dp)
            .background(colors.secondaryContainer)
            .clickable(actionStartActivity(quickAddIntent(context, type))),
    ) {
        Text(
            text = type.symbol,
            style = TextStyle(color = colors.onSecondaryContainer, fontSize = 18.sp, fontWeight = FontWeight.Bold),
        )
    }
}
