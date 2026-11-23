package com.pobla.cuaderno.cloud

/**
 * Datos públicos del proyecto Firebase (los mismos que google-services.json).
 * No son secretos: la seguridad la dan las reglas de Firestore (firestore.rules).
 */
object FirebaseConfig {
    const val PROJECT_ID = "cuaderno-de-bolsillo"
    const val ANDROID_APP_ID = "1:1087631261659:android:c0bd58c52453c01c5f31fe"
    const val API_KEY = "AIzaSyDHA2E9fhTmq-C56Nxlc_kcwKpw65g5hDQ"
    const val SENDER_ID = "1087631261659"
    const val STORAGE_BUCKET = "cuaderno-de-bolsillo.firebasestorage.app"

    /** Cliente OAuth "web" (tipo 3): lo pide el login con Google para obtener el ID token. */
    const val WEB_CLIENT_ID = "1087631261659-ue8ltdqachqo8552mrkqesv0thms03pr.apps.googleusercontent.com"
}
