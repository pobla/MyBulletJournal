# Cuaderno de bolsillo

App Android (Kotlin + Jetpack Compose, Material 3, minSdk 26) para apuntar cosas cuando no tienes el bullet journal a mano y pasarlas luego al cuaderno.

**Idiomas:** inglés (por defecto) y español. Sigue el idioma del móvil; en Android 13+ se puede elegir solo para esta app en *Ajustes → Idioma de la app*.

## Instalar en el móvil (sin ordenador)

Cada push a GitHub compila un APK debug firmado (workflow `.github/workflows/android.yml`):

1. **Lo más cómodo:** en el repo, abre *Releases* → **debug-latest** y descarga `cuaderno-de-bolsillo-N.apk`. Se actualiza en cada push.
2. **Alternativa:** pestaña *Actions* → última ejecución verde → *Artifacts* (descarga un .zip con el APK dentro).
3. Abre el .apk y permite "instalar apps desconocidas" para el navegador/gestor de archivos la primera vez.

Todas las compilaciones se firman con la misma clave debug (`keystore/debug.keystore`, contraseña `android`), así que cada APK nuevo se instala **encima** del anterior sin perder datos. Es una clave de depuración: no la uses para publicar en Play Store.

También puedes lanzar una compilación a mano: *Actions* → *APK debug* → *Run workflow*.

### Actualizaciones automáticas

Desde la versión #5, la app comprueba **una vez al día al abrirse** si hay un APK más nuevo en la release `debug-latest` y lo ofrece. Se descarga dentro de la app y Android pide confirmar la instalación (la primera vez, también permitir "instalar apps desconocidas" para Cuaderno de bolsillo). También se puede buscar a mano en *Ajustes → Actualizaciones*. Requiere que el repo sea público.

### Notas de versión

El workflow genera `changelog.tsv` a partir de los mensajes de commit (título + líneas que empiezan por `- ` del cuerpo; se omiten los commits "README…"). Va dentro del APK y adjunto a la release:
- El aviso de actualización muestra qué trae la versión nueva respecto a la instalada.
- Tras actualizar, el diálogo **Novedades** enseña los cambios desde la versión anterior (aunque te saltes alguna).
- *Ajustes → Actualizaciones → Ver novedades* para volver a verlas.

Por eso conviene escribir los commits pensando en que son las notas de versión.

## Fases

| Fase | Contenido | Estado |
|------|-----------|--------|
| 1 | Estructura, workflow, Room, pantalla del día (navegación, hábitos, registro •○–, frase del día, "pasado al cuaderno" con resaltado de cambios) | ✅ |
| 2 | Vista semanal, lista de días pendientes, edición de hábitos, navegación inferior | ✅ |
| 2.5 | Estilo Material 3 Expressive, colores del fondo de pantalla (Android 12+), animaciones, deslizar entre días, actualizador integrado | ✅ |
| 3 | Fotos desde cámara o galería (por entrada o por día) | ⏳ |
| 4 | Notificaciones con WorkManager (hábitos y pasar al cuaderno), permisos Android 13+ | ✅ |

## Uso rápido

- Escribe abajo y pulsa enviar. Por defecto es **tarea (•)**. Empieza con `o ` para **evento (○)** o `- ` para **nota (–)**. También puedes tocar el símbolo a la izquierda del campo para cambiar el tipo.
- Toca el **símbolo** de una tarea para ciclar • → × (hecha) → > (migrada) → •.
- Toca el **texto** de una entrada para editarla, cambiar su tipo/estado o borrarla.
- **Semana**: rejilla de hábitos (se puede marcar directamente) y resumen de cada día; toca un día para abrirlo.
- **Pendientes**: días con apuntes sin pasar al cuaderno, del más antiguo al más reciente. ✓ los marca como pasados.
- **Recordatorios** (Ajustes): uno para hábitos y otro para pasar al cuaderno, a la hora y en los días de la semana que elijas (p. ej. L–V a las 9:00). Solo avisan si hace falta (te falta algún hábito / hay días pendientes). Tocar la notificación abre el día o la lista de pendientes.
- **Importar texto**: comparte texto desde cualquier app (p. ej. una respuesta de Claude) a *Cuaderno de bolsillo*, o usa *⋮ → Importar texto* en la vista del día. Reconoce • ○ – × >, casillas y listas numeradas; líneas como "Mañana", "Lunes" o "23/11" cambian el día. "× Creatina" marca el hábito. Vista previa para desmarcar o cambiar tipos antes de guardar. El botón "Copiar instrucciones y abrir Claude" le pasa a Claude el formato.
- **Ajustes**: añadir, renombrar, reordenar y quitar hábitos (se conserva el historial).
- "Ya lo pasé" marca el día como pasado al cuaderno. Si después cambias algo, vuelve a pendiente y lo nuevo se resalta en naranja.

## Arquitectura

- `data/db`: Room (`days`, `habits`, `habit_checks`, `entries`, `photos`). Fechas como `epochDay`.
- `data/JournalRepository`: única puerta de escritura; cualquier cambio marca el día como pendiente.
- `ui/AppRoot`: navegación inferior (Día · Semana · Pendientes · Ajustes).
- `ui/day`, `ui/week`, `ui/pending`, `ui/settings`: un ViewModel (StateFlow) y una pantalla Compose por sección.
- DI manual en `CuadernoApp`/`AppContainer`.
