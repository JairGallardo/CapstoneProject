package com.example.domingo.ui.notificaciones

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.domingo.data.repository.NotificacionRepository
import com.example.domingo.model.Notificacion
import com.google.firebase.firestore.ListenerRegistration

class NotificacionesViewModel : ViewModel() {

    private val repository = NotificacionRepository()
    private var listener: ListenerRegistration? = null

    private val _notificacionesCompletas = MutableLiveData<List<Notificacion>>()

    private val _filtroActivo = MutableLiveData<String>("Todas")

    private val _notificacionesFiltradas = MediatorLiveData<List<Notificacion>>().apply {
        addSource(_notificacionesCompletas) { aplicarFiltro() }
        addSource(_filtroActivo)            { aplicarFiltro() }
    }
    val notificaciones: LiveData<List<Notificacion>> get() = _notificacionesFiltradas

    private val _estaVacio = MutableLiveData<Boolean>(false)
    val estaVacio: LiveData<Boolean> get() = _estaVacio

    private val _textoVacio = MutableLiveData<String>("Sin notificaciones")
    val textoVacio: LiveData<String> get() = _textoVacio

    private val _errorMensaje = MutableLiveData<String>()
    val errorMensaje: LiveData<String> get() = _errorMensaje

    fun iniciar() {
        listener?.remove()
        listener = repository.escucharNotificaciones { lista ->
            _notificacionesCompletas.value = lista
        }
    }

    fun cambiarFiltro(filtro: String) {
        _filtroActivo.value = filtro
    }

    private fun aplicarFiltro() {
        val lista  = _notificacionesCompletas.value ?: emptyList()
        val filtro = _filtroActivo.value ?: "Todas"

        val filtrada = when (filtro) {
            "No leídas" -> lista.filter { !it.leida }
            "Alertas"   -> lista.filter { it.tipo in listOf("BLOQUEO", "AVISO_RATING") }
            "Sistema"   -> lista.filter { it.tipo == "SISTEMA" }
            else        -> lista
        }

        _notificacionesFiltradas.value = filtrada

        val vacio = filtrada.isEmpty()
        _estaVacio.value = vacio
        _textoVacio.value = when {
            !vacio          -> ""
            filtro == "Todas" -> "Aún no tienes notificaciones"
            else              -> "Sin notificaciones en \"$filtro\""
        }
    }

    fun marcarComoLeida(notifId: String) {
        repository.marcarComoLeida(notifId)
    }

    fun marcarTodasComoLeidas() {
        repository.marcarTodasComoLeidas {
        }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}