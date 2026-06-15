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

    private val _recuperacionExitosa = MutableLiveData<Boolean>()
    val recuperacionExitosa: LiveData<Boolean> get() = _recuperacionExitosa

    private val _cargando = MutableLiveData<Boolean>()
    val cargando: LiveData<Boolean> get() = _cargando

    enum class DestinoPantalla {
        IR_A_VERIFICAR,
        IR_A_MAIN,
        IR_A_REGISTRO
    }

    fun verificarSesionExistente() {
        val userId = repository.obtenerUsuarioActualId()
        if (userId != null) verificarRolYRedirigir(userId)
    }

    fun intentarLogin(correo: String, pass: String) {
        if (correo.isBlank()) {
            _estadoToast.value = "Ingresa tu correo electrónico"
            return
        }
        if (pass.isBlank()) {
            _estadoToast.value = "Ingresa tu contraseña"
            return
        }
        _cargando.value = true
        repository.iniciarSesion(correo, pass) { exitoso, mensajeError ->
            _cargando.postValue(false)
            if (exitoso) {
                val userId = repository.obtenerUsuarioActualId()
                if (userId != null) verificarRolYRedirigir(userId)
            } else {
                _estadoToast.postValue(mensajeError ?: "Error al iniciar sesión")
            }
        }
    }

    fun iniciarSesionConGoogle(idToken: String) {
        _cargando.value = true
        repository.iniciarSesionConGoogle(idToken) { exitoso, error ->
            if (exitoso) {
                val uid = repository.obtenerUsuarioActualId() ?: run {
                    _cargando.postValue(false)
                    _estadoToast.postValue("No se pudo obtener el usuario")
                    return@iniciarSesionConGoogle
                }
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                repository.registrarUsuarioSocial(
                    nombre = user?.displayName ?: "Usuario",
                    correo = user?.email ?: "",
                    uid = uid,
                    proveedor = "google"
                ) { _, _ ->
                    _cargando.postValue(false)
                    verificarRolYRedirigir(uid)
                }
            } else {
                _cargando.postValue(false)
                _estadoToast.postValue(error ?: "Error al iniciar sesión con Google")
            }
        }
    }

    fun enviarRecuperacionContrasena(correo: String) {
        if (correo.isBlank()) {
            _estadoToast.value = "Ingresa tu correo electrónico"
            return
        }
        _cargando.value = true
        repository.enviarCorreoRecuperacion(correo) { exitoso, error ->
            _cargando.postValue(false)
            if (exitoso) {
                _recuperacionExitosa.postValue(true)
            } else {
                _estadoToast.postValue(error ?: "No se pudo enviar el correo")
            }
        }
    }

    fun navegarARegistro() {
        _navegacionDestino.value = DestinoPantalla.IR_A_REGISTRO
    }

    private fun verificarRolYRedirigir(userId: String) {
        repository.obtenerDatosUsuario(userId) { document, error ->
            if (error != null) {
                _estadoToast.postValue("Error al obtener datos: ${error.message}")
                return@obtenerDatosUsuario
            }
            if (document != null && document.exists()) {
                val rol = document.getString("rol")
                val verificado = document.getString("verificado")
                if (rol == "trabajador") {
                    when (verificado) {
                        "no", null -> _navegacionDestino.postValue(DestinoPantalla.IR_A_VERIFICAR)
                        "en_revision" -> {
                            _estadoToast.postValue("Tu perfil sigue en revisión. Por favor ten paciencia.")
                            repository.cerrarSesion()
                        }
                        else -> _navegacionDestino.postValue(DestinoPantalla.IR_A_MAIN)
                    }
                } else {
                    _navegacionDestino.postValue(DestinoPantalla.IR_A_MAIN)
                }
            }
        }
    }
}