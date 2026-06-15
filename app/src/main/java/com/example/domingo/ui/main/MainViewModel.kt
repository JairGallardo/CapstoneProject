package com.example.domingo.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.domingo.data.repository.MainRepository
import com.example.domingo.model.ServicioFinalizado
import com.example.domingo.model.Socio
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.domingo.R

class MainViewModel : ViewModel() {

    private val repository = MainRepository()
    private val db         = FirebaseFirestore.getInstance()

    private var historialListener: ListenerRegistration? = null
    private var bandejaListener:   ListenerRegistration? = null

    private val _esTrabajador = MutableLiveData<Boolean>()
    val esTrabajador: LiveData<Boolean> get() = _esTrabajador

    private val _disponible = MutableLiveData<Boolean>()
    val disponible: LiveData<Boolean> get() = _disponible

    private val _historial = MutableLiveData<List<ServicioFinalizado>>()
    val historial: LiveData<List<ServicioFinalizado>> get() = _historial

    private val _errorMensaje = MutableLiveData<String>()
    val errorMensaje: LiveData<String> get() = _errorMensaje

    private val _imagenesCarrusel = MutableLiveData<List<Int>>()
    val imagenesCarrusel: LiveData<List<Int>> get() = _imagenesCarrusel

    private val _conteoPorCategoria = MutableLiveData<Map<String, Int>>(emptyMap())
    val conteoPorCategoria: LiveData<Map<String, Int>> get() = _conteoPorCategoria

    private val _bandejaSociosCompleta = MutableLiveData<List<Socio>>()

    private val _filtroSeleccionado = MutableLiveData<String>("Mensajes")

    private val _bandejaSociosFiltrada = MediatorLiveData<List<Socio>>().apply {
        addSource(_bandejaSociosCompleta) { updateFilteredList() }
        addSource(_filtroSeleccionado)   { updateFilteredList() }
    }
    val bandejaSocios: LiveData<List<Socio>> get() = _bandejaSociosFiltrada

    private val _nombreUsuario = MutableLiveData<String>()
    val nombreUsuario: LiveData<String> get() = _nombreUsuario

    fun actualizarFiltro(nuevoFiltro: String) {
        _filtroSeleccionado.value = nuevoFiltro
    }

    private fun updateFilteredList() {
        val lista  = _bandejaSociosCompleta.value ?: emptyList()
        val filtro = _filtroSeleccionado.value    ?: "Mensajes"
        _bandejaSociosFiltrada.value = when (filtro) {
            "Mensajes"    -> lista.filter  { it.activo  }
            "Finalizados" -> lista.filter  { !it.activo }
            else          -> lista
        }
    }

    fun inicializarDatos() {
        val uid = repository.obtenerUsuarioId() ?: return

        repository.obtenerDatosUsuario(uid) { doc ->
            val esTrab = doc.getString("rol") == "trabajador"
            _esTrabajador.value  = esTrab
            _nombreUsuario.value = doc.getString("nombre") ?: ""

            if (esTrab) {
                _disponible.value = doc.getBoolean("disponible") ?: false
                cargarConteoPorCategoria(uid)
            }

            cargarHistorialRealTime(uid)
            conmutarBandejaEntrada(true)
        }

        cargarBannersCarrusel()
    }

    private fun cargarBannersCarrusel() {
        _imagenesCarrusel.value = listOf(
            R.drawable.gasfiteria,
            R.drawable.electricista,
            R.drawable.limpieza,
            R.drawable.lavanderia
        )
    }

    private fun cargarHistorialRealTime(uid: String) {
        historialListener?.remove()
        historialListener = repository.escucharHistorialServicios(uid) { lista ->
            _historial.value = lista
        }
    }

    fun cargarConteoPorCategoria(uid: String) {
        repository.obtenerConteoPorCategoria(uid) { mapa ->
            _conteoPorCategoria.value = mapa
        }
    }

