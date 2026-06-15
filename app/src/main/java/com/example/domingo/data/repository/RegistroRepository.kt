package com.example.domingo.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegistroRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun registrarUsuario(
        nombre: String,
        correo: String,
        pass: String,
        telefono: String,
        rol: String,
        onResultado: (Boolean, String?) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(correo, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        val datosUsuario = hashMapOf(
                            "nombre" to nombre,
                            "correo" to correo,
                            "telefono" to telefono,
                            "rol" to rol,
                            "uid" to userId,
                            "rating" to 0.0,
                            "trabajosRealizados" to 0,
                            "verificado" to if (rol == "trabajador") "no" else "si",
                            "proveedor" to "email"
                        )
                        db.collection("usuarios").document(userId).set(datosUsuario)
                            .addOnSuccessListener { onResultado(true, null) }
                            .addOnFailureListener { ex -> onResultado(false, ex.message) }
                    } else {
                        onResultado(false, "No se pudo obtener el ID de usuario")
                    }
                } else {
                    onResultado(false, traducirErrorFirebase(task.exception?.message))
                }
            }
    }

    private fun traducirErrorFirebase(mensaje: String?): String {
        return when {
            mensaje == null -> "Ocurrió un error desconocido"
            "email address is already in use" in (mensaje) ->
                "Este correo ya está registrado. ¿Olvidaste tu contraseña?"
            "badly formatted" in mensaje || "invalid-email" in mensaje ->
                "El formato del correo electrónico no es válido"
            "password should be at least" in mensaje ->
                "La contraseña debe tener al menos 6 caracteres"
            "network error" in mensaje ->
                "Error de conexión. Verifica tu internet"
            "blocked all requests" in mensaje ->
                "Demasiados intentos. Intenta más tarde"
            else -> "Error: $mensaje"
        }
    }
}