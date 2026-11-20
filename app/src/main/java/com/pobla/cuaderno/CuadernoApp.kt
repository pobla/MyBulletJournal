package com.pobla.cuaderno

import android.app.Application
import com.pobla.cuaderno.data.JournalRepository
import com.pobla.cuaderno.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Contenedor de dependencias manual (sin Hilt, para mantenerlo sencillo). */
class AppContainer(app: Application) {
    val database: AppDatabase = AppDatabase.build(app)
    val repository = JournalRepository(database)

    /** Para escrituras que deben terminar aunque se cierre la pantalla. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class CuadernoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
