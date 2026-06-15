package com.example.domingo.ui.negociacion

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.domingo.data.repository.NegociacionRepository
import com.example.domingo.model.Mensaje
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class NegociacionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NegociacionRepository(application.applicationContext)

    private val _mensajes = MutableLiveData<List<Mensaje>>()
    val mensajes: LiveData<List<Mensaje>> get() = _mensajes

    private val _especialidadReceptor = MutableLiveData<String>()
    val especialidadReceptor: LiveData<String> get() = _especialidadReceptor

    private val _ticketMonto = MutableLiveData<Double>()
    val ticketMonto: LiveData<Double> get() = _ticketMonto

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    private val _chatCancelado = MutableLiveData<Boolean>()
    val chatCancelado: LiveData<Boolean> get() = _chatCancelado

    private val _chatActivoEstado = MutableLiveData<Boolean>()
    val chatActivoEstado: LiveData<Boolean> get() = _chatActivoEstado

    private val _cargandoIA = MutableLiveData<Boolean>()
    val cargandoIA: LiveData<Boolean> get() = _cargandoIA

    private val _resultadoIA = MutableLiveData<ResultadoIA?>()
    val resultadoIA: LiveData<ResultadoIA?> get() = _resultadoIA

    private val _categoriaServicio = MutableLiveData<String>()
    val categoriaServicio: LiveData<String> get() = _categoriaServicio

    fun limpiarResultadoIA() { _resultadoIA.value = null }

    private val GROQ_API_KEY  = ""
    private val GROQ_MODEL    = "meta-llama/llama-4-scout-17b-16e-instruct"
    private val CF_ACCOUNT_ID = ""
    private val CF_API_TOKEN  = ""

    init {
        repository.guardarFcmTokenActual()
    }

    fun obtenerMiId(): String = repository.obtenerUsuarioId() ?: ""

    fun cargarEspecialidad(receptorId: String?) {
        receptorId?.let {
            repository.obtenerDatosUsuario(it) { doc ->
                _especialidadReceptor.value = doc.getString("especialidad") ?: "General"
            }
        }
    }

    fun cargarCategoriaServicio(chatId: String) {
        repository.obtenerDatosNegociacion(chatId) { doc ->
            _categoriaServicio.value = doc.getString("categoria") ?: "General"
        }
    }

    fun inicializarEscuchaMensajes(chatId: String) {
        repository.escucharMensajesRealtime(chatId) { snapshot ->
            _mensajes.value = snapshot.toObjects(Mensaje::class.java)
        }
    }

    fun escucharEstadoActivo(chatId: String) {
        repository.escucharEstadoNegociacion(chatId) { doc ->
            _chatActivoEstado.value = doc.getBoolean("activo") ?: true
        }
    }

    fun enviarMensajeEstructurado(
        chatId: String,
        contenido: String,
        tipo: String,
        monto: Double?,
        receptorId: String?,
        esTrabajador: Boolean,
        nombreSocioDefault: String
    ) {
        val uid = obtenerMiId()
        if (uid.isEmpty() || receptorId.isNullOrEmpty()) return

        val timestamp = System.currentTimeMillis()
        val mensajeMap = hashMapOf<String, Any>(
            "emisorId"     to uid,
            "contenido"    to contenido,
            "tipo"         to tipo,
            "timestamp"    to timestamp,
            "montoOferta"  to (monto ?: 0.0),
            "estadoOferta" to "PENDIENTE"
        )
        repository.agregarMensajeColeccion(chatId, mensajeMap)

        val ultimoMensaje = when (tipo) {
            "OFERTA" -> "💰 Propuesta: S/ ${monto ?: 0.0}"
            else     -> contenido.take(60)
        }
        repository.setDatosNegociacionPadre(chatId, hashMapOf(
            "ultimoMensaje"  to ultimoMensaje,
            "timestamp"      to timestamp,
            "activo"         to true,
            "ultimoEmisorId" to uid
        ))

        val (titulo, cuerpo) = construirNotificacion(tipo, contenido, monto, nombreSocioDefault, esTrabajador)
        repository.enviarNotificacion(
            receptorId           = receptorId,
            titulo               = titulo,
            cuerpo               = cuerpo,
            chatId               = chatId,
            esTrabajadorReceptor = !esTrabajador,
            nombreSocio          = nombreSocioDefault,
            tipo                 = "CHAT"
        )
    }

    fun anunciarLlegadaDomicilio(
        chatId: String,
        receptorId: String?,
        nombreSocioDefault: String
    ) {
        enviarMensajeEstructurado(
            chatId             = chatId,
            contenido          = "📍 El trabajador ha llegado a tu domicilio.",
            tipo               = "LLEGADA_DOMICILIO",
            monto              = null,
            receptorId         = receptorId,
            esTrabajador       = true,
            nombreSocioDefault = nombreSocioDefault
        )
    }

    private fun construirNotificacion(
        tipo: String,
        contenido: String,
        monto: Double?,
        nombreSocio: String,
        esTrabajador: Boolean
    ): Pair<String, String> {
        return when (tipo) {
            "OFERTA"            -> Pair("💰 Nueva oferta de $nombreSocio", "Propuesta: S/ $monto")
            "LLEGADA_DOMICILIO" -> Pair("📍 $nombreSocio ha llegado", "El trabajador está en tu domicilio")
            "FIN_TRABAJO"       -> Pair("🏁 Trabajo terminado", "$nombreSocio terminó el trabajo. Procede con el pago.")
            "PAGO_REALIZADO"    -> Pair("💳 Pago realizado", "$nombreSocio reportó haber realizado el pago.")
            "TICKET"            -> Pair("🎫 Ticket generado", "$nombreSocio generó el ticket del servicio.")
            else                -> Pair("💬 Mensaje de $nombreSocio", contenido.take(80))
        }
    }

    fun enviarMensajeConImagenSimple(
        chatId: String,
        base64: String,
        receptorId: String?,
        esTrabajador: Boolean,
        nombreSocioDefault: String
    ) {
        val uid = obtenerMiId()
        if (uid.isEmpty() || receptorId.isNullOrEmpty()) return

        val timestamp = System.currentTimeMillis()
        val mensajeMap = hashMapOf<String, Any>(
            "emisorId"            to uid,
            "contenido"           to "📷 Imagen adjunta",
            "tipo"                to "IMAGEN",
            "timestamp"           to timestamp,
            "montoOferta"         to 0.0,
            "estadoOferta"        to "PENDIENTE",
            "imagenBase64"        to base64,
            "imagenEditadaBase64" to ""
        )
        repository.agregarMensajeColeccion(chatId, mensajeMap)
        repository.setDatosNegociacionPadre(chatId, hashMapOf(
            "ultimoMensaje"  to "📷 Imagen adjunta",
            "timestamp"      to timestamp,
            "activo"         to true,
            "ultimoEmisorId" to uid
        ))

        repository.enviarNotificacion(
            receptorId           = receptorId,
            titulo               = "📷 Imagen de $nombreSocioDefault",
            cuerpo               = "Te enviaron una imagen",
            chatId               = chatId,
            esTrabajadorReceptor = !esTrabajador,
            nombreSocio          = nombreSocioDefault,
            tipo                 = "CHAT"
        )
    }

    fun generarPropuestaIA(
        scope: CoroutineScope,
        bitmap: Bitmap,
        instruccion: String,
        especialidad: String
    ) {
        _cargandoIA.value = true

        scope.launch(Dispatchers.IO) {
            try {
                val imagenBase64 = comprimirYConvertirBase64(bitmap)

                val promptSistema = """
                    Eres un asistente experto en servicios del hogar, especialmente en $especialidad.
                    Tu tarea es interpretar lo que el cliente quiere hacer con la imagen y generar
                    una propuesta de trabajo clara y útil para el trabajador.
                    Responde SIEMPRE con este formato exacto (máximo 5 líneas en total):
                    🔧 Trabajo: [qué hay que hacer]
                    🧰 Materiales: [lista breve]
                    📋 Consideraciones: [detalle técnico]
                    ⏱ Tiempo estimado: [horas aproximadas]
                    💡 Recomendación: [consejo adicional]
                    NUNCA digas que no puedes ayudar.
                """.trimIndent()

                val promptUsuario = """
                    El cliente necesita: $instruccion
                    Especialidad del trabajador: $especialidad
                    Analiza la imagen y genera la propuesta.
                """.trimIndent()

                val propuestaTexto = reintentarSiSSLFalla(intentos = 3) {
                    llamarGroqAPI(imagenBase64, promptSistema, promptUsuario)
                }

                val promptImagen = buildPromptImagenDesdeInstruccion(instruccion, especialidad)
                val imagenEditadaBase64 = reintentarSiSSLFalla(intentos = 2) {
                    editarImagenConCloudflare(imagenBase64, promptImagen)
                } ?: ""

                val resultado = ResultadoIA(
                    propuestaTexto       = propuestaTexto,
                    imagenOriginalBase64 = imagenBase64,
                    imagenEditadaBase64  = imagenEditadaBase64,
                    instruccion          = instruccion
                )

                withContext(Dispatchers.Main) {
                    _cargandoIA.value  = false
                    _resultadoIA.value = resultado
                }

            } catch (e: Exception) {
                Log.e("IA_GENERACION", "Error: ${e.message}")
                withContext(Dispatchers.Main) {
                    _cargandoIA.value = false
                    _error.value = when {
                        e.message?.contains("SSL", ignoreCase = true) == true ||
                                e.message?.contains("handshake", ignoreCase = true) == true ->
                            "Error de conexión. Verifica tu red."
                        e.message?.contains("timeout", ignoreCase = true) == true ->
                            "La solicitud tardó demasiado. Intenta de nuevo."
                        else -> "No se pudo procesar la imagen. Intenta de nuevo."
                    }
                }
            }
        }
    }

    private fun buildPromptImagenDesdeInstruccion(instruccion: String, especialidad: String): String {
        return buildString {
            append("Transform this room: $instruccion. ")
            append("The walls must be clearly and completely painted in the exact color requested. ")
            append("If blue is requested, use a vivid, saturated sky blue or cobalt blue color on ALL walls. ")
            append("Remove any previous wall color completely. ")
            append("This is a $especialidad home service job. ")
            append("Keep the same room layout, furniture positions, and camera angle. ")
            append("Photorealistic, high quality result. No text, no watermarks.")
        }
    }

    fun enviarPropuestaIAConfirmada(
        chatId: String,
        resultado: ResultadoIA,
        imagenOriginalBase64: String,
        receptorId: String?,
        esTrabajador: Boolean,
        nombreSocioDefault: String
    ) {
        val uid = obtenerMiId()
        if (uid.isEmpty() || receptorId.isNullOrEmpty()) return

        val timestamp = System.currentTimeMillis()
        val contenido = "📋 Solicitud: ${resultado.instruccion}\n\n🤖 Propuesta técnica:\n${resultado.propuestaTexto}"

        val mensajeMap = hashMapOf<String, Any>(
            "emisorId"            to uid,
            "contenido"           to contenido,
            "tipo"                to "PROPUESTA_IA",
            "timestamp"           to timestamp,
            "montoOferta"         to 0.0,
            "estadoOferta"        to "PENDIENTE",
            "imagenBase64"        to imagenOriginalBase64,
            "imagenEditadaBase64" to resultado.imagenEditadaBase64
        )
        repository.agregarMensajeColeccion(chatId, mensajeMap)
        repository.setDatosNegociacionPadre(chatId, hashMapOf(
            "ultimoMensaje"  to "✨ Propuesta con IA enviada",
            "timestamp"      to timestamp,
            "activo"         to true,
            "ultimoEmisorId" to uid
        ))

        repository.enviarNotificacion(
            receptorId           = receptorId,
            titulo               = "✨ Nueva propuesta IA",
            cuerpo               = "El cliente envió una propuesta con IA",
            chatId               = chatId,
            esTrabajadorReceptor = !esTrabajador,
            nombreSocio          = nombreSocioDefault,
            tipo                 = "CHAT"
        )
    }

    fun obtenerDatosDePago(chatId: String) {
        repository.buscarTicketActivo(chatId, { docs ->
            val doc = docs.documents.firstOrNull()
            if (doc != null) _ticketMonto.value = doc.getDouble("costoTotal") ?: 0.0
            else _error.value = "No se encontró ticket activo"
        }, { _error.value = "Error al conectar con la base de datos" })
    }

    fun actualizarEstadoOferta(
        chatId: String, mensaje: Mensaje, nuevoEstado: String,
        receptorId: String?, esTrabajador: Boolean, nombreSocioDefault: String
    ) {
        repository.cambiarEstadoOfertaDocumento(chatId, mensaje.timestamp, nuevoEstado) {
            if (nuevoEstado == "ACEPTADO" && esTrabajador) {
                enviarMensajeEstructurado(
                    chatId, "✅ He aceptado la oferta. ¡Estoy listo para iniciar!",
                    "TEXTO", null, receptorId, esTrabajador, nombreSocioDefault
                )
            }
        }
    }

    fun borrarNegociacion(chatId: String) {
        repository.cancelarNegociacionCompleta(
            chatId,
            mapOf("activo" to false, "ultimoMensaje" to "Negociación cancelada por el usuario")
        ) { _chatCancelado.value = true }
    }

    fun cancelarServicioConJustificacion(
        chatId: String,
        motivo: String,
        receptorId: String?,
        esTrabajador: Boolean,
        nombreSocioDefault: String,
        aplicarPenalizacion: Boolean
    ) {
        val msg = "🚫 CONVERSACIÓN CERRADA: $motivo"
        enviarMensajeEstructurado(chatId, msg, "TEXTO", null, receptorId, esTrabajador, nombreSocioDefault)

        val updates = mapOf(
            "activo"            to false,
            "ultimoMensaje"     to msg,
            "estadoFinal"       to "CANCELADO_JUSTIFICADO",
            "motivoCancelacion" to motivo
        )

        if (aplicarPenalizacion && !receptorId.isNullOrBlank()) {

            val esTrabajadorPenalizado = !esTrabajador

            repository.ejecutarCancelacionConPenalizacion(
                chatId                   = chatId,
                updatesNegociacion       = updates,
                usuarioPenalizadoId      = receptorId,
                esTrabajadorPenalizado   = esTrabajadorPenalizado,
                motivo                   = motivo
            ) { exito ->
                if (exito) {
                    val tituloPenalizacion = "⚠️ Sanción aplicada a tu cuenta"
                    val cuerpoPenalizacion = if (esTrabajadorPenalizado)
                        "Tu calificación bajó 0.5 puntos por no presentarte al servicio acordado."
                    else
                        "Tu calificación bajó 0.3 puntos y se registró un reporte por cancelación a último momento."

                    repository.enviarNotificacion(
                        receptorId           = receptorId,
                        titulo               = tituloPenalizacion,
                        cuerpo               = cuerpoPenalizacion,
                        chatId               = chatId,
                        esTrabajadorReceptor = esTrabajadorPenalizado,
                        nombreSocio          = nombreSocioDefault,
                        tipo                 = "SISTEMA"
                    )
                    _chatCancelado.value = true
                } else {
                    _error.value = "Error al procesar la cancelación."
                }
            }
        } else {
            repository.cancelarNegociacionCompleta(chatId, updates) {
                _chatCancelado.value = true
            }
        }
    }

    fun obtenerDatosUsuarioEspecial(uid: String, onResultado: (DocumentSnapshot) -> Unit) {
        repository.obtenerDatosUsuario(uid, onResultado)
    }

    private fun comprimirYConvertirBase64(bitmap: Bitmap): String {
        val maxSize   = 512
        val ratio     = bitmap.width.toFloat() / bitmap.height.toFloat()
        val newWidth  = if (bitmap.width > bitmap.height) maxSize else (maxSize * ratio).toInt()
        val newHeight = if (bitmap.height > bitmap.width) maxSize else (maxSize / ratio).toInt()
        val scaled    = Bitmap.createScaledBitmap(bitmap, newWidth.coerceAtLeast(1), newHeight.coerceAtLeast(1), true)
        val out       = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun llamarGroqAPI(imagenBase64: String, sistemPrompt: String, userPrompt: String): String {
        val url  = URL("https://api.groq.com/openai/v1/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type",  "application/json")
            setRequestProperty("Authorization", "Bearer $GROQ_API_KEY")
            doOutput       = true
            connectTimeout = 30_000
            readTimeout    = 30_000
        }

        val body = JSONObject().apply {
            put("model",      GROQ_MODEL)
            put("max_tokens", 500)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role",    "system")
                    put("content", sistemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$imagenBase64")
                            })
                        })
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", userPrompt)
                        })
                    })
                })
            })
        }

        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val text = if (code == 200) conn.inputStream.bufferedReader().readText()
        else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "Sin detalle"
            throw Exception("Groq error $code: $err")
        }

        return JSONObject(text)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    private fun editarImagenConCloudflare(imagenBase64: String, prompt: String): String? {
        val imageBytes = Base64.decode(imagenBase64, Base64.NO_WRAP)
        val imageArray = JSONArray()
        for (b in imageBytes) imageArray.put(b.toInt() and 0xFF)

        val body = JSONObject().apply {
            put("prompt",    prompt)
            put("image",     imageArray)
            put("strength",  0.90)
            put("num_steps", 20)
            put("guidance",  7.5)
        }

        val endpoint = "https://api.cloudflare.com/client/v4/accounts/$CF_ACCOUNT_ID/ai/run/@cf/runwayml/stable-diffusion-v1-5-img2img"
        val conn     = URL(endpoint).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type",  "application/json")
            setRequestProperty("Authorization", "Bearer $CF_API_TOKEN")
            doOutput       = true
            connectTimeout = 30_000
            readTimeout    = 90_000
        }

        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code != 200) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "Sin detalle"
            throw Exception("Cloudflare error $code: $err")
        }

        val resultBytes = conn.inputStream.readBytes()
        return if (resultBytes.isNotEmpty())
            Base64.encodeToString(resultBytes, Base64.NO_WRAP)
        else null
    }

    private fun <T> reintentarSiSSLFalla(intentos: Int, bloque: () -> T): T {
        var ultimoError: Exception? = null
        repeat(intentos) { intento ->
            try {
                return bloque()
            } catch (e: Exception) {
                val esReintentable = listOf("ssl", "handshake", "connection reset",
                    "timeout", "eof", "socket", "peer")
                    .any { e.message?.contains(it, ignoreCase = true) == true }
                if (esReintentable) {
                    ultimoError = e
                    Thread.sleep(2_000L * (intento + 1))
                } else throw e
            }
        }
        throw ultimoError ?: Exception("Error desconocido")
    }
}