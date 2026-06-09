package com.example.domingo.ui.registro

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.domingo.data.repository.RegistroRepository

class RegistroViewModel : ViewModel() {

    private val repository = RegistroRepository()
    private val _errorNombre = MutableLiveData<String?>()
    val errorNombre: LiveData<String?> get() = _errorNombre

    private val _errorTelefono = MutableLiveData<String?>()
    val errorTelefono: LiveData<String?> get() = _errorTelefono

    private val _estadoToast = MutableLiveData<String>()
    val estadoToast: LiveData<String> get() = _estadoToast

    private val _mostrarAlertaContrasena = MutableLiveData<Boolean>()
    val mostrarAlertaContrasena: LiveData<Boolean> get() = _mostrarAlertaContrasena

    private val _navegacionDestino = MutableLiveData<String>()
    val navegacionDestino: LiveData<String> get() = _navegacionDestino

    fun validarYRegistrar(nombre: String, correo: String, pass: String, telefono: String, rol: String) {
        _errorNombre.value = null
        _errorTelefono.value = null

        if (nombre.isEmpty() || correo.isEmpty() || pass.isEmpty() || telefono.isEmpty()) {
            _estadoToast.value = "Por favor, llena todos los campos"
            return
        }

        if (nombre.length < 3) {
            _errorNombre.value = "Nombre demasiado corto"
            return
        }

        if (telefono.length != 9 || !telefono.startsWith("9")) {
            _errorTelefono.value = "El número debe empezar con 9 y tener 9 dígitos"
            return
        }

        if (!esContrasenaSegura(pass)) {
            _mostrarAlertaContrasena.value = true
            return
        }

        repository.registrarUsuario(nombre, correo, pass, telefono, rol) { exitoso, mensajeError ->
            if (exitoso) {
                _estadoToast.value = "Registro exitoso"
                _navegacionDestino.value = rol
            } else {
                _estadoToast.value = "Error: $mensajeError"
            }
        }
    }

    private fun esContrasenaSegura(pass: String): Boolean {
        val pattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$".toRegex()
        return pattern.matches(pass)
    }
}