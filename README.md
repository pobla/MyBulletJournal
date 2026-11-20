# Cuaderno de bolsillo

App Android (Kotlin + Jetpack Compose, Material 3, minSdk 26) para apuntar cosas cuando no tienes el bullet journal a mano y pasarlas luego al cuaderno.

## Instalar en el móvil (sin ordenador)

Cada push a GitHub compila un APK debug firmado (workflow `.github/workflows/android.yml`):

1. **Lo más cómodo:** en el repo, abre *Releases* → **debug-latest** y descarga `cuaderno-de-bolsillo-N.apk`. Se actualiza en cada push.
2. **Alternativa:** pestaña *Actions* → última ejecución verde → *Artifacts* (descarga un .zip con el APK dentro).
3. Abre el .apk y permite "instalar apps desconocidas" para el navegador/gestor de archivos la primera vez.

Todas las compilaciones se firman con la misma clave debug (`keystore/debug.keystore`, contraseña `android`), así que cada APK nuevo se instala **encima** del anterior sin perder datos. Es una clave de depuración: no la uses para publicar en Play Store.

También puedes lanzar una compilación a mano: *Actions* → *APK debug* → *Run workflow*.

## Fases

| Fase | Contenido | Estado |
|------|-----------|--------|
| 1 | Estructura, workflow, Room, pantalla del día (navegación, hábitos, registro •○–, frase del día, "pasado al cuaderno" con resaltado de cambios) | ✅ |
| 2 | Vista semanal, lista de días pendientes, edición de hábitos, navegación inferior | ✅ |
| 3 | Fotos desde cámara o galería (por entrada o por día) | ⏳ |
| 4 | Notificaciones con WorkManager (hábitos y pasar al cuaderno), permisos Android 13+ | ⏳ |

## Uso rápido

- Escribe abajo y pulsa enviar. Por defecto es **tarea (•)**. Empieza con `o ` para **evento (○)** o `- ` para **nota (–)**. También puedes tocar el símbolo a la izquierda del campo para cambiar el tipo.
- Toca el **símbolo** de una tarea para ciclar • → × (hecha) → > (migrada) → •.
- Toca el **texto** de una entrada para editarla, cambiar su tipo/estado o borrarla.
- **Semana**: rejilla de hábitos (se puede marcar directamente) y resumen de cada día; toca un día para abrirlo.
- **Pendientes**: días con apuntes sin pasar al cuaderno, del más antiguo al más reciente. ✓ los marca como pasados.
- **Ajustes**: añadir, renombrar, reordenar y quitar hábitos (se conserva el historial).
- "Ya lo pasé" marca el día como pasado al cuaderno. Si después cambias algo, vuelve a pendiente y lo nuevo se resalta en naranja.

## Arquitectura

- `data/db`: Room (`days`, `habits`, `habit_checks`, `entries`, `photos`). Fechas como `epochDay`.
- `data/JournalRepository`: única puerta de escritura; cualquier cambio marca el día como pendiente.
- `ui/AppRoot`: navegación inferior (Día · Semana · Pendientes · Ajustes).
- `ui/day`, `ui/week`, `ui/pending`, `ui/settings`: un ViewModel (StateFlow) y una pantalla Compose por sección.
- DI manual en `CuadernoApp`/`AppContainer`.
