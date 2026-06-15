package com.example.domingo.ui.atencion

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.R
import java.io.ByteArrayOutputStream

class AtencionClienteActivity : AppCompatActivity() {

    private val viewModel: AtencionClienteViewModel by viewModels()

    private lateinit var rgMotivo            : RadioGroup
    private lateinit var etDescripcion       : EditText
    private lateinit var etIdTicket          : EditText
    private lateinit var cardAvisoApelacion  : CardView
    private lateinit var cardIdTicket        : CardView
    private lateinit var btnEnviar           : Button
    private lateinit var rvFotos             : RecyclerView

    private val fotosEvidenciaB64 = mutableListOf<String>()
    private lateinit var fotosAdapter: EvidenciaFotoAdapter

    private val MAX_FOTOS = 3

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let { agregarFoto(it) }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriToBitmap(it)?.let { bitmap -> agregarFoto(bitmap) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_atencion_cliente)

        bindViews()
        setupFotosEvidencia()
        setupListeners()
        configurarObservadores()
    }

    private fun bindViews() {
        rgMotivo           = findViewById(R.id.rgMotivoAtencion)
        etDescripcion      = findViewById(R.id.etDescripcionAtencion)
        etIdTicket         = findViewById(R.id.etIdTicket)
        cardAvisoApelacion = findViewById(R.id.cardAvisoApelacion)
        cardIdTicket       = findViewById(R.id.cardIdTicket)
        btnEnviar          = findViewById(R.id.btnEnviarAtencion)
        rvFotos            = findViewById(R.id.rvFotosEvidencia)
    }

    private fun setupFotosEvidencia() {
        rvFotos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        fotosAdapter = EvidenciaFotoAdapter(
            fotos = fotosEvidenciaB64,
            maxFotos = MAX_FOTOS,
            onAgregarClick = { mostrarDialogoOrigenFoto() },
            onQuitarClick  = { position -> quitarFoto(position) }
        )
        rvFotos.adapter = fotosAdapter
    }

    private fun mostrarDialogoOrigenFoto() {
        val opciones = arrayOf("Tomar foto", "Elegir de galería")
        AlertDialog.Builder(this)
            .setTitle("Agregar evidencia")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun agregarFoto(bitmap: Bitmap) {
        if (fotosEvidenciaB64.size >= MAX_FOTOS) {
            Toast.makeText(this, "Máximo $MAX_FOTOS fotos", Toast.LENGTH_SHORT).show()
            return
        }
        val scaled = Bitmap.createScaledBitmap(bitmap, 720, 720, true)
        val b64    = bitmapToBase64(scaled)
        fotosEvidenciaB64.add(b64)
        fotosAdapter.actualizar()
    }

    private fun quitarFoto(position: Int) {
        if (position in fotosEvidenciaB64.indices) {
            fotosEvidenciaB64.removeAt(position)
            fotosAdapter.actualizar()
        }
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.btnVolverAtencion)?.setOnClickListener { finish() }

        rgMotivo.setOnCheckedChangeListener { _, checkedId ->
            val motivoId = when (checkedId) {
                R.id.rbPenalizacionInjusta -> AtencionClienteViewModel.MOTIVO_PENALIZACION
                R.id.rbConflictoServicio   -> AtencionClienteViewModel.MOTIVO_CONFLICTO
                else                       -> -1
            }
            viewModel.onMotivoSeleccionado(motivoId)
        }

        btnEnviar.setOnClickListener {
            val motivoTexto = obtenerTextoMotivo()
            viewModel.enviarSolicitud(
                motivoTexto         = motivoTexto,
                descripcion         = etDescripcion.text.toString(),
                idTicketRelacionado = etIdTicket.text.toString(),
                fotosB64            = fotosEvidenciaB64.toList()
            )
        }
    }

    private fun configurarObservadores() {
        viewModel.mostrarAvisoApelacion.observe(this) { mostrar ->
            cardAvisoApelacion.visibility = if (mostrar) View.VISIBLE else View.GONE
        }

        viewModel.mostrarCampoTicket.observe(this) { mostrar ->
            cardIdTicket.visibility = if (mostrar) View.VISIBLE else View.GONE
        }

        viewModel.enviando.observe(this) { enviando ->
            btnEnviar.isEnabled = !enviando
            btnEnviar.text      = if (enviando) "Enviando..." else "Enviar solicitud"
        }

        viewModel.exitoEnvio.observe(this) { exito ->
            if (exito) mostrarDialogoExito()
        }

        viewModel.errorMensaje.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun obtenerTextoMotivo(): String {
        return when (rgMotivo.checkedRadioButtonId) {
            R.id.rbProblemaApp         -> "Problema técnico con la app"
            R.id.rbPenalizacionInjusta -> "Apelar una penalización"
            R.id.rbConflictoServicio   -> "Conflicto con un servicio"
            R.id.rbCuentaBloqueada     -> "Cuenta suspendida o bloqueada"
            R.id.rbOtroMotivo          -> "Otro motivo"
            else                       -> ""
        }
    }

    private fun mostrarDialogoExito() {
        AlertDialog.Builder(this)
            .setTitle("✅ Solicitud enviada")
            .setMessage("Recibimos tu caso. Nuestro equipo te responderá en un plazo de 24 a 48 horas a través de las notificaciones.")
            .setPositiveButton("Entendido") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}