package com.example.domingo.data.repository

import com.example.domingo.model.ServicioFinalizado
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class MainRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    fun obtenerUsuarioId(): String? = auth.currentUser?.uid

    fun obtenerDatosUsuario(uid: String, onExito: (DocumentSnapshot) -> Unit) {
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { onExito(it) }
    }

    fun actualizarEstadoDisponibilidad(
        uid: String,
        disponible: Boolean,
        latitud: Double?,
        longitud: Double?,
        completado: (Boolean) -> Unit
    ) {
        val actualizaciones = mutableMapOf<String, Any>("disponible" to disponible)
        if (disponible && latitud != null && longitud != null) {
            actualizaciones["latitud"]  = latitud
            actualizaciones["longitud"] = longitud
        }
        db.collection("usuarios").document(uid).update(actualizaciones)
            .addOnSuccessListener { completado(true) }
            .addOnFailureListener { completado(false) }
    }

    fun escucharHistorialServicios(
        uid: String,
        onDatos: (List<ServicioFinalizado>) -> Unit
    ): ListenerRegistration {
        return db.collection("servicios_finalizados")
            .whereArrayContains("participantes", uid)
            .orderBy("fecha", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    onDatos(snapshot.toObjects(ServicioFinalizado::class.java))
                }
            }
    }

    fun escucharBandejaEntrada(
        uid: String,
        esTrabajador: Boolean,
        onDatos: (List<DocumentSnapshot>) -> Unit
    ): ListenerRegistration {
        val query = if (esTrabajador)
            db.collection("negociaciones").whereEqualTo("trabajadorId", uid)
        else
            db.collection("negociaciones").whereEqualTo("clienteId",   uid)

        return query.addSnapshotListener { snapshot, error ->
            if (error == null && snapshot != null) {
                onDatos(snapshot.documents)
            }
        }
    }

    fun ocultarChatYNotificar(
        chatId: String,
        esTrabajador: Boolean,
        onCompletado: (Boolean) -> Unit
    ) {
        val campoOculto = if (esTrabajador) "ocultoParaTrabajador" else "ocultoParaCliente"
        val docRef      = db.collection("negociaciones").document(chatId)

        db.runTransaction { transaction ->
            val updatesPadre = mapOf<String, Any>(
                campoOculto      to true,
                "activo"         to false,
                "ultimoMensaje"  to "🚫 Conversación eliminada por la otra parte."
            )
            transaction.update(docRef, updatesPadre)

            val mensajeRef = docRef.collection("mensajes").document()
            val mensaje    = mapOf(
                "emisorId"  to "SISTEMA",
                "contenido" to "🚫 La otra parte ha eliminado esta conversación de su bandeja. Ya no podrás enviar mensajes.",
                "tipo"      to "TEXTO",
                "timestamp" to System.currentTimeMillis()
            )
            transaction.set(mensajeRef, mensaje)
            null
        }.addOnSuccessListener  { onCompletado(true)  }
            .addOnFailureListener  { onCompletado(false) }
    }

    fun obtenerConteoPorCategoria(
        trabajadorId: String,
        onResultado: (Map<String, Int>) -> Unit
    ) {
        db.collection("servicios_finalizados")
            .whereEqualTo("trabajadorId", trabajadorId)
            .get()
            .addOnSuccessListener { snapshot ->
                val conteo = mutableMapOf<String, Int>()
                for (doc in snapshot.documents) {
                    val cat = doc.getString("categoria") ?: continue
                    conteo[cat] = (conteo[cat] ?: 0) + 1
                }
                onResultado(conteo)
            }
            .addOnFailureListener {
                onResultado(emptyMap())
            }
    }
}