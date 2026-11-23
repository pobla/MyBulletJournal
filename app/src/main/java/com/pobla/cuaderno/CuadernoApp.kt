package com.pobla.cuaderno

import android.app.Application
import com.pobla.cuaderno.data.AppSettings
import com.pobla.cuaderno.data.JournalRepository
import com.pobla.cuaderno.data.db.AppDatabase
import com.pobla.cuaderno.reminders.Notifications
import com.pobla.cuaderno.reminders.ReminderScheduler
import com.pobla.cuaderno.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import androidx.glance.appwidget.updateAll
import com.pobla.cuaderno.widget.TaskWidget
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import com.pobla.cuaderno.cloud.CloudSync

/** Contenedor de dependencias manual (sin Hilt, para mantenerlo sencillo). */
class AppContainer(app: Application) {
    val database: AppDatabase = AppDatabase.build(app)
    val repository = JournalRepository(database)

    /** Para escrituras que deben terminar aunque se cierre la pantalla. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = AppSettings(app)
    val updates = UpdateManager(app, settings, appScope)
    val reminders = ReminderScheduler(app, settings)
    val cloud = CloudSync(app, database, settings)
}

class CuadernoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        refreshWidgetOnChanges()
        container.cloud.init()
        syncOnChanges()
    }

    /** Cualquier cambio en los datos programa una copia en la nube (si hay sesión iniciada). */
    @OptIn(FlowPreview::class)
    private fun syncOnChanges() {
        container.appScope.launch {
            container.database.journalDao().observeAnyChange()
                .debounce(3_000)
                .collect { container.cloud.requestSync() }
        }
    }

    /** Cada cambio en las entradas (desde la app o el propio widget) repinta el widget. */
    @OptIn(FlowPreview::class)
    private fun refreshWidgetOnChanges() {
        container.appScope.launch {
            container.repository.observeEntriesChanges()
                .debounce(500)
                .collect { runCatching { TaskWidget().updateAll(this@CuadernoApp) } }
        }
    }
}
