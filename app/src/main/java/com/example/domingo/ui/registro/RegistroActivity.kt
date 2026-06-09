package com.example.domingo.ui.registro

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.domingo.R
import com.example.domingo.ui.main.MainActivity
import com.example.domingo.ui.verificarsocio.VerificarSocioActivity
import kotlin.jvm.java

class RegistroActivity : AppCompatActivity() {

    private val viewModel: RegistroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Crear Cuenta"

        val btnRegistrar = findViewById<Button>(R.id.btnRegistrar)
        val etNombre = findViewById<EditText>(R.id.etNombre)
        val etCorreo = findViewById<EditText>(R.id.etCorreo)
        val etPass = findViewById<EditText>(R.id.etPassword)
        val etTelefono = findViewById<EditText>(R.id.etTelefono)
        val rbTrabajador = findViewById<RadioButton>(R.id.rbTrabajador)

        btnRegistrar.setOnClickListener {
            val rol = if (rbTrabajador.isChecked) "trabajador" else "cliente"
            viewModel.validarYRegistrar(
                etNombre.text.toString().trim(),
                etCorreo.text.toString().trim(),
                etPass.text.toString(),
                etTelefono.text.toString().trim(),
                rol
            )
        }

        viewModel.errorNombre.observe(this) { error ->
            etNombre.error = error
        }

        viewModel.errorTelefono.observe(this) { error ->
            etTelefono.error = error
        }

        viewModel.estadoToast.observe(this) { mensaje ->
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        }

        viewModel.mostrarAlertaContrasena.observe(this) { mostrar ->
            if (mostrar == true) {
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
            }
        }

        viewModel.navegacionDestino.observe(this) { rol ->
            if (rol == "trabajador") {
                startActivity(Intent(this, VerificarSocioActivity::class.java))
            } else {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}