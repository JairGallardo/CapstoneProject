package com.example.domingo.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.ChatAdapter
import com.example.domingo.R
import com.example.domingo.model.*
import com.example.domingo.network.GroqApiService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import androidx.appcompat.app.AlertDialog
import com.example.domingo.ui.FinalizarServicioActivity

class NegociacionActivity : AppCompatActivity() {

    private val GROQ_API_KEY = "Bearer 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"

    private lateinit var adapter: ChatAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var especialidadTrabajador: String = "Especialista"
    private var chatId: String? = null
    private var receptorId: String? = null
    private var esTrabajador: Boolean = false

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.groq.com/openai/v1/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val groqService = retrofit.create(GroqApiService::class.java)

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let { ejecutarConsultaIA("Analiza esta imagen y ayúdame con mi consulta de $especialidadTrabajador", it) }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                ejecutarConsultaIA("Analiza esta imagen de mi galería", bitmap)
            } catch (e: Exception) {
                Log.e("GALLERY_ERROR", "${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_negociacion)

        chatId = intent.getStringExtra("CHAT_ID") ?: run {
            finish()
            return
        }

        receptorId = intent.getStringExtra("RECEPTOR_ID")
        esTrabajador = intent.getBooleanExtra("ES_TRABAJADOR", false)
        val nombreSocio = intent.getStringExtra("SOCIO_NOMBRE") ?: "Socio"

        findViewById<TextView>(R.id.tvNombreChat).text = nombreSocio

        val etMontoOferta = findViewById<EditText>(R.id.etMontoOferta)
        val btnEnviarOferta = findViewById<Button>(R.id.btnEnviarOferta)
        val etMensajeChat = findViewById<EditText>(R.id.etMensajeChat)
        val btnEnviarMensaje = findViewById<ImageButton>(R.id.btnEnviarMensaje)
        val btnAsistenteIA = findViewById<ImageButton>(R.id.btnAsistenteIA)
        val btnEliminarChat = findViewById<ImageButton>(R.id.btnEliminarChatCompleto)
        btnEliminarChat?.setOnClickListener {
            mostrarDialogoCancelarNegociacion()
        }

        setupRecyclerView()
        escucharMensajes()
        obtenerEspecialidadReceptor()

        btnEnviarOferta.setOnClickListener {
            val montoTexto = etMontoOferta.text.toString().trim()
            if (montoTexto.isNotEmpty()) {
                val monto = montoTexto.toDoubleOrNull()
                if (monto != null && monto > 0) {
                    enviarMensaje("Nueva propuesta de servicio", "OFERTA", monto)
                    etMontoOferta.text.clear()
                    Toast.makeText(this, "Oferta enviada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Monto no válido", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Ingresa un monto", Toast.LENGTH_SHORT).show()
            }
        }

        btnEnviarMensaje.setOnClickListener {
            val texto = etMensajeChat.text.toString().trim()
            if (texto.isNotEmpty()) {
                enviarMensaje(texto, "TEXTO")
                etMensajeChat.text.clear()
            }
        }

        btnAsistenteIA?.setOnClickListener {
            mostrarMenuIA()
        }
    }

    private fun obtenerEspecialidadReceptor() {
        receptorId?.let { id ->
            db.collection("usuarios").document(id).get()
                .addOnSuccessListener { doc ->
                    especialidadTrabajador = doc.getString("especialidad") ?: "General"
                }
        }
    }

    private fun mostrarMenuIA() {
        val opciones = arrayOf("Cámara", "Galería", "Solo Texto")
        AlertDialog.Builder(this)
            .setTitle("Asistente IA")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                    1 -> galleryLauncher.launch("image/*")
                    2 -> {
                        val texto = findViewById<EditText>(R.id.etMensajeChat).text.toString().trim()
                        if (texto.isNotEmpty()) ejecutarConsultaIA(texto)
                        else Toast.makeText(this, "Escribe algo primero", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoCancelarNegociacion() {
        AlertDialog.Builder(this)
            .setTitle("Cancelar Servicio")
            .setMessage("¿Estás seguro de que deseas cancelar la negociación y borrar todo el historial? Esta acción no se puede deshacer.")
            .setPositiveButton("Sí, cancelar") { _, _ ->
                borrarNegociacionCompleta()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun borrarNegociacionCompleta() {
        val idChat = chatId ?: return

        val updates = mapOf(
            "activo" to false,
            "ultimoMensaje" to "Trato cancelado"
        )

        db.collection("negociaciones").document(idChat)
            .update(updates)
            .addOnSuccessListener {
                db.collection("negociaciones").document(idChat).collection("mensajes")
                    .get()
                    .addOnSuccessListener { mensajes ->
                        val batch = db.batch()
                        for (doc in mensajes) batch.delete(doc.reference)
                        batch.commit().addOnSuccessListener {
                            Toast.makeText(this, "Negociación finalizada", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
            }
            .addOnFailureListener { e ->
                Log.e("DELETE_ERROR", "Error: ${e.message}")
                Toast.makeText(this, "No se pudo cancelar el trato", Toast.LENGTH_SHORT).show()
            }
    }
    private fun ejecutarConsultaIA(prompt: String, bitmap: Bitmap? = null) {
        val esVision = bitmap != null
        val modelo = if (esVision) "llama-3.2-11b-vision-preview" else "llama3-8b-8192"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val content: Any = if (esVision) {
                    listOf(
                        mapOf("type" to "text", "text" to prompt),
                        mapOf("type" to "image_url", "image_url" to mapOf(
                            "url" to "data:image/jpeg;base64,${prepararImagenBase64(bitmap!!)}"
                        ))
                    )
                } else prompt

                val messages = listOf(mapOf("role" to "user", "content" to content))
                val requestBody = mapOf(
                    "model" to modelo,
                    "messages" to messages,
                    "max_tokens" to 1000,
                    "temperature" to 0.7
                )

                val response = groqService.getCompletion(GROQ_API_KEY, requestBody)
                val respuestaIA = response.choices[0].message.content

                withContext(Dispatchers.Main) {
                    enviarMensaje("IA: $respuestaIA", "TEXTO")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("GROQ_ERROR", "Error: ${e.message}")
                }
            }
        }
    }

    private fun prepararImagenBase64(bitmap: Bitmap): String {
        val maxSize = 512
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val newWidth = if (bitmap.width > bitmap.height) maxSize else (maxSize * ratio).toInt()
        val newHeight = if (bitmap.height > bitmap.width) maxSize else (maxSize / ratio).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 25, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun setupRecyclerView() {
        val miId = auth.currentUser?.uid ?: ""

        adapter = ChatAdapter(
            miId,
            esTrabajador,
            { mensaje, nuevoEstado -> actualizarEstadoOferta(mensaje, nuevoEstado) },
            { mensaje -> mostrarDialogoEliminar(mensaje) }
        )

        findViewById<RecyclerView>(R.id.rvChatNegociacion).apply {
            layoutManager = LinearLayoutManager(this@NegociacionActivity)
            adapter = this@NegociacionActivity.adapter
        }
    }
    private fun mostrarDialogoEliminar(mensaje: Mensaje) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar mensaje")
            .setMessage("¿Deseas eliminar este mensaje para todos?")
            .setPositiveButton("Eliminar") { _, _ ->
                db.collection("negociaciones").document(chatId!!)
                    .collection("mensajes")
                    .whereEqualTo("timestamp", mensaje.timestamp)
                    .get()
                    .addOnSuccessListener { docs ->
                        for (doc in docs) doc.reference.delete()
                        Toast.makeText(this, "Mensaje eliminado", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun enviarMensaje(contenido: String, tipo: String, monto: Double? = null) {
        val uid = auth.currentUser?.uid ?: return
        val nombreSocio = intent.getStringExtra("SOCIO_NOMBRE") ?: "Socio"

        val updatesRaiz = hashMapOf<String, Any>(
            "ultimoMensaje" to contenido,
            "timestamp" to System.currentTimeMillis(),
            "activo" to true
        )

        db.collection("negociaciones").document(chatId!!)
            .update(updatesRaiz)
            .addOnFailureListener {
                val dataInicial = hashMapOf(
                    "clienteId" to if (esTrabajador) receptorId ?: "" else uid,
                    "trabajadorId" to if (esTrabajador) uid else (receptorId ?: ""),
                    "ultimoMensaje" to contenido,
                    "timestamp" to System.currentTimeMillis(),
                    "nombreCliente" to "Usuario DominGO",
                    "nombreTrabajador" to if (esTrabajador) "Yo" else nombreSocio,
                    "activo" to true
                )
                db.collection("negociaciones").document(chatId!!).set(dataInicial)
            }

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
        val btnIrAPagar = findViewById<Button>(R.id.btnIrAPagar)
        val btnContinuarProceso = findViewById<Button>(R.id.btnContinuarProceso)

        db.collection("negociaciones").document(chatId!!)
            .collection("mensajes")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val lista = snapshot.toObjects(Mensaje::class.java)
                    adapter.actualizarMensajes(lista)

                    val ofertaAceptada = lista.find { it.tipo == "OFERTA" && it.estadoOferta == "ACEPTADO" }

                    if (ofertaAceptada != null) {
                        if (!esTrabajador) {
                            btnContinuarProceso.visibility = View.VISIBLE
                            btnContinuarProceso.text = "CONTINUAR PROCESO S/ ${String.format("%.2f", ofertaAceptada.montoOferta)}"
                            btnContinuarProceso.setOnClickListener {
                                val intentTicket = Intent(this, TicketClienteActivity::class.java).apply {
                                    putExtra("CHAT_ID", chatId)
                                    putExtra("MONTO_SERVICIO", ofertaAceptada.montoOferta)
                                    putExtra("TRABAJADOR_ID", receptorId)
                                    putExtra("SOCIO_NOMBRE", intent.getStringExtra("SOCIO_NOMBRE"))
                                }
                                startActivity(intentTicket)
                            }
                        } else {
                            btnIrAPagar.visibility = View.GONE
                            btnContinuarProceso.visibility = View.GONE
                        }
                    } else {
                        btnIrAPagar.visibility = View.GONE
                        btnContinuarProceso.visibility = View.GONE
                    }

                    if (lista.isNotEmpty()) {
                        findViewById<RecyclerView>(R.id.rvChatNegociacion).scrollToPosition(lista.size - 1)
                    }
                }
            }
    }

    private fun actualizarEstadoOferta(mensaje: Mensaje, nuevoEstado: String) {
        if (chatId == null) return

        // Buscamos el documento del mensaje específico
        db.collection("negociaciones").document(chatId!!)
            .collection("mensajes")
            .whereEqualTo("timestamp", mensaje.timestamp)
            .get()
            .addOnSuccessListener { docs ->
                for (doc in docs) {
                    doc.reference.update("estadoOferta", nuevoEstado)
                        .addOnSuccessListener {
                            val accion = if (nuevoEstado == "ACEPTADO") "aceptada" else "rechazada"
                            Toast.makeText(this, "Oferta $accion", Toast.LENGTH_SHORT).show()

                            // Si el trabajador acepta, enviamos un aviso automático al chat
                            if (nuevoEstado == "ACEPTADO" && esTrabajador) {
                                enviarMensaje("✅ He aceptado la oferta. ¡Estoy listo para iniciar!", "TEXTO")
                            }
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al actualizar oferta", Toast.LENGTH_SHORT).show()
            }
    }

    private fun mostrarDialogoMetodoPago(monto: Double) {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.layout_metodo_pago, null)
        builder.setView(view)

        val dialog = builder.create()
        val rgMetodos = view.findViewById<RadioGroup>(R.id.rgMetodosPago)
        val btnConfirmar = view.findViewById<Button>(R.id.btnConfirmarPago)

        btnConfirmar.setOnClickListener {
            val seleccionadoId = rgMetodos.checkedRadioButtonId
            if (seleccionadoId == -1) {
                Toast.makeText(this, "Selecciona un método", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val metodo = when (seleccionadoId) {
                R.id.rbYape -> "Yape"
                R.id.rbPlin -> "Plin"
                R.id.rbEfectivo -> "Efectivo"
                else -> "Efectivo"
            }

            val intentFinalizar = Intent(this, FinalizarServicioActivity::class.java).apply {
                putExtra("MONTO_FINAL", monto)
                putExtra("METODO_PAGO", metodo)
                putExtra("RECEPTOR_ID", receptorId)
                putExtra("SOCIO_NOMBRE", intent.getStringExtra("SOCIO_NOMBRE"))
            }
            startActivity(intentFinalizar)
            dialog.dismiss()
        }
        dialog.show()
    }
}