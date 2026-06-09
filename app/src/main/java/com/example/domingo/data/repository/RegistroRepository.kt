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
        auth.createUserWithEmailAndPassword(correo, pass).addOnCompleteListener { task ->
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
                        "verificado" to if (rol == "trabajador") "no" else "si"
                    )

                    db.collection("usuarios").document(userId).set(datosUsuario)
                        .addOnSuccessListener {
                            onResultado(true, null)
                        }
                        .addOnFailureListener { exception ->
                            onResultado(false, exception.message)
                        }
                } else {
                    onResultado(false, "No se pudo obtener el ID de usuario.")
                }
            } else {
                onResultado(false, task.exception?.message)
            }
        }
    }
}