package com.example.domingo.ui.login

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.domingo.R
import com.example.domingo.data.repository.NegociacionRepository
import com.example.domingo.ui.main.MainActivity
import com.example.domingo.ui.registro.RegistroActivity
import com.example.domingo.ui.verificarsocio.VerificarSocioActivity

class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            NegociacionRepository(applicationContext).guardarFcmTokenActual()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        pedirPermisoNotificacionesSiNecesario()

        viewModel.verificarSesionExistente()

        val btnLogin    = findViewById<Button>(R.id.btnLogin)
        val tvIrRegistro = findViewById<TextView>(R.id.tvIrARegistro)
        val etCorreo    = findViewById<EditText>(R.id.etLoginCorreo)
        val etPass      = findViewById<EditText>(R.id.etLoginPass)

        btnLogin.setOnClickListener {
            viewModel.intentarLogin(etCorreo.text.toString(), etPass.text.toString())
        }

        tvIrRegistro.setOnClickListener {
            viewModel.navegarARegistro()
        }

        viewModel.estadoToast.observe(this) { mensaje ->
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
        }

        viewModel.navegacionDestino.observe(this) { destino ->
            when (destino) {
                LoginViewModel.DestinoPantalla.IR_A_VERIFICAR -> {
                    NegociacionRepository(applicationContext).guardarFcmTokenActual()
                    startActivity(Intent(this, VerificarSocioActivity::class.java))
                    finish()
                }
                LoginViewModel.DestinoPantalla.IR_A_MAIN -> {
                    NegociacionRepository(applicationContext).guardarFcmTokenActual()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                LoginViewModel.DestinoPantalla.IR_A_REGISTRO -> {
                    startActivity(Intent(this, RegistroActivity::class.java))
                }
            }
        }
    }

    private fun pedirPermisoNotificacionesSiNecesario() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permiso = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permiso) != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(permiso)
            } else {
                NegociacionRepository(applicationContext).guardarFcmTokenActual()
            }
        } else {
            NegociacionRepository(applicationContext).guardarFcmTokenActual()
        }
    }
}