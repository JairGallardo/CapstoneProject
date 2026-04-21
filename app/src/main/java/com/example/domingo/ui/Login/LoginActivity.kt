package com.example.domingo.ui.Login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.domingo.MainActivity
import com.example.domingo.R
import com.example.domingo.ui.Registro.RegistroActivity
import com.example.domingo.VerificarSocioActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        if (auth.currentUser != null) {
            verificarRolYRedirigir()
            return // Importante poner el return para que no cargue el resto del login
        }

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvIrARegistro = findViewById<TextView>(R.id.tvIrARegistro)

        btnLogin.setOnClickListener {
            val correo = findViewById<EditText>(R.id.etLoginCorreo).text.toString()
            val pass = findViewById<EditText>(R.id.etLoginPass).text.toString()

            if (correo.isNotEmpty() && pass.isNotEmpty()) {
                auth.signInWithEmailAndPassword(correo, pass).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        verificarRolYRedirigir()
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        tvIrARegistro.setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }
    }

    private fun verificarRolYRedirigir() {
        val userId = auth.currentUser?.uid
        db.collection("usuarios").document(userId!!).get().addOnSuccessListener { document ->
            val rol = document.getString("rol")
            val verificado = document.getString("verificado") // "si", "no", o "en_revision"

            if (rol == "trabajador") {
                // Si es trabajador y NO ha enviado documentos, lo mandamos a verificar
                if (verificado == "no" || verificado == null) {
                    startActivity(Intent(this, VerificarSocioActivity::class.java))
                } else if (verificado == "en_revision") {
                    Toast.makeText(this, "Tu perfil sigue en revisión. Ten paciencia.", Toast.LENGTH_LONG).show()
                    auth.signOut() // Lo sacamos para que no entre a la app aún
                } else {
                    // Si ya está verificado ("si"), entra a su panel de control
                    startActivity(Intent(this, MainActivity::class.java))
                }
            } else {
                // Si es cliente, entra directo
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }
    }
}