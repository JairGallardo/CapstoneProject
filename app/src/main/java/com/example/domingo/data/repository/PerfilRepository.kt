package com.example.domingo.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class PerfilRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun obtenerUsuarioId(): String? = auth.currentUser?.uid

    fun cerrarSesion() {
        auth.signOut()
    }

    fun cargarDatosUsuario(uid: String, onSuccess: (com.google.firebase.firestore.DocumentSnapshot) -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { onSuccess(it) }
            .addOnFailureListener { onFailure(it) }
    }

    fun actualizarCamposPerfil(uid: String, updates: Map<String, Any>, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("usuarios").document(uid).update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun cargarResenasUsuario(usuarioId: String, onSuccess: (List<Map<String, Any>>) -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("usuarios").document(usuarioId)
            .collection("resenas")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val lista = snap.documents.mapNotNull { it.data }
                onSuccess(lista)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun cargarResenasPorCategoria(
        usuarioId: String,
        categoria: String,
        onSuccess: (List<Map<String, Any>>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("usuarios").document(usuarioId)
            .collection("resenas")
            .whereEqualTo("categoria", categoria)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val lista = snap.documents.mapNotNull { it.data }
                onSuccess(lista)
            }
            .addOnFailureListener { onFailure(it) }
    }
    fun agregarFotoPortafolio(uid: String, fotoId: String, fotoData: Any, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("usuarios").document(uid)
            .collection("portafolio").document(fotoId)
            .set(fotoData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun eliminarFotoPortafolio(uid: String, fotoId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("usuarios").document(uid)
            .collection("portafolio").document(fotoId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun escucharPortafolioCompleto(uid: String, onUpdate: (com.google.firebase.firestore.QuerySnapshot?) -> Unit) {
        db.collection("usuarios").document(uid).collection("portafolio")
            .addSnapshotListener { snap, _ -> onUpdate(snap) }
    }

    fun consultarPortafolioPorCategoria(uid: String, categoria: String, onSuccess: (com.google.firebase.firestore.QuerySnapshot) -> Unit) {
        db.collection("usuarios").document(uid).collection("portafolio")
            .whereEqualTo("categoria", categoria).get()
            .addOnSuccessListener { onSuccess(it) }
    }
}