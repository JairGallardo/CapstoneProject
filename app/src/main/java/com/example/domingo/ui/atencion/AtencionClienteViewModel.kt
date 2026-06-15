package com.example.domingo.ui.atencion

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.domingo.data.repository.NotificacionRepository
import com.google.firebase.auth.FirebaseAuth

class AtencionClienteViewModel : ViewModel() {

    private val repository = NotificacionRepository()
    private val auth       = FirebaseAuth.getInstance()

    private val _enviando = MutableLiveData<Boolean>(false)
    val enviando: LiveData<Boolean> get() = _enviando

    private val _exitoEnvio = MutableLiveData<Boolean>()
    val exitoEnvio: LiveData<Boolean> get() = _exitoEnvio

    private val _errorMensaje = MutableLiveData<String>()
    val errorMensaje: LiveData<String> get() = _errorMensaje

    private val _mostrarAvisoApelacion = MutableLiveData<Boolean>(false)
    val mostrarAvisoApelacion: LiveData<Boolean> get() = _mostrarAvisoApelacion

    private val _mostrarCampoTicket = MutableLiveData<Boolean>(false)
    val mostrarCampoTicket: LiveData<Boolean> get() = _mostrarCampoTicket

    fun onMotivoSeleccionado(motivoId: Int) {
        _mostrarAvisoApelacion.value  = motivoId == MOTIVO_PENALIZACION
        _mostrarCampoTicket.value     = motivoId == MOTIVO_PENALIZACION || motivoId == MOTIVO_CONFLICTO
    }

    fun enviarSolicitud(
        motivoTexto: String,
        descripcion: String,
        idTicketRelacionado: String,
        fotosB64: List<String> = emptyList()
    ) {
        if (motivoTexto.isBlank()) {
            _errorMensaje.value = "Selecciona un motivo para continuar"
            return
        }
        if (descripcion.isBlank()) {
            _errorMensaje.value = "Describe tu situación antes de enviar"
            return
        }

        val uid = auth.currentUser?.uid
        if (uid == null) {
            _errorMensaje.value = "Error de sesión. Vuelve a iniciar sesión."
            return
        }

        _enviando.value = true

        repository.enviarTicketSoporte(
            uid                 = uid,
            motivo              = motivoTexto,
            descripcion         = descripcion.trim(),
            idTicketRelacionado = idTicketRelacionado.trim(),
            fotosB64            = fotosB64,
            onExito = {
                _enviando.value  = false
                _exitoEnvio.value = true
            },
            onError = { e ->
                _enviando.value   = false
                _errorMensaje.value = "No se pudo enviar: ${e.localizedMessage}"
            }
        )
    }

    companion object {
        const val MOTIVO_PENALIZACION = 1
        const val MOTIVO_CONFLICTO    = 2
    }
}