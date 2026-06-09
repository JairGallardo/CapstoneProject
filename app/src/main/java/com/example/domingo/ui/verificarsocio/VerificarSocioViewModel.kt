package com.example.domingo.ui.verificarsocio

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.domingo.data.repository.VerificarSocioRepository
import com.example.domingo.util.ImageUtils

class VerificarSocioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VerificarSocioRepository()
    private val contexto = application.applicationContext
    private val _estadoCarga = MutableLiveData<EstadoCarga>()
    val estadoCarga: LiveData<EstadoCarga> get() = _estadoCarga
    private val _formularioValido = MutableLiveData<Boolean>()
    val formularioValido: LiveData<Boolean> get() = _formularioValido
    private val _procesoExitoso = MutableLiveData<Boolean>()
    val procesoExitoso: LiveData<Boolean> get() = _procesoExitoso
    sealed class EstadoCarga {
        object Inicial : EstadoCarga()
        object Cargando : EstadoCarga()
        object Error : EstadoCarga()
    }

    fun evaluarFormulario(perfil: Uri?, frontal: Uri?, trasero: Uri?, checksAceptados: Boolean) {
        val fotosListas = perfil != null && frontal != null && trasero != null
        _formularioValido.value = fotosListas && checksAceptados
    }

    fun procesarYEnviar(perfilUri: Uri?, frontalUri: Uri?, traseroUri: Uri?) {
        val uid = repository.obtenerUsuarioId() ?: return

        _estadoCarga.value = EstadoCarga.Cargando

        Thread {
            val perfilB64 = perfilUri?.let { ImageUtils.convertirUriABase64(contexto, it) }
            val frontalB64 = frontalUri?.let { ImageUtils.convertirUriABase64(contexto, it) }
            val traseroB64 = traseroUri?.let { ImageUtils.convertirUriABase64(contexto, it) }

            if (perfilB64 != null && frontalB64 != null && traseroB64 != null) {
                repository.enviarDocumentosRevision(uid, perfilB64, frontalB64, traseroB64) { completado ->
                    if (completado) {
                        repository.cerrarSesion()
                        _procesoExitoso.postValue(true)
                    } else {
                        _estadoCarga.postValue(EstadoCarga.Error)
                    }
                }
            } else {
                _estadoCarga.postValue(EstadoCarga.Error)
            }
        }.start()
    }
}