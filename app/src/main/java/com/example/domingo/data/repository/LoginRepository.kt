package com.example.domingo.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class LoginRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun obtenerUsuarioActualId(): String? {
        return auth.currentUser?.uid
    }

    fun iniciarSesion(correo: String, pass: String, onResultado: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(correo, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onResultado(true, null)
            } else {
                onResultado(false, task.exception?.message)
            }
        }
    }

    fun obtenerDatosUsuario(userId: String, onResultado: (DocumentSnapshot?, Exception?) -> Unit) {
        db.collection("usuarios").document(userId).get()
            .addOnSuccessListener { document -> onResultado(document, null) }
            .addOnFailureListener { exception -> onResultado(null, exception) }
    }

    fun cerrarSesion() {
        auth.signOut()
    }

}