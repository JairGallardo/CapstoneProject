package com.example.domingo.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
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

    private fun setupRecyclerView() {
        val miId = auth.currentUser?.uid ?: ""

        adapter = ChatAdapter(
            miId,
            esTrabajador,
            { mensaje, nuevoEstado -> actualizarEstadoOferta(mensaje, nuevoEstado) },
            { mensaje -> mostrarDialogoEliminar(mensaje) },
            { mensaje -> mostrarConfirmacionFinTrabajo(mensaje) },
            { mensaje -> abrirPantallaDePago(mensaje) }
        )

        findViewById<RecyclerView>(R.id.rvChatNegociacion).apply {
            layoutManager = LinearLayoutManager(this@NegociacionActivity)
            adapter = this@NegociacionActivity.adapter
        }
    }

    private fun mostrarConfirmacionFinTrabajo(mensaje: Mensaje) {
        AlertDialog.Builder(this)
            .setTitle("¿Terminaste el trabajo?")
            .setMessage("Se notificará al cliente para que proceda con el pago.")
            .setPositiveButton("Sí, terminar") { _, _ ->
                enviarMensaje("🏁 TRABAJO TERMINADO. Esperando pago.", "FIN_TRABAJO")
            }
            .setNegativeButton("Aún no", null)
            .show()
    }

    private fun abrirPantallaDePago(mensaje: Mensaje) {
        db.collection("tickets")
            .whereEqualTo("chatId", chatId)
            .get()
            .addOnSuccessListener { docs ->
                val doc = docs.documents.firstOrNull()
                if (doc != null) {
                    val monto = doc.getDouble("costoTotal") ?: 0.0
                    mostrarDialogoMetodoPago(monto)
                } else {
                    Toast.makeText(this, "No se encontró un ticket activo para este chat", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("PAGO_ERROR", "Error al buscar ticket: ${e.message}")
                Toast.makeText(this, "Error al conectar con la base de datos", Toast.LENGTH_SHORT).show()
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
            .setMessage("¿Estás seguro de que deseas cancelar la negociación y borrar todo el historial?")
            .setPositiveButton("Sí, cancelar") { _, _ ->
                borrarNegociacionCompleta()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun borrarNegociacionCompleta() {
        val idChat = chatId ?: return
        val updates = mapOf("activo" to false, "ultimoMensaje" to "Trato cancelado")

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
        val nombreSocioChat = intent.getStringExtra("SOCIO_NOMBRE") ?: "Socio"

        db.collection("usuarios").document(uid).get().addOnSuccessListener { miDoc ->
            val miNombre = miDoc.getString("nombre") ?: "Usuario"
            val miFoto = miDoc.getString("fotoPerfilB64") ?: ""

            receptorId?.let { rId ->
                db.collection("usuarios").document(rId).get().addOnSuccessListener { receptorDoc ->
                    val nombreReceptor = receptorDoc.getString("nombre") ?: nombreSocioChat
                    val fotoReceptor = receptorDoc.getString("fotoPerfilB64") ?: ""

                    val nombreCliente = if (esTrabajador) nombreReceptor else miNombre
                    val nombreTrabajador = if (esTrabajador) miNombre else nombreReceptor
                    val fotoCliente = if (esTrabajador) fotoReceptor else miFoto
                    val fotoTrabajador = if (esTrabajador) miFoto else fotoReceptor

                    val dataNegociacion = hashMapOf(
                        "clienteId" to if (esTrabajador) rId else uid,
                        "trabajadorId" to if (esTrabajador) uid else rId,
                        "nombreCliente" to nombreCliente,
                        "nombreTrabajador" to nombreTrabajador,
                        "fotoClienteB64" to fotoCliente,
                        "fotoTrabajadorB64" to fotoTrabajador,
                        "ultimoMensaje" to contenido,
                        "timestamp" to System.currentTimeMillis(),
                        "activo" to true
                    )

                    db.collection("negociaciones").document(chatId!!).set(dataNegociacion, com.google.firebase.firestore.SetOptions.merge())
                }
            }
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
        db.collection("negociaciones").document(chatId!!)
            .collection("mensajes")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val lista = snapshot.toObjects(Mensaje::class.java)

                    val layoutOferta = findViewById<View>(R.id.layoutOferta)
                    val btnContinuar = findViewById<Button>(R.id.btnContinuarProceso)

                    gestionarVisibilidadPaneles(lista, layoutOferta, btnContinuar)
                    val pagoConfirmado = lista.find { it.tipo == "PAGO_REALIZADO" && it.estadoOferta == "PAGO_CONFIRMADO" }

                    if (pagoConfirmado != null && !esTrabajador) {
                        val contenido = pagoConfirmado.contenido
                        val metodoDetectado = when {
                            contenido.contains("Yape", ignoreCase = true) -> "Yape"
                            contenido.contains("Plin", ignoreCase = true) -> "Plin"
                            contenido.contains("Efectivo", ignoreCase = true) -> "Efectivo"
                            else -> "Otro"
                        }

                        val intentFinal = Intent(this, FinalizarServicioActivity::class.java).apply {
                            putExtra("MONTO_FINAL", pagoConfirmado.montoOferta)
                            putExtra("SOCIO_NOMBRE", intent.getStringExtra("SOCIO_NOMBRE"))
                            putExtra("METODO_PAGO", metodoDetectado)
                            putExtra("RECEPTOR_ID", receptorId)
                            putExtra("CHAT_ID", chatId)
                        }
                        startActivity(intentFinal)
                        finish()
                    }

                    adapter.actualizarMensajes(lista)
                }
            }
    }

    private fun gestionarVisibilidadPaneles(lista: List<Mensaje>, layoutOferta: View, btnContinuar: Button) {
        val existeAlgunaOferta = lista.any { it.tipo == "OFERTA" }
        val ofertaAceptada = lista.find { it.tipo == "OFERTA" && it.estadoOferta == "ACEPTADO" }
        val ticketYaGenerado = lista.any { it.tipo == "TICKET" }

        if (esTrabajador) {
            layoutOferta.visibility = if (ticketYaGenerado) View.GONE else View.VISIBLE
        } else {
            layoutOferta.visibility = if (existeAlgunaOferta && !ticketYaGenerado) View.VISIBLE else View.GONE
        }

        if (ofertaAceptada != null && !esTrabajador && !ticketYaGenerado) {
            btnContinuar.visibility = View.VISIBLE
            btnContinuar.text = "GENERAR TICKET S/ ${String.format("%.2f", ofertaAceptada.montoOferta)}"

            btnContinuar.setOnClickListener {
                val intentTicket = Intent(this, TicketClienteActivity::class.java).apply {
                    putExtra("CHAT_ID", chatId)
                    putExtra("MONTO_SERVICIO", ofertaAceptada.montoOferta)
                    putExtra("TRABAJADOR_ID", receptorId)
                    putExtra("SOCIO_NOMBRE", intent.getStringExtra("SOCIO_NOMBRE"))
                }
                startActivity(intentTicket)
            }
        } else {
            btnContinuar.visibility = View.GONE
        }
    }

    private fun actualizarEstadoOferta(mensaje: Mensaje, nuevoEstado: String) {
        if (chatId == null) return
        db.collection("negociaciones").document(chatId!!)
            .collection("mensajes")
            .whereEqualTo("timestamp", mensaje.timestamp)
            .get()
            .addOnSuccessListener { docs ->
                for (doc in docs) {
                    doc.reference.update("estadoOferta", nuevoEstado)
                        .addOnSuccessListener {
                            if (nuevoEstado == "ACEPTADO" && esTrabajador) {
                                enviarMensaje("✅ He aceptado la oferta. ¡Estoy listo para iniciar!", "TEXTO")
                            }
                        }
                }
            }
    }

    private fun mostrarDialogoMetodoPago(monto: Double) {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.layout_metodo_pago, null)
        builder.setView(view)

        val dialog = builder.create()

        val rgMetodos = view.findViewById<RadioGroup>(R.id.rgMetodosPago)
        val layoutDetalles = view.findViewById<LinearLayout>(R.id.layoutDetallesPago)
        val tvNombre = view.findViewById<TextView>(R.id.tvNombreTitular)
        val tvCelular = view.findViewById<TextView>(R.id.tvCelularPago)
        val ivQrPago = view.findViewById<ImageView>(R.id.ivQrPago)
        val btnConfirmar = view.findViewById<Button>(R.id.btnConfirmarPago)

        var qrYapeRecuperado: String? = null
        var qrPlinRecuperado: String? = null

        receptorId?.let { id ->
            db.collection("usuarios").document(id).get()
                .addOnSuccessListener { doc ->
                    val nombre = doc.getString("nombre") ?: "Sin nombre"
                    val celular = doc.getString("telefono") ?: "Sin número"

                    qrYapeRecuperado = doc.getString("qrYapeB64")
                    qrPlinRecuperado = doc.getString("qrPlinB64")

                    tvNombre.text = "Titular: $nombre"
                    tvCelular.text = "Número: $celular"

                    if (!qrYapeRecuperado.isNullOrEmpty()) {
                        mostrarImagenBase64(qrYapeRecuperado!!, ivQrPago)
                    }
                }
        }

        rgMetodos.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbYape -> {
                    layoutDetalles.visibility = View.VISIBLE
                    if (!qrYapeRecuperado.isNullOrEmpty()) {
                        mostrarImagenBase64(qrYapeRecuperado!!, ivQrPago)
                    } else {
                        ivQrPago.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
                R.id.rbPlin -> {
                    layoutDetalles.visibility = View.VISIBLE
                    if (!qrPlinRecuperado.isNullOrEmpty()) {
                        mostrarImagenBase64(qrPlinRecuperado!!, ivQrPago)
                    } else {
                        ivQrPago.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
                else -> {
                    layoutDetalles.visibility = View.GONE
                }
            }
        }

        btnConfirmar.setOnClickListener {
            val seleccionadoId = rgMetodos.checkedRadioButtonId
            if (seleccionadoId == -1) {
                Toast.makeText(this, "Selecciona un método", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val metodo = when (seleccionadoId) {
                R.id.rbYape -> "Yape"
                R.id.rbPlin -> "Plin"
                else -> "Efectivo"
            }

            enviarMensaje("📱 He enviado el pago por $metodo. Esperando confirmación...", "PAGO_REALIZADO", monto)
            dialog.dismiss()
            mostrarPantallaEsperaPago(monto, metodo)
        }
        dialog.show()
    }

    private fun mostrarImagenBase64(base64String: String, imageView: ImageView) {
        try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            val decodedByte = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            imageView.setImageBitmap(decodedByte)
        } catch (e: Exception) {
            Log.e("ERROR_IMAGE", "Error al decodificar Base64: ${e.message}")
        }
    }

    private fun mostrarPantallaEsperaPago(monto: Double, metodo: String) {
        val builder = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val view = layoutInflater.inflate(R.layout.layout_esperando_confirmacion, null)
        builder.setView(view)
        builder.setCancelable(false)
        val dialogEspera = builder.create()
        dialogEspera.show()

    }
}