package com.example.domingo.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.domingo.data.repository.LoginRepository

class LoginViewModel : ViewModel() {

    private val repository = LoginRepository()
    private val _estadoToast = MutableLiveData<String>()
    val estadoToast: LiveData<String> get() = _estadoToast

    private val _navegacionDestino = MutableLiveData<DestinoPantalla>()
    val navegacionDestino: LiveData<DestinoPantalla> get() = _navegacionDestino

    enum class DestinoPantalla {
        IR_A_VERIFICAR,
        IR_A_MAIN,
        IR_A_REGISTRO
    }

    fun verificarSesionExistente() {
        val userId = repository.obtenerUsuarioActualId()
        if (userId != null) {
            verificarRolYRedirigir(userId)
        }
    }

    fun intentarLogin(correo: String, pass: String) {
        if (correo.isNotEmpty() && pass.isNotEmpty()) {
            repository.iniciarSesion(correo, pass) { exitoso, mensajeError ->
                if (exitoso) {
                    val userId = repository.obtenerUsuarioActualId()
                    if (userId != null) verificarRolYRedirigir(userId)
                } else {
                    _estadoToast.value = "Error: $mensajeError"
                }
            }
        }
    }

    fun navegarARegistro() {
        _navegacionDestino.value = DestinoPantalla.IR_A_REGISTRO
    }

    private fun verificarRolYRedirigir(userId: String) {
        repository.obtenerDatosUsuario(userId) { document, error ->
            if (error != null) {
                _estadoToast.value = "Error al obtener datos: ${error.message}"
                return@obtenerDatosUsuario
            }

            if (document != null && document.exists()) {
                val rol = document.getString("rol")
                val verificado = document.getString("verificado")

                if (rol == "trabajador") {
                    when (verificado) {
                        "no", null -> _navegacionDestino.value = DestinoPantalla.IR_A_VERIFICAR
                        "en_revision" -> {
                            _estadoToast.value = "Tu perfil sigue en revisión. Ten paciencia."
                            repository.cerrarSesion()
                        }
                        else -> _navegacionDestino.value = DestinoPantalla.IR_A_MAIN
                    }
                } else {
                    _navegacionDestino.value = DestinoPantalla.IR_A_MAIN
                }
            }
        }
    }
}