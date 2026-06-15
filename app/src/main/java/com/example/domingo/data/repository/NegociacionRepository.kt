package com.example.domingo.data.repository

import android.content.Context
import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class NegociacionRepository(private val context: Context) {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun obtenerUsuarioId(): String? = auth.currentUser?.uid

    fun obtenerDatosUsuario(uid: String, onExito: (DocumentSnapshot) -> Unit) {
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { onExito(it) }
    }

    fun escucharMensajesRealtime(chatId: String, onListaActualizada: (QuerySnapshot) -> Unit) {
        db.collection("negociaciones").document(chatId)
            .collection("mensajes")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ -> snapshot?.let { onListaActualizada(it) } }
    }

    fun escucharEstadoNegociacion(chatId: String, onCambio: (DocumentSnapshot) -> Unit) {
        db.collection("negociaciones").document(chatId)
            .addSnapshotListener { snapshot, _ -> snapshot?.let { onCambio(it) } }
    }

    fun obtenerDatosNegociacion(chatId: String, onExito: (DocumentSnapshot) -> Unit) {
        db.collection("negociaciones").document(chatId).get()
            .addOnSuccessListener { onExito(it) }
    }

    fun setDatosNegociacionPadre(chatId: String, data: Map<String, Any>) {
        db.collection("negociaciones").document(chatId)
            .update(data)
            .addOnFailureListener {
                db.collection("negociaciones").document(chatId)
                    .set(data, SetOptions.merge())
            }
    }

    fun agregarMensajeColeccion(chatId: String, mensaje: Map<String, Any>) {
        db.collection("negociaciones").document(chatId)
            .collection("mensajes").add(mensaje)
    }

    fun buscarTicketActivo(chatId: String, onExito: (QuerySnapshot) -> Unit, onFallo: (Exception) -> Unit) {
        db.collection("tickets").whereEqualTo("chatId", chatId).get()
            .addOnSuccessListener { onExito(it) }
            .addOnFailureListener { onFallo(it) }
    }

    fun cambiarEstadoOfertaDocumento(chatId: String, timestamp: Long, nuevoEstado: String, onExito: () -> Unit) {
        db.collection("negociaciones").document(chatId)
            .collection("mensajes")
            .whereEqualTo("timestamp", timestamp)
            .get()
            .addOnSuccessListener { docs ->
                for (doc in docs) {
                    doc.reference.update("estadoOferta", nuevoEstado)
                        .addOnSuccessListener { onExito() }
                }
            }
    }

    fun cancelarNegociacionCompleta(chatId: String, updates: Map<String, Any>, onFinalizado: () -> Unit) {
        db.collection("negociaciones").document(chatId).update(updates)
            .addOnSuccessListener { onFinalizado() }
    }

    /**
     * Ejecuta la cancelación con penalización en una transacción atómica.
     *
     * Lógica de penalización:
     * - Si el TRABAJADOR es penalizado  → se le baja el rating (–0.5, mínimo 1.0)
     * - Si el CLIENTE   es penalizado  → se le baja el rating (–0.3, mínimo 1.0)
     *                                     y se incrementa reportesPorInasistencia
     *
     * Además registra el evento en la colección "penalizaciones" para trazabilidad.
     */
    fun ejecutarCancelacionConPenalizacion(
        chatId: String,
        updatesNegociacion: Map<String, Any>,
        usuarioPenalizadoId: String,
        esTrabajadorPenalizado: Boolean,
        motivo: String,
        onResultado: (Boolean) -> Unit
    ) {
        val negociacionRef  = db.collection("negociaciones").document(chatId)
        val usuarioRef      = db.collection("usuarios").document(usuarioPenalizadoId)
        val penalizacionRef = db.collection("penalizaciones").document()

        db.runTransaction { transaction ->
            val snap   = transaction.get(usuarioRef)
            val rating = snap.getDouble("rating") ?: 5.0

            transaction.update(negociacionRef, updatesNegociacion)

            if (esTrabajadorPenalizado) {
                transaction.update(
                    usuarioRef,
                    "rating", (rating - 0.5).coerceAtLeast(1.0)
                )
            } else {
                transaction.update(
                    usuarioRef,
                    mapOf(
                        "rating"                  to (rating - 0.3).coerceAtLeast(1.0),
                        "reportesPorInasistencia" to FieldValue.increment(1)
                    )
                )
            }

            val registroPenalizacion = mapOf(
                "chatId"                  to chatId,
                "usuarioPenalizadoId"     to usuarioPenalizadoId,
                "esTrabajadorPenalizado"  to esTrabajadorPenalizado,
                "motivo"                  to motivo,
                "timestamp"               to System.currentTimeMillis(),
                "ratingAnterior"          to rating,
                "ratingNuevo"             to if (esTrabajadorPenalizado)
                    (rating - 0.5).coerceAtLeast(1.0)
                else
                    (rating - 0.3).coerceAtLeast(1.0)
            )
            transaction.set(penalizacionRef, registroPenalizacion)

            null
        }.addOnSuccessListener { onResultado(true) }
            .addOnFailureListener { onResultado(false) }
    }

    fun crearNegociacionInicial(
        chatId: String, clienteId: String, trabajadorId: String, categoria: String
    ) {
        val data = hashMapOf<String, Any>(
            "clienteId"      to clienteId,
            "trabajadorId"   to trabajadorId,
            "activo"         to true,
            "timestamp"      to System.currentTimeMillis(),
            "ultimoMensaje"  to "Conversación iniciada",
            "categoria"      to categoria,
            "ultimoEmisorId" to clienteId
        )
        db.collection("negociaciones").document(chatId)
            .set(data, SetOptions.merge())
    }

    fun guardarFcmTokenActual() {
        val uid = auth.currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            db.collection("usuarios").document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .addOnSuccessListener { Log.d("FCM", "Token guardado para $uid") }
                .addOnFailureListener { Log.e("FCM", "Error guardando token: ${it.message}") }
        }
    }

    fun enviarNotificacion(
        receptorId: String,
        titulo: String,
        cuerpo: String,
        chatId: String,
        esTrabajadorReceptor: Boolean,
        nombreSocio: String,
        tipo: String = "CHAT"
    ) {
        db.collection("usuarios").document(receptorId).get()
            .addOnSuccessListener { doc ->
                val fcmToken = doc.getString("fcmToken") ?: run {
                    Log.w("FCM", "Receptor $receptorId no tiene fcmToken guardado")
                    return@addOnSuccessListener
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val stream = context.assets.open("service_account.json")
                        val credentials = GoogleCredentials.fromStream(stream)
                            .createScoped(/* scopes = */ listOf("https://www.googleapis.com/auth/firebase.messaging"))
                        credentials.refreshIfExpired()
                        val accessToken = credentials.accessToken.tokenValue

                        val messageJson = JSONObject().apply {
                            put("message", JSONObject().apply {
                                put("token", fcmToken)
                                put("notification", JSONObject().apply {
                                    put("title", titulo)
                                    put("body",  cuerpo)
                                })
                                put("data", JSONObject().apply {
                                    put("chatId",       chatId)
                                    put("esTrabajador", esTrabajadorReceptor.toString())
                                    put("nombreSocio",  nombreSocio)
                                    put("tipo",         tipo)
                                })
                            })
                        }

                        val url  = URL("https://fcm.googleapis.com/v1/projects/domingo-8d503/messages:send")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Authorization", "Bearer $accessToken")
                        conn.setRequestProperty("Content-Type",  "application/json; UTF-8")
                        conn.doOutput = true

                        OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                            writer.write(messageJson.toString())
                            writer.flush()
                        }

                        val responseCode = conn.responseCode
                        if (responseCode == 200) {
                            Log.d("FCM_HTTP", "Notificación enviada directamente ✓")
                        } else {
                            val errorStr = conn.errorStream?.bufferedReader()?.readText() ?: "Error desconocido"
                            Log.e("FCM_HTTP", "Error $responseCode enviando notificación: $errorStr")
                        }

                    } catch (e: Exception) {
                        Log.e("FCM_HTTP", "Excepción al enviar notificación: ${e.message}", e)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("FCM", "Error leyendo token de Firestore: ${e.message}")
            }
    }
}