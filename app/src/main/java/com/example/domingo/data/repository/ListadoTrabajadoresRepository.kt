package com.example.domingo.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions

class ListadoTrabajadoresRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun obtenerUsuarioId(): String? = auth.currentUser?.uid

    fun buscarTrabajadoresDisponibles(
        categoria: String,
        onExito: (QuerySnapshot) -> Unit,
        onFallo: (Exception) -> Unit
    ) {
        db.collection("usuarios")
            .whereEqualTo("rol", "trabajador")
            .whereEqualTo("verificado", "si")
            .whereEqualTo("disponible", true)
            .whereArrayContains("categorias", categoria)
            .get()
            .addOnSuccessListener { onExito(it) }
            .addOnFailureListener { exception ->
                Log.e("FirestoreIndex", "Falta índice en Firebase. Clic aquí para crearlo:", exception)
                onFallo(exception)
            }
    }

    fun crearNegociacionInicial(
        chatId: String,
        clienteId: String,
        trabajadorId: String,
        categoria: String
    ) {
        val dataInicial = hashMapOf<String, Any>(
            "clienteId" to clienteId,
            "trabajadorId" to trabajadorId,
            "activo" to true,
            "timestamp" to System.currentTimeMillis(),
            "ultimoMensaje" to "Conversación iniciada",
            "categoria" to categoria,
            "ultimoEmisorId" to clienteId
        )

        db.collection("negociaciones").document(chatId)
            .set(dataInicial)
            .addOnFailureListener {
                db.collection("negociaciones").document(chatId)
                    .set(dataInicial, SetOptions.merge())
            }
    }

    fun buscarNegociacionActiva(
        clienteId: String,
        trabajadorId: String,
        categoria: String,
        onResultado: (String?) -> Unit
    ) {
        db.collection("negociaciones")
            .whereEqualTo("clienteId", clienteId)
            .whereEqualTo("trabajadorId", trabajadorId)
            .whereEqualTo("categoria", categoria)
            .whereEqualTo("activo", true)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    onResultado(snapshot.documents[0].id)
                } else {
                    onResultado(null)
                }
            }
            .addOnFailureListener {
                onResultado(null)
            }
    }

    fun generarNuevoChatId(): String {
        return db.collection("negociaciones").document().id
    }
}