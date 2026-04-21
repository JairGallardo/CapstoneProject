package com.example.domingo.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.BuildConfig
import com.example.domingo.ChatAdapter
import com.example.domingo.FinalizarServicioActivity
import com.example.domingo.R
import com.example.domingo.model.Mensaje
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch

class NegociacionActivity : AppCompatActivity() {

    private lateinit var adapter: ChatAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var nombreTrabajador: String = ""
    private var telefonoTrabajador: String = ""
    private var especialidadTrabajador: String = "Especialista"
    private var chatId: String? = null
    private var receptorId: String? = null
    private var esTrabajador: Boolean = false
    private var listaMensajesActual: MutableList<Mensaje> = mutableListOf()

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as Bitmap
            mostrarPrevisualizaciónIA(bitmap)
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, it)
            mostrarPrevisualizaciónIA(bitmap)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_negociacion)

        chatId = intent.getStringExtra("CHAT_ID") ?: "chat_generico"
        receptorId = intent.getStringExtra("RECEPTOR_ID")

        if (receptorId != null) {
            db.collection("usuarios").document(receptorId!!).get()
                .addOnSuccessListener { doc ->
                    nombreTrabajador = doc.getString("nombre") ?: "Socio"
                    telefonoTrabajador = doc.getString("telefono") ?: "999999999"
                    especialidadTrabajador = doc.getString("especialidad") ?: "general"
                }
        }

        esTrabajador = intent.getBooleanExtra("ES_TRABAJADOR", false)
        val nombreSocio = intent.getStringExtra("SOCIO_NOMBRE") ?: "Socio"
        findViewById<TextView>(R.id.tvNombreChat).text = nombreSocio

        setupRecyclerView()
        escucharMensajes()

        findViewById<ImageButton>(R.id.btnEnviarMensaje).setOnClickListener {
            val etTexto = findViewById<EditText>(R.id.etMensajeChat)
            val texto = etTexto.text.toString().trim()
            if (texto.isNotEmpty()) {
                enviarMensaje(texto, "TEXTO")
                etTexto.text.clear()
            }
        }

        findViewById<Button>(R.id.btnEnviarOferta).setOnClickListener {
            val etMonto = findViewById<EditText>(R.id.etMontoOferta)
            val monto = etMonto.text.toString().toDoubleOrNull()
            if (monto != null) {
                enviarMensaje("Oferta de servicio por S/ $monto", "OFERTA", monto)
                etMonto.text.clear()
            }
        }

        findViewById<ImageButton>(R.id.btnAsistenteIA)?.setOnClickListener {
            elegirFuenteImagen()
        }
    }

    private fun elegirFuenteImagen() {
        val opciones = arrayOf("Cámara", "Galería")
        AlertDialog.Builder(this)
            .setTitle("Selecciona una foto de tu espacio")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                    1 -> galleryLauncher.launch("image/*")
                }
            }.show()
    }

    private fun mostrarPrevisualizaciónIA(fotoUsuario: Bitmap) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_propuesta_ia, null)

        val ivPreview = view.findViewById<ImageView>(R.id.ivImagenGenerada)
        val etPrompt = view.findViewById<EditText>(R.id.etInstruccionClienteIA)
        val btnGenerar = view.findViewById<Button>(R.id.btnGenerarPropuesta)

        ivPreview.setImageBitmap(fotoUsuario)

        btnGenerar.setOnClickListener {
            val instruccion = etPrompt.text.toString()
            if (instruccion.isNotEmpty()) {
                consultarIAConImagen(instruccion, fotoUsuario)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Escribe qué deseas hacer en el espacio", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun prepararPromptIA(mensajeCliente: String): String {
        return when (especialidadTrabajador.lowercase()) {
            "pintor" -> "Como experto pintor, visualiza un diseño de pintura para: $mensajeCliente. Sugiere códigos de color y tipo de acabado."
            "carpintero" -> "Como ebanista, diseña un mueble o estructura de madera para: $mensajeCliente. Sugiere tipo de madera (tornillo, cedro, melamina)."
            "electricista" -> "Como técnico electricista, propone una distribución de luces led o puntos de red para: $mensajeCliente."
            else -> "Como experto en mantenimiento de DominGO, propón una solución técnica profesional para: $mensajeCliente."
        }
    }

    private fun consultarIAConImagen(prompt: String, bitmapUsuario: Bitmap?) {
        Toast.makeText(this, "La IA de DominGO está analizando...", Toast.LENGTH_SHORT).show()

        Log.d("API_KEY_DEBUG", BuildConfig.GEMINI_KEY)
        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_KEY
        )

        lifecycleScope.launch {
            try {
                val promptAdaptado = prepararPromptIA(prompt)
                val inputContent = content {
                    if (bitmapUsuario != null) image(bitmapUsuario)
                    text(promptAdaptado)
                }

                val response = generativeModel.generateContent(inputContent)
                val respuestaTexto = response.text

                if (respuestaTexto != null) {
                    enviarPropuestaAlChat("", respuestaTexto)
                }
            } catch (e: Exception) {
                Log.e("IA_ERROR", e.message ?: "Error")
                Toast.makeText(this@NegociacionActivity, "Error de conexión con IA", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enviarPropuestaAlChat(urlImagen: String, explicacion: String) {
        val uid = auth.currentUser?.uid ?: return
        val mensaje = hashMapOf(
            "emisorId" to uid,
            "contenido" to explicacion,
            "urlImagenIA" to urlImagen,
            "tipo" to "PROPUESTA_IA",
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("negociaciones").document(chatId!!)
            .collection("mensajes").add(mensaje)
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(auth.currentUser?.uid ?: "", esTrabajador) { mensaje, nuevoEstado ->
            actualizarEstadoOferta(mensaje, nuevoEstado)
        }
        val rv = findViewById<RecyclerView>(R.id.rvChatNegociacion)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
    }

    private fun enviarMensaje(contenido: String, tipo: String, monto: Double? = null) {
        val uid = auth.currentUser?.uid ?: return
        val mensaje = hashMapOf(
            "emisorId" to uid,
            "contenido" to contenido,
            "tipo" to tipo,
            "timestamp" to System.currentTimeMillis(),
            "montoOferta" to (monto ?: 0.0),
            "estadoOferta" to "PENDIENTE"
        )
        db.collection("negociaciones").document(chatId!!).collection("mensajes").add(mensaje)
    }

    private fun escucharMensajes() {
        db.collection("negociaciones").document(chatId!!)
            .collection("mensajes")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val lista = snapshot.toObjects(Mensaje::class.java)
                    listaMensajesActual.clear()
                    listaMensajesActual.addAll(lista)
                    adapter.actualizarMensajes(lista)
                    if (!esTrabajador) verificarSiFueAceptado(lista)
                    if (lista.isNotEmpty()) {
                        findViewById<RecyclerView>(R.id.rvChatNegociacion).scrollToPosition(lista.size - 1)
                    }
                }
            }
    }

    private fun finalizarTransaccion(metodo: String, monto: String) {
        chatId?.let { id ->
            val updates = mapOf("estado" to "FINALIZADO", "metodoPago" to metodo, "montoFinal" to monto)
            db.collection("negociaciones").document(id).update(updates)
                .addOnSuccessListener {
                    val intentFin = Intent(this, FinalizarServicioActivity::class.java)
                    intentFin.putExtra("PRECIO_FINAL", monto)
                    startActivity(intentFin)
                    finish()
                }
        }
    }

    private fun actualizarEstadoOferta(mensaje: Mensaje, nuevoEstado: String) {
        db.collection("negociaciones").document(chatId!!)
            .collection("mensajes")
            .whereEqualTo("timestamp", mensaje.timestamp)
            .get()
            .addOnSuccessListener { docs ->
                for (doc in docs) doc.reference.update("estadoOferta", nuevoEstado)
            }
    }

    private fun verificarSiFueAceptado(mensajes: List<Mensaje>) {
        val ultimaOferta = mensajes.lastOrNull { it.tipo == "OFERTA" }
        val btnPagar = findViewById<Button>(R.id.btnIrAPagar)
        if (ultimaOferta?.estadoOferta == "ACEPTADO") {
            btnPagar.visibility = View.VISIBLE
            btnPagar.setOnClickListener { /* Lógica de pago */ }
        } else {
            btnPagar.visibility = View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}