    fun conmutarBandejaEntrada(mostrarBandeja: Boolean) {
        if (!mostrarBandeja) {
            bandejaListener?.remove()
            return
        }

        val uid          = repository.obtenerUsuarioId() ?: return
        val esTrabajador = _esTrabajador.value ?: false

        bandejaListener?.remove()
        bandejaListener = repository.escucharBandejaEntrada(uid, esTrabajador) { documentos ->
            val listaTemporal   = mutableListOf<Socio>()

            if (documentos.isEmpty()) {
                _bandejaSociosCompleta.value = listaTemporal
                return@escucharBandejaEntrada
            }

            var contadorProcesados = 0
            val totalDocumentos   = documentos.size

            documentos.forEach { doc ->
                val campoOculto = if (esTrabajador) "ocultoParaTrabajador" else "ocultoParaCliente"
                if (doc.getBoolean(campoOculto) == true) {
                    contadorProcesados++
                    if (contadorProcesados == totalDocumentos) {
                        _bandejaSociosCompleta.value = listaTemporal.toList()
                    }
                    return@forEach
                }

                val receptorId = if (esTrabajador)
                    doc.getString("clienteId")   ?: ""
                else
                    doc.getString("trabajadorId") ?: ""

                val ultimoMsg    = doc.getString("ultimoMensaje") ?: "Nuevo mensaje"
                val categoriaRaw = doc.getString("categoria") ?: ""
                val categoriaDoc = normalizarCategoria(categoriaRaw)
                val estaActivo   = doc.getBoolean("activo")   ?: true

                repository.obtenerDatosUsuario(receptorId) { userRef ->
                    val nombreReceptor = userRef.getString("nombre")        ?: "Usuario"
                    val fotoReceptor   = userRef.getString("fotoPerfilB64") ?: ""

                    if (!esTrabajador && categoriaDoc.isNotEmpty() && receptorId.isNotEmpty()) {
                        db.collection("servicios_finalizados")
                            .whereEqualTo("trabajadorId", receptorId)
                            .whereEqualTo("categoria", categoriaDoc)
                            .get()
                            .addOnSuccessListener { serviciosSnap ->
                                val trabajosCategoria = serviciosSnap.size()

                                val ratingCategoria = if (!serviciosSnap.isEmpty) {
                                    val suma = serviciosSnap.documents.sumOf {
                                        it.getDouble("rating") ?: 0.0
                                    }
                                    if (suma > 0.0) suma / serviciosSnap.size()
                                    else userRef.getDouble("rating") ?: 0.0
                                } else {
                                    if (trabajosCategoria == 0) 0.0
                                    else userRef.getDouble("rating") ?: 0.0
                                }

                                listaTemporal.add(
                                    Socio(
                                        id                 = doc.id,
                                        nombre             = nombreReceptor,
                                        descripcion        = ultimoMsg,
                                        receptorId         = receptorId,
                                        fotoPerfilB64      = fotoReceptor,
                                        activo             = estaActivo,
                                        ultimoEmisorId     = doc.getString("ultimoEmisorId") ?: "",
                                        rating             = ratingCategoria,
                                        trabajosRealizados = trabajosCategoria
                                    )
                                )
                                contadorProcesados++
                                if (contadorProcesados == totalDocumentos) {
                                    _bandejaSociosCompleta.value = listaTemporal.toList()
                                }
                            }
                            .addOnFailureListener {
                                listaTemporal.add(
                                    Socio(
                                        id             = doc.id,
                                        nombre         = nombreReceptor,
                                        descripcion    = ultimoMsg,
                                        receptorId     = receptorId,
                                        fotoPerfilB64  = fotoReceptor,
                                        activo         = estaActivo,
                                        ultimoEmisorId = doc.getString("ultimoEmisorId") ?: "",
                                        rating         = userRef.getDouble("rating") ?: 0.0,
                                        trabajosRealizados = 0
                                    )
                                )
                                contadorProcesados++
                                if (contadorProcesados == totalDocumentos) {
                                    _bandejaSociosCompleta.value = listaTemporal.toList()
                                }
                            }
                    } else {
                        listaTemporal.add(
                            Socio(
                                id             = doc.id,
                                nombre         = nombreReceptor,
                                descripcion    = ultimoMsg,
                                receptorId     = receptorId,
                                fotoPerfilB64  = fotoReceptor,
                                activo         = estaActivo,
                                ultimoEmisorId = doc.getString("ultimoEmisorId") ?: ""
                            )
                        )
                        contadorProcesados++
                        if (contadorProcesados == totalDocumentos) {
                            _bandejaSociosCompleta.value = listaTemporal.toList()
                        }
                    }
                }
            }
        }
    }

    fun eliminarConversacionCompleta(chatId: String) {
        val esTrab = _esTrabajador.value ?: false
        repository.ocultarChatYNotificar(chatId, esTrab) { exitoso ->
            if (!exitoso) {
                _errorMensaje.value = "Hubo un error al intentar eliminar la conversación."
            }
        }
    }

    fun cambiarDisponibilidad(
        disponible: Boolean,
        latitud: Double?  = null,
        longitud: Double? = null
    ) {
        val uid = repository.obtenerUsuarioId() ?: return
        repository.actualizarEstadoDisponibilidad(uid, disponible, latitud, longitud) { exitoso ->
            if (exitoso) _disponible.value = disponible
            else         _errorMensaje.value = "Error al actualizar estado"
        }
    }

    override fun onCleared() {
        super.onCleared()
        historialListener?.remove()
        bandejaListener?.remove()
    }
    private fun normalizarCategoria(raw: String): String {
        val mapaOficial = mapOf(
            "gasfitero"       to "Gasfitero",
            "electricista"    to "Electricista",
            "limpieza"        to "Limpieza",
            "lavanderia"      to "Lavandería",
            "lavandería"      to "Lavandería",
            "mascotas"        to "Mascotas",
            "pintor"          to "Pintor",
            "carpintero"      to "Carpintero",
            "jardineria"      to "Jardinería",
            "jardinería"      to "Jardinería",
            "soporte tecnico" to "Soporte Técnico",
            "soporte técnico" to "Soporte Técnico"
        )
        val clave = raw.trim().lowercase(java.util.Locale.getDefault())
        return mapaOficial[clave] ?: raw.trim()
            .replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault())
                else it.toString()
            }
    }
}