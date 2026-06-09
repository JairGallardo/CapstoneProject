package com.example.domingo.ui.verificarsocio

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.domingo.R
import java.io.File

class VerificarSocioActivity : AppCompatActivity() {

    private val viewModel: VerificarSocioViewModel by viewModels()

    private lateinit var photoUri: Uri
    private var imagenPerfil: Uri? = null
    private var imagenDniFrontal: Uri? = null
    private var imagenDniTrasero: Uri? = null

    private lateinit var btnEnviar: Button
    private lateinit var cbDeclaracion: CheckBox

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) abrirCamara() else Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show()
    }

    private val camaraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            imagenPerfil = photoUri
            findViewById<ImageView>(R.id.ivPreviewFoto)?.setImageURI(photoUri)
            dispararValidacion()
        }
    }
    private val dniFrontalLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { imagenDniFrontal = it; dispararValidacion() }
    }

    private val dniTraseroLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { imagenDniTrasero = it; dispararValidacion() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verificar_socio)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Verificación de Identidad"

        btnEnviar = findViewById(R.id.btnEnviarRevision)
        cbDeclaracion = findViewById(R.id.cbDeclaracion)

        findViewById<Button>(R.id.btnCamaraPerfil)?.setOnClickListener { verificarPermisoYCamara() }
        findViewById<Button>(R.id.btnDniFrontal)?.setOnClickListener { dniFrontalLauncher.launch("image/*") }
        findViewById<Button>(R.id.btnDniTrasero)?.setOnClickListener { dniTraseroLauncher.launch("image/*") }

        cbDeclaracion.setOnCheckedChangeListener { _, _ -> dispararValidacion() }

        btnEnviar.setOnClickListener {
            viewModel.procesarYEnviar(imagenPerfil, imagenDniFrontal, imagenDniTrasero)
        }

        configurarObservadores()
    }

    private fun dispararValidacion() {
        viewModel.evaluarFormulario(imagenPerfil, imagenDniFrontal, imagenDniTrasero, cbDeclaracion.isChecked)
    }

    private fun configurarObservadores() {
        viewModel.formularioValido.observe(this) { esValido ->
            btnEnviar.isEnabled = esValido
        }

        viewModel.estadoCarga.observe(this) { estado ->
            when (estado) {
                VerificarSocioViewModel.EstadoCarga.Cargando -> {
                    btnEnviar.isEnabled = false
                    btnEnviar.text = "Procesando documentos..."
                }
                VerificarSocioViewModel.EstadoCarga.Error -> {
                    btnEnviar.isEnabled = true
                    btnEnviar.text = "Reintentar"
                    Toast.makeText(this, "Error al subir documentos", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        viewModel.procesoExitoso.observe(this) { exitoso ->
            if (exitoso == true) {
                Toast.makeText(this, "Documentos enviados. Espera la verificación.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    private fun verificarPermisoYCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) abrirCamara()
        else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
    private fun abrirCamara() {
        val file = File(filesDir, "temp_perfil.jpg")
        photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        camaraLauncher.launch(photoUri)
    }
}