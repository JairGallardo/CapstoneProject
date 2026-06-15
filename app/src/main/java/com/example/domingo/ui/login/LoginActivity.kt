package com.example.domingo.ui.login

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.domingo.R
import com.example.domingo.data.repository.NegociacionRepository
import com.example.domingo.ui.main.MainActivity
import com.example.domingo.ui.registro.RegistroActivity
import com.example.domingo.ui.verificarsocio.VerificarSocioActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val cuenta = task.getResult(ApiException::class.java)
            cuenta.idToken?.let { viewModel.iniciarSesionConGoogle(it) }
        } catch (e: ApiException) {
            Toast.makeText(this, "Error al iniciar con Google: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) NegociacionRepository(applicationContext).guardarFcmTokenActual()
    }

    private lateinit var btnLogin: Button
    private lateinit var tilPass: TextInputLayout
    private lateinit var etLoginCorreo: TextInputEditText
    private lateinit var etLoginPass: TextInputEditText
    private var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        supportActionBar?.hide()

        pedirPermisoNotificacionesSiNecesario()
        viewModel.verificarSesionExistente()

        inicializarVistas()
        configurarListeners()
        configurarObservadores()
    }

    private fun inicializarVistas() {
        btnLogin = findViewById(R.id.btnLogin)
        tilPass = findViewById(R.id.tilPass)
        etLoginCorreo = findViewById(R.id.etLoginCorreo)
        etLoginPass = findViewById(R.id.etLoginPass)
        progressBar = findViewById(R.id.progressBarLogin)
    }

    private fun configurarListeners() {
        btnLogin.setOnClickListener {
            viewModel.intentarLogin(
                etLoginCorreo.text.toString().trim(),
                etLoginPass.text.toString()
            )
        }

        findViewById<android.widget.TextView>(R.id.tvIrARegistro)?.setOnClickListener {
            viewModel.navegarARegistro()
        }

        findViewById<android.widget.TextView>(R.id.tvOlvideContrasena)?.setOnClickListener {
            mostrarDialogoRecuperacion()
        }

        findViewById<CardView>(R.id.btnGoogle)?.setOnClickListener {
            iniciarFlujoGoogle()
        }

    }

    private fun configurarObservadores() {
        viewModel.estadoToast.observe(this) { mensaje ->
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
        }

        viewModel.cargando.observe(this) { cargando ->
            progressBar?.isVisible = cargando
            btnLogin.isEnabled = !cargando
            btnLogin.text = if (cargando) "Iniciando sesión..." else "Iniciar Sesión"
        }

        viewModel.recuperacionExitosa.observe(this) { exitosa ->
            if (exitosa) {
                Toast.makeText(
                    this,
                    "✅ Enlace enviado. Revisa tu correo para restablecer tu contraseña.",
                    Toast.LENGTH_LONG
                ).show()
            }
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

    private fun mostrarDialogoRecuperacion() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_recuperar_contrasena)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etCorreo = dialog.findViewById<TextInputEditText>(R.id.etCorreoRecuperacion)
        val btnEnviar = dialog.findViewById<Button>(R.id.btnEnviarRecuperacion)
        val btnCancelar = dialog.findViewById<Button>(R.id.btnCancelarRecuperacion)

        btnEnviar.setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            viewModel.enviarRecuperacionContrasena(correo)
            dialog.dismiss()
        }

        btnCancelar.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun iniciarFlujoGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val cliente = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(cliente.signInIntent)
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