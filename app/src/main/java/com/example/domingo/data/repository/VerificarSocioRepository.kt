package com.example.domingo.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class VerificarSocioRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun obtenerUsuarioId(): String? = auth.currentUser?.uid

    fun enviarDocumentosRevision(
        uid: String,
        perfilB64: String,
        frontalB64: String,
        traseroB64: String,
        onResultado: (Boolean) -> Unit
    ) {
        val datos = mapOf(
            "fotoPerfilB64" to perfilB64,
            "dniFrontalB64" to frontalB64,
            "dniTraseroB64" to traseroB64,
            "verificado" to "en_revision",
            "fechaSolicitud" to Timestamp.now()
        )

        db.collection("usuarios").document(uid)
            .update(datos)
            .addOnSuccessListener { onResultado(true) }
            .addOnFailureListener { onResultado(false) }
    }

    fun cerrarSesion() {
        auth.signOut()
    }
}