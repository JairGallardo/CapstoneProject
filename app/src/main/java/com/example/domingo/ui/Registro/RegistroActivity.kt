package com.example.domingo.ui.Registro

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.domingo.MainActivity
import com.example.domingo.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegistroActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro)

        // Configuración de la barra superior con botón atrás
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Crear Cuenta"

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val btnRegistrar = findViewById<Button>(R.id.btnRegistrar)
        val etNombre = findViewById<EditText>(R.id.etNombre)
        val etCorreo = findViewById<EditText>(R.id.etCorreo)
        val etPass = findViewById<EditText>(R.id.etPassword)
        val etTelefono = findViewById<EditText>(R.id.etTelefono) // Asegúrate que este ID exista en tu XML
        val rbTrabajador = findViewById<RadioButton>(R.id.rbTrabajador)

        btnRegistrar.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val correo = etCorreo.text.toString().trim()
            val pass = etPass.text.toString()
            val telefono = etTelefono.text.toString().trim()
            val rol = if (rbTrabajador.isChecked) "trabajador" else "cliente"

            // --- VALIDACIONES DE SEGURIDAD ---

            if (nombre.isEmpty() || correo.isEmpty() || pass.isEmpty() || telefono.isEmpty()) {
                Toast.makeText(this, "Por favor, llena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (nombre.length < 3) {
                etNombre.error = "Nombre demasiado corto"
                return@setOnClickListener
            }

            if (telefono.length < 9) {
                etTelefono.error = "Ingresa un número de teléfono válido (9 dígitos)"
                return@setOnClickListener
            }

            if (!esContrasenaSegura(pass)) {
                AlertDialog.Builder(this)
                    .setTitle("Contraseña poco segura")
                    .setMessage("La contraseña debe tener al menos:\n" +
                            "• 8 caracteres\n" +
                            "• Una letra mayúscula\n" +
                            "• Una letra minúscula\n" +
                            "• Un número\n" +
                            "• Un carácter especial (@#$%^&+=)")
                    .setPositiveButton("Entendido", null)
                    .show()
                return@setOnClickListener
            }

            // Si pasa todas las validaciones, procedemos
            crearCuentaEnFirebase(nombre, correo, pass, telefono, rol)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun esContrasenaSegura(pass: String): Boolean {
        val pattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$".toRegex()
        return pattern.matches(pass)
    }

    private fun crearCuentaEnFirebase(nombre: String, correo: String, pass: String, telefono: String, rol: String) {
        auth.createUserWithEmailAndPassword(correo, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val userId = auth.currentUser?.uid ?: return@addOnCompleteListener

                val datosUsuario = hashMapOf(
                    "nombre" to nombre,
                    "correo" to correo,
                    "telefono" to telefono, // Nuevo campo guardado
                    "rol" to rol,
                    "uid" to userId,
                    "rating" to 0.0,
                    "trabajosRealizados" to 0,
                    "verificado" to if (rol == "trabajador") "no" else "si"
                )

                db.collection("usuarios").document(userId).set(datosUsuario)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Registro exitoso como $rol", Toast.LENGTH_SHORT).show()

                        // Redirección común al MainActivity
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
            } else {
                Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}