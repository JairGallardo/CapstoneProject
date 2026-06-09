package com.example.domingo.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.domingo.data.repository.MainRepository
import com.example.domingo.model.ServicioFinalizado
import com.example.domingo.model.Socio
import com.google.firebase.firestore.ListenerRegistration
import com.example.domingo.R

class MainViewModel : ViewModel() {

    private val repository = MainRepository()
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

    private val _filtroSeleccionado = MutableLiveData<String>("Todos")

    private val _bandejaSociosFiltrada = MediatorLiveData<List<Socio>>().apply {
        addSource(_bandejaSociosCompleta) { updateFilteredList() }
        addSource(_filtroSeleccionado)   { updateFilteredList() }
    }
    val bandejaSocios: LiveData<List<Socio>> get() = _bandejaSociosFiltrada

    fun actualizarFiltro(nuevoFiltro: String) {
        _filtroSeleccionado.value = nuevoFiltro
    }

    private fun updateFilteredList() {
        val lista  = _bandejaSociosCompleta.value ?: emptyList()
        val filtro = _filtroSeleccionado.value    ?: "Todos"
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
            _esTrabajador.value = esTrab

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
            val listaTemporal = mutableListOf<Socio>()

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
                        _bandejaSociosCompleta.value = listaTemporal
                    }
                    return@forEach
                }

                val receptorId  = if (esTrabajador) doc.getString("clienteId")   ?: ""
                else               doc.getString("trabajadorId") ?: ""
                val ultimoMsg   = doc.getString("ultimoMensaje") ?: "Nuevo mensaje"

                repository.obtenerDatosUsuario(receptorId) { userRef ->
                    listaTemporal.add(
                        Socio(
                            id              = doc.id,
                            nombre          = userRef.getString("nombre")       ?: "Usuario",
                            descripcion     = ultimoMsg,
                            receptorId      = receptorId,
                            fotoPerfilB64   = userRef.getString("fotoPerfilB64") ?: "",
                            activo          = doc.getBoolean("activo") ?: true,
                            ultimoEmisorId  = doc.getString("ultimoEmisorId") ?: ""
                        )
                    )
                    contadorProcesados++
                    if (contadorProcesados == totalDocumentos) {
                        _bandejaSociosCompleta.value = listaTemporal
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
}