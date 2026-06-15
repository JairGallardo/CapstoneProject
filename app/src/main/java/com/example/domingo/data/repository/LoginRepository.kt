package com.example.domingo.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class LoginRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun obtenerUsuarioActualId(): String? = auth.currentUser?.uid

    fun iniciarSesion(correo: String, pass: String, onResultado: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(correo, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResultado(true, null)
                } else {
                    onResultado(false, traducirErrorFirebase(task.exception?.message))
                }
            }
    }

    fun iniciarSesionConGoogle(idToken: String, onResultado: (Boolean, String?) -> Unit) {
        val credencial = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credencial)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResultado(true, null)
                } else {
                    onResultado(false, traducirErrorFirebase(task.exception?.message))
                }
            }
    }

    fun registrarUsuarioSocial(
        nombre: String,
        correo: String,
        uid: String,
        proveedor: String,
        onResultado: (Boolean, String?) -> Unit
    ) {
        val ref = db.collection("usuarios").document(uid)
        ref.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                val datos = hashMapOf(
                    "nombre" to nombre,
                    "correo" to correo,
                    "telefono" to "",
                    "rol" to "cliente",
                    "uid" to uid,
                    "rating" to 0.0,
                    "trabajosRealizados" to 0,
                    "verificado" to "si",
                    "proveedor" to proveedor
                )
                ref.set(datos)
                    .addOnSuccessListener { onResultado(true, null) }
                    .addOnFailureListener { onResultado(false, it.message) }
            } else {
                onResultado(true, null)
            }
        }.addOnFailureListener { onResultado(false, it.message) }
    }

    fun enviarCorreoRecuperacion(correo: String, onResultado: (Boolean, String?) -> Unit) {
        auth.sendPasswordResetEmail(correo)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResultado(true, null)
                } else {
                    onResultado(false, traducirErrorFirebase(task.exception?.message))
                }
            }
    }

    fun obtenerDatosUsuario(userId: String, onResultado: (DocumentSnapshot?, Exception?) -> Unit) {
        db.collection("usuarios").document(userId).get()
            .addOnSuccessListener { document -> onResultado(document, null) }
            .addOnFailureListener { exception -> onResultado(null, exception) }
    }

    fun cerrarSesion() = auth.signOut()

    private fun traducirErrorFirebase(mensaje: String?): String {
        return when {
            mensaje == null -> "Ocurrió un error desconocido"
            "no user record" in mensaje || "user-not-found" in mensaje ->
                "No existe una cuenta con este correo"
            "password is invalid" in mensaje || "wrong-password" in mensaje ->
                "La contraseña es incorrecta"
            "badly formatted" in mensaje || "invalid-email" in mensaje ->
                "El formato del correo no es válido"
            "blocked all requests" in mensaje || "too-many-requests" in mensaje ->
                "Demasiados intentos fallidos. Intenta más tarde"
            "network error" in mensaje ->
                "Error de conexión. Verifica tu internet"
            "email already in use" in mensaje ->
                "Este correo ya está registrado"
            else -> "Error: $mensaje"
        }
    }
}