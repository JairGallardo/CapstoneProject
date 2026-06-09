package com.example.domingo.ui.negociacion

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.R
import com.example.domingo.model.Mensaje
import com.example.domingo.ui.finalizarservicio.FinalizarServicioActivity
import com.example.domingo.ui.ticketcliente.TicketClienteActivity
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import java.io.ByteArrayOutputStream

class NegociacionActivity : AppCompatActivity() {

    private val viewModel: NegociacionViewModel by viewModels()
    private lateinit var adapter: ChatAdapter

    private lateinit var chatId: String
    private var receptorId: String? = null
    private var esTrabajador: Boolean = false
    private var nombreSocio: String = "Socio"
    private var modoSoloLectura: Boolean = false

    private var bitmapSeleccionado: Bitmap? = null
    private var dialogIAView: View? = null
    private var dialogVistaPrevia: AlertDialog? = null

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let { mostrarDialogoOpcionesImagen(it) }
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                bitmap?.let { bmp -> mostrarDialogoOpcionesImagen(bmp) }
            } catch (e: Exception) {
                Log.e("GALLERY", "${e.message}")
            }
        }
    }

    private val permisoCameraLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        when {
            concedido -> {
                cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            }
            !ActivityCompat.shouldShowRequestPermissionRationale(
                this, android.Manifest.permission.CAMERA
            ) -> {

                AlertDialog.Builder(this)
                    .setTitle("Permiso de cámara necesario")
                    .setMessage("Debes habilitar el permiso de cámara manualmente desde Ajustes > Aplicaciones > Domingo > Permisos.")
                    .setPositiveButton("Ir a Ajustes") { _, _ ->
                        startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", packageName, null)
                            )
                        )
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            else -> {
                Toast.makeText(this, "Permiso de cámara denegado. Vuelve a intentarlo.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_negociacion)

        chatId = intent.getStringExtra("CHAT_ID") ?: run { finish(); return }
        receptorId    = intent.getStringExtra("RECEPTOR_ID")
        esTrabajador  = intent.getBooleanExtra("ES_TRABAJADOR", false)
        nombreSocio   = intent.getStringExtra("SOCIO_NOMBRE") ?: "Socio"
        modoSoloLectura = intent.getBooleanExtra("SOLO_LECTURA", false)

        Log.d("DEBUG_NEGOCIACION", "chatId=$chatId | receptor=$receptorId | trabajador=$esTrabajador")

        findViewById<TextView>(R.id.tvNombreChat).text = nombreSocio

        setupRecyclerView()
        configurarObservadores()

        val layoutOferta = findViewById<View>(R.id.layoutOferta)
        val btnContinuar = findViewById<Button>(R.id.btnContinuarProceso)
        gestionarVisibilidadPaneles(emptyList(), layoutOferta, btnContinuar)

        viewModel.cargarEspecialidad(receptorId)
        viewModel.inicializarEscuchaMensajes(chatId)

        val etMontoOferta   = findViewById<EditText>(R.id.etMontoOferta)
        val btnEnviarOferta = findViewById<Button>(R.id.btnEnviarOferta)
        val etMensajeChat   = findViewById<EditText>(R.id.etMensajeChat)
        val btnEnviarMensaje = findViewById<ImageButton>(R.id.btnEnviarMensaje)
        val btnEliminarChat = findViewById<ImageButton>(R.id.btnEliminarChatCompleto)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            gestionarVisibilidadPaneles(viewModel.mensajes.value ?: emptyList(), layoutOferta, btnContinuar)
        }, 800)

        if (modoSoloLectura) congelarChatInterfaz()

        btnEliminarChat?.setOnClickListener { mostrarDialogoCancelarNegociacion() }

        btnEnviarOferta.setOnClickListener {
            val monto = etMontoOferta.text.toString().trim().toDoubleOrNull()
            if (monto != null && monto > 0) {
                viewModel.enviarMensajeEstructurado(chatId, "Propuesta de precio", "OFERTA", monto, receptorId, esTrabajador, nombreSocio)
                etMontoOferta.text.clear()
                Toast.makeText(this, "Oferta enviada", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Ingrese un monto válido", Toast.LENGTH_SHORT).show()
            }
        }

        btnEnviarMensaje.setOnClickListener {
            val texto = etMensajeChat.text.toString().trim()
            if (texto.isNotEmpty()) {
                viewModel.enviarMensajeEstructurado(chatId, texto, "TEXTO", null, receptorId, esTrabajador, nombreSocio)
                etMensajeChat.text.clear()
            }
        }

        val btnAsistenteIA = findViewById<ImageButton>(R.id.btnAsistenteIA)
        if (!esTrabajador && !modoSoloLectura) {
            btnAsistenteIA?.visibility = View.VISIBLE
            btnAsistenteIA?.setOnClickListener { mostrarDialogoOrigenImagen() }
        } else {
            btnAsistenteIA?.visibility = View.GONE
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(
            userId             = viewModel.obtenerMiId(),
            esTrabajador       = esTrabajador,
            onOfertaClick      = { mensaje, nuevoEstado ->
                viewModel.actualizarEstadoOferta(chatId, mensaje, nuevoEstado, receptorId, esTrabajador, nombreSocio)
            },
            onMensajeLongClick = { mensaje -> mostrarDialogoEliminar(mensaje) },
            onLlegadaClick     = { mensaje ->
                viewModel.enviarMensajeEstructurado(
                    chatId, "📍 He llegado a tu domicilio.", "LLEGADA_DOMICILIO",
                    null, receptorId, esTrabajador, nombreSocio
                )
            },
            onFinalizarClick   = { mensaje -> mostrarConfirmacionFinTrabajo(mensaje) },
            onPagarClick       = { _ -> viewModel.obtenerDatosDePago(chatId) },
            onImagenClick      = { base64 -> mostrarImagenExpandida(base64) }
        )

        findViewById<RecyclerView>(R.id.rvChatNegociacion).apply {
            layoutManager = LinearLayoutManager(this@NegociacionActivity)
            adapter = this@NegociacionActivity.adapter
        }
    }

    private fun configurarObservadores() {
        viewModel.escucharEstadoActivo(chatId)
        viewModel.chatActivoEstado.observe(this) { esActivo ->
            if (!esActivo) congelarChatInterfaz()
        }

        viewModel.mensajes.observe(this) { lista ->
            val layoutOferta = findViewById<View>(R.id.layoutOferta)
            val btnContinuar = findViewById<Button>(R.id.btnContinuarProceso)

            if (modoSoloLectura) {
                layoutOferta?.visibility = View.GONE
                btnContinuar?.visibility = View.GONE
            } else {
                gestionarVisibilidadPaneles(lista, layoutOferta, btnContinuar)

                val pConfirmado = lista.find { it.tipo == "PAGO_REALIZADO" && it.estadoOferta == "PAGO_CONFIRMADO" }
                if (pConfirmado != null) {
                    val miId            = viewModel.obtenerMiId()
                    val clienteIdStr    = if (esTrabajador) receptorId else miId
                    val trabajadorIdStr = if (esTrabajador) miId else receptorId
                    val miRolStr        = if (esTrabajador) "trabajador" else "cliente"

                    startActivity(Intent(this, FinalizarServicioActivity::class.java).apply {
                        putExtra("MONTO_FINAL",   pConfirmado.montoOferta)
                        putExtra("CLIENTE_ID",    clienteIdStr)
                        putExtra("TRABAJADOR_ID", trabajadorIdStr)
                        putExtra("MI_ROL",        miRolStr)
                        putExtra("SOCIO_NOMBRE",  nombreSocio)
                        putExtra("METODO_PAGO",   "Digital/Efectivo")
                        putExtra("CHAT_ID",       chatId)
                    })
                    finish()
                }
            }
            adapter.actualizarMensajes(lista)
        }

        viewModel.ticketMonto.observe(this) { monto ->
            if (!modoSoloLectura) mostrarDialogoMetodoPago(monto)
        }

        viewModel.chatCancelado.observe(this) { finalizado ->
            if (finalizado) { Toast.makeText(this, "Negociación finalizada", Toast.LENGTH_SHORT).show(); finish() }
        }

        viewModel.error.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        viewModel.resultadoIA.observe(this) { resultado ->
            resultado ?: return@observe
            dialogVistaPrevia?.dismiss()
            mostrarVistaPreviaIA(resultado)
            viewModel.limpiarResultadoIA()
        }
    }

    private fun mostrarDialogoOrigenImagen() {
        if (modoSoloLectura) return

        AlertDialog.Builder(this)
            .setTitle("Subir imagen")
            .setMessage("¿Cómo deseas agregar la foto?")
            .setPositiveButton("📷  Cámara") { _, _ -> abrirCamara() }
            .setNegativeButton("🖼  Galería") { _, _ -> galleryLauncher.launch("image/*") }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun abrirCamara() {
        val permiso = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
        if (permiso == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        } else {
            permisoCameraLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun mostrarDialogoOpcionesImagen(bitmap: Bitmap) {
        bitmapSeleccionado = bitmap

        val view = layoutInflater.inflate(R.layout.dialog_opciones_imagen, null)
        val ivPreview = view.findViewById<ImageView>(R.id.ivPreviewOpciones)
        ivPreview.setImageBitmap(bitmap)

        AlertDialog.Builder(this)
            .setView(view)
            .setTitle("Imagen seleccionada")
            .setPositiveButton("✨  Mejorar con IA") { _, _ -> mostrarDialogoInstruccionIA() }
            .setNegativeButton("📤  Enviar tal como está") { _, _ -> enviarImagenDirecta(bitmap) }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun enviarImagenDirecta(bitmap: Bitmap) {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

        viewModel.enviarMensajeConImagenSimple(
            chatId             = chatId,
            base64             = base64,
            receptorId         = receptorId,
            esTrabajador       = esTrabajador,
            nombreSocioDefault = nombreSocio
        )
        Toast.makeText(this, "Imagen enviada", Toast.LENGTH_SHORT).show()
    }

    private fun mostrarDialogoInstruccionIA() {
        val bitmap = bitmapSeleccionado ?: return

        val view      = layoutInflater.inflate(R.layout.dialog_asistente_ia, null)
        dialogIAView  = view

        val ivPreview       = view.findViewById<ImageView>(R.id.ivPreviewImagen)
        val layoutSinImg    = view.findViewById<View>(R.id.layoutSinImagen)
        val tvCategoria     = view.findViewById<TextView>(R.id.tvCategoriaIA)
        val etInstruccion   = view.findViewById<EditText>(R.id.etInstruccionIA)
        val btnCamara       = view.findViewById<Button>(R.id.btnTomarFoto)
        val btnGaleria      = view.findViewById<Button>(R.id.btnElegirGaleria)
        val btnGenerar      = view.findViewById<Button>(R.id.btnGenerarPropuestaIA)
        val layoutCargando  = view.findViewById<View>(R.id.layoutCargandoIA)

        ivPreview.setImageBitmap(bitmap)
        layoutSinImg.visibility = View.GONE

        tvCategoria.text = viewModel.especialidadReceptor.value ?: "Servicio"

        btnCamara.setOnClickListener { abrirCamara() }
        btnGaleria.setOnClickListener { galleryLauncher.launch("image/*") }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        btnGenerar.setOnClickListener {
            val instruccion = etInstruccion.text.toString().trim()
            val bmp         = bitmapSeleccionado ?: bitmap

            if (instruccion.isEmpty()) {
                Toast.makeText(this, "Describe lo que deseas hacer con la imagen", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            layoutCargando.visibility = View.VISIBLE
            btnGenerar.isEnabled  = false
            btnCamara.isEnabled   = false
            btnGaleria.isEnabled  = false

            val especialidad = viewModel.especialidadReceptor.value ?: "Servicio general"

            dialogVistaPrevia = dialog

            viewModel.generarPropuestaIA(
                scope              = lifecycleScope,
                bitmap             = bmp,
                instruccion        = instruccion,
                especialidad       = especialidad
            )

            Toast.makeText(this, "Procesando con IA, espera un momento...", Toast.LENGTH_LONG).show()
        }

        dialog.show()
    }

    private fun mostrarVistaPreviaIA(resultado: ResultadoIA) {
        val view = layoutInflater.inflate(R.layout.dialog_vista_previa_ia, null)

        val ivOriginal      = view.findViewById<ImageView>(R.id.ivVistaPreviaOriginal)
        val ivEditada       = view.findViewById<ImageView>(R.id.ivVistaPreviaEditada)
        val tvPropuesta     = view.findViewById<TextView>(R.id.tvVistaPreviaPropuesta)
        val etNuevaInstr    = view.findViewById<EditText>(R.id.etNuevaInstruccion)
        val btnEnviar       = view.findViewById<Button>(R.id.btnEnviarVistaPreviaIA)
        val btnReintentar   = view.findViewById<Button>(R.id.btnReintentarIA)
        val layoutReintentar = view.findViewById<View>(R.id.layoutNuevaInstruccion)
        val btnMostrarReintentar = view.findViewById<Button>(R.id.btnMostrarReintentar)
        val layoutCargandoPrevia = view.findViewById<View>(R.id.layoutCargandoPreviaIA)

        val bmpOriginal = bitmapSeleccionado
        if (bmpOriginal != null) ivOriginal.setImageBitmap(bmpOriginal)

        if (resultado.imagenEditadaBase64.isNotEmpty()) {
            try {
                val bytes = Base64.decode(resultado.imagenEditadaBase64, Base64.DEFAULT)
                val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ivEditada.setImageBitmap(bmp)
            } catch (e: Exception) {
                Log.e("VISTA_PREVIA", "Error cargando imagen editada: ${e.message}")
            }
        }

        tvPropuesta.text = resultado.propuestaTexto

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .setTitle("Vista previa — ¿Te gusta el resultado?")
            .create()

        btnEnviar.setOnClickListener {
            viewModel.enviarPropuestaIAConfirmada(
                chatId             = chatId,
                resultado          = resultado,
                imagenOriginalBase64 = resultado.imagenOriginalBase64,
                receptorId         = receptorId,
                esTrabajador       = esTrabajador,
                nombreSocioDefault = nombreSocio
            )
            dialog.dismiss()
            Toast.makeText(this, "Propuesta enviada ✓", Toast.LENGTH_SHORT).show()
        }

        btnMostrarReintentar.setOnClickListener {
            layoutReintentar.visibility = View.VISIBLE
            btnMostrarReintentar.visibility = View.GONE
        }

        btnReintentar.setOnClickListener {
            val nuevaInstruccion = etNuevaInstr.text.toString().trim()
            if (nuevaInstruccion.isEmpty()) {
                Toast.makeText(this, "Escribe qué deseas cambiar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            layoutCargandoPrevia.visibility = View.VISIBLE
            btnReintentar.isEnabled = false
            btnEnviar.isEnabled     = false

            val bmp          = bitmapSeleccionado ?: return@setOnClickListener
            val especialidad = viewModel.especialidadReceptor.value ?: "Servicio general"

            viewModel.generarPropuestaIA(
                scope        = lifecycleScope,
                bitmap       = bmp,
                instruccion  = nuevaInstruccion,
                especialidad = especialidad
            )

            dialogVistaPrevia = dialog
            dialog.dismiss()
        }

        dialog.show()
    }

    fun mostrarImagenExpandida(base64: String) {
        val view = layoutInflater.inflate(R.layout.dialog_imagen_expandida, null)

        val ivExpandida  = view.findViewById<ImageView>(R.id.ivImagenExpandida)
        val btnDescargar = view.findViewById<Button>(R.id.btnDescargarImagen)
        val btnCerrar    = view.findViewById<ImageButton>(R.id.btnCerrarImagenExpandida)

        var bitmapActual: Bitmap? = null

        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            bitmapActual = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ivExpandida.setImageBitmap(bitmapActual)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(view)
            .create()

        btnCerrar.setOnClickListener { dialog.dismiss() }

        btnDescargar.setOnClickListener {
            val bitmap = bitmapActual ?: return@setOnClickListener
            guardarImagenEnGaleria(bitmap)
        }

        dialog.show()
    }

    private fun guardarImagenEnGaleria(bitmap: Bitmap) {
        try {
            val nombre = "Negociacion_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, nombre)
                put(MediaStore.Images.Media.MIME_TYPE,    "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Domingo")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                Toast.makeText(this, "Imagen guardada en Galería ✓", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo guardar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun actualizarPreviewEnDialog(bitmap: Bitmap) {
        bitmapSeleccionado = bitmap
        val view = dialogIAView ?: return
        val ivPreview    = view.findViewById<ImageView>(R.id.ivPreviewImagen)
        val layoutSinImg = view.findViewById<View>(R.id.layoutSinImagen)
        ivPreview.setImageBitmap(bitmap)
        layoutSinImg.visibility = View.GONE
    }

    private fun congelarChatInterfaz() {
        modoSoloLectura = true
        findViewById<View>(R.id.layoutOferta)?.visibility            = View.GONE
        findViewById<View>(R.id.btnEliminarChatCompleto)?.visibility = View.GONE
        findViewById<View>(R.id.btnAsistenteIA)?.visibility          = View.GONE

        val etMensajeChat = findViewById<EditText>(R.id.etMensajeChat)
        etMensajeChat?.isEnabled = false
        etMensajeChat?.hint      = "Conversación finalizada (Solo Lectura)"

        findViewById<ImageButton>(R.id.btnEnviarMensaje)?.visibility = View.GONE
        findViewById<Button>(R.id.btnEnviarOferta)?.visibility       = View.GONE
        findViewById<EditText>(R.id.etMontoOferta)?.visibility       = View.GONE
        findViewById<Button>(R.id.btnContinuarProceso)?.visibility   = View.GONE
        findViewById<Button>(R.id.btnIrAPagar)?.visibility           = View.GONE
    }

    private fun gestionarVisibilidadPaneles(lista: List<Mensaje>, layoutOferta: View, btnContinuar: Button) {
        if (modoSoloLectura) {
            layoutOferta.visibility = View.GONE
            btnContinuar.visibility = View.GONE
            return
        }
        val ticketYaGenerado  = lista.any { it.tipo == "TICKET" }
        val hayOfertaAceptada = lista.any { it.tipo == "OFERTA" && it.estadoOferta == "ACEPTADO" }

        layoutOferta.visibility = if (esTrabajador && !ticketYaGenerado) View.VISIBLE else View.GONE

        if (!esTrabajador && hayOfertaAceptada && !ticketYaGenerado) {
            btnContinuar.visibility = View.VISIBLE
            btnContinuar.text       = "GENERAR TICKET"
            btnContinuar.setOnClickListener {
                val monto = lista.find { it.tipo == "OFERTA" && it.estadoOferta == "ACEPTADO" }?.montoOferta ?: 0.0
                startActivity(Intent(this, TicketClienteActivity::class.java).apply {
                    putExtra("CHAT_ID",       chatId)
                    putExtra("MONTO_SERVICIO", monto)
                    putExtra("TRABAJADOR_ID", receptorId)
                    putExtra("SOCIO_NOMBRE",  nombreSocio)
                })
            }
        } else {
            btnContinuar.visibility = View.GONE
        }
    }

    private fun mostrarConfirmacionFinTrabajo(mensaje: Mensaje) {
        if (modoSoloLectura) return
        AlertDialog.Builder(this)
            .setTitle("¿Terminaste el trabajo?")
            .setMessage("Se notificará al cliente para que proceda con el pago.")
            .setPositiveButton("Sí, terminar") { _, _ ->
                viewModel.enviarMensajeEstructurado(chatId, "🏁 TRABAJO TERMINADO. Esperando pago.", "FIN_TRABAJO", null, receptorId, esTrabajador, nombreSocio)
            }
            .setNegativeButton("Aún no", null)
            .show()
    }

    private fun mostrarDialogoCancelarNegociacion() {
        if (modoSoloLectura) return
        val mensajesActuales  = adapter.mensajes
        val ticketYaGenerado  = mensajesActuales.any { it.tipo == "TICKET" }

        if (!ticketYaGenerado) {
            AlertDialog.Builder(this)
                .setTitle("Cancelar Negociación")
                .setMessage("¿Estás seguro de que deseas salirte de la negociación en curso?")
                .setPositiveButton("Sí, cancelar") { _, _ -> viewModel.borrarNegociacion(chatId) }
                .setNegativeButton("Volver", null)
                .show()
        } else {
            if (!esTrabajador) {
                val opciones = arrayOf(
                    "El trabajador no se presentó (Penalizar)",
                    "El trabajador canceló por chat/llamada interna",
                    "Cambié de opinión (Cancelación mutua)"
                )
                AlertDialog.Builder(this)
                    .setTitle("¿Por qué deseas cancelar?")
                    .setItems(opciones) { _, pos ->
                        when (pos) {
                            0 -> viewModel.cancelarServicioConJustificacion(chatId, "El trabajador no se presentó.", receptorId, esTrabajador, nombreSocio, aplicarPenalizacion = true)
                            1 -> viewModel.cancelarServicioConJustificacion(chatId, "El trabajador canceló por chat.", receptorId, esTrabajador, nombreSocio, aplicarPenalizacion = false)
                            2 -> viewModel.cancelarServicioConJustificacion(chatId, "Cancelación mutua.", receptorId, esTrabajador, nombreSocio, aplicarPenalizacion = false)
                        }
                    }
                    .setNegativeButton("Volver", null)
                    .show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Reportar Inasistencia del Cliente")
                    .setMessage("¿Te encuentras en el lugar y el cliente no responde?")
                    .setPositiveButton("Reportar y Cerrar") { _, _ ->
                        viewModel.cancelarServicioConJustificacion(chatId, "El cliente no se presentó.", receptorId, esTrabajador, nombreSocio, aplicarPenalizacion = true)
                    }
                    .setNegativeButton("Esperar", null)
                    .show()
            }
        }
    }

    private fun mostrarDialogoEliminar(mensaje: Mensaje) {
        if (modoSoloLectura) return
        AlertDialog.Builder(this)
            .setTitle("Eliminar mensaje")
            .setMessage("¿Deseas eliminar este mensaje para todos?")
            .setPositiveButton("Eliminar") { _, _ ->
                FirebaseFirestore.getInstance()
                    .collection("negociaciones").document(chatId)
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

    private fun mostrarDialogoMetodoPago(monto: Double) {
        if (modoSoloLectura) return
        val view = layoutInflater.inflate(R.layout.layout_metodo_pago, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()

        val rgMetodos    = view.findViewById<RadioGroup>(R.id.rgMetodosPago)
        val layoutDet    = view.findViewById<LinearLayout>(R.id.layoutDetallesPago)
        val tvNombre     = view.findViewById<TextView>(R.id.tvNombreTitular)
        val tvCelular    = view.findViewById<TextView>(R.id.tvCelularPago)
        val ivQr         = view.findViewById<ImageView>(R.id.ivQrPago)
        val btnConfirmar = view.findViewById<Button>(R.id.btnConfirmarPago)

        var qrYape: String? = null
        var qrPlin: String? = null

        receptorId?.let { id ->
            viewModel.obtenerDatosUsuarioEspecial(id) { doc ->
                tvNombre.text  = "Titular: ${doc.getString("nombre") ?: "Sin nombre"}"
                tvCelular.text = "Número: ${doc.getString("telefono") ?: "Sin número"}"
                qrYape = doc.getString("qrYapeB64")
                qrPlin = doc.getString("qrPlinB64")
                if (!qrYape.isNullOrEmpty()) mostrarImagenBase64(qrYape!!, ivQr)
            }
        }

        rgMetodos.setOnCheckedChangeListener { _, checkedId ->
            layoutDet.visibility = View.VISIBLE
            when (checkedId) {
                R.id.rbYape -> if (!qrYape.isNullOrEmpty()) mostrarImagenBase64(qrYape!!, ivQr) else ivQr.setImageResource(android.R.drawable.ic_menu_gallery)
                R.id.rbPlin -> if (!qrPlin.isNullOrEmpty()) mostrarImagenBase64(qrPlin!!, ivQr) else ivQr.setImageResource(android.R.drawable.ic_menu_gallery)
                else        -> layoutDet.visibility = View.GONE
            }
        }

        btnConfirmar.setOnClickListener {
            val selId = rgMetodos.checkedRadioButtonId
            if (selId == -1) { Toast.makeText(this, "Selecciona un método", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val metodo = if (selId == R.id.rbYape) "Yape" else if (selId == R.id.rbPlin) "Plin" else "Efectivo"
            viewModel.enviarMensajeEstructurado(chatId, "📱 He enviado el pago por $metodo. Esperando confirmación...", "PAGO_REALIZADO", monto, receptorId, esTrabajador, nombreSocio)
            dialog.dismiss()
            mostrarPantallaEsperaPago()
        }
        dialog.show()
    }

    private fun mostrarImagenBase64(base64String: String, imageView: ImageView) {
        try {
            val bytes = Base64.decode(base64String, Base64.DEFAULT)
            imageView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        } catch (e: Exception) {
            Log.e("IMAGE_BASE64", "${e.message}")
        }
    }

    private fun mostrarPantallaEsperaPago() {
        val view = layoutInflater.inflate(R.layout.layout_esperando_confirmacion, null)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
            .setView(view).setCancelable(false).create().show()
    }
}