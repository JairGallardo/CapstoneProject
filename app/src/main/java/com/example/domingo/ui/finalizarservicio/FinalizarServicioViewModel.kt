package com.example.domingo.ui.finalizarservicio

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.domingo.data.repository.FinalizarServicioRepository

class FinalizarServicioViewModel : ViewModel() {

    private val repository = FinalizarServicioRepository()

    private val _operacionExitosa = MutableLiveData<Boolean>()
    val operacionExitosa: LiveData<Boolean> get() = _operacionExitosa

    private val _mensajeError = MutableLiveData<String>()
    val mensajeError: LiveData<String> get() = _mensajeError

    fun guardarFeedbackYFinalizar(
        chatId: String?,
        trabajadorId: String?,
        clienteIdChat: String?,
        miRol: String,
        nombreOtraPersona: String,
        categoriaServicio: String,
        montoFinal: Double,
        nota: Float,
        comentario: String
    ) {
        if (trabajadorId == null || clienteIdChat == null || chatId == null) {
            _mensajeError.value = "Error: Faltan datos para finalizar (IDs nulos)."
            return
        }

        val miId = repository.obtenerUsuarioId() ?: return
        val miNombre = repository.obtenerNombreUsuario() ?: "Usuario"

        val idReceptorResena = if (miRol == "cliente") trabajadorId else clienteIdChat

        val servicioFinalizado = hashMapOf<String, Any?>(
            "participantes" to listOf(clienteIdChat, trabajadorId),
            "clienteId" to clienteIdChat,
            "trabajadorId" to trabajadorId,
            "clienteNombre" to if (miRol == "cliente") miNombre else nombreOtraPersona,
            "trabajadorNombre" to if (miRol == "trabajador") miNombre else nombreOtraPersona,
            "categoria" to categoriaServicio,
            "monto" to montoFinal,
            "fecha" to System.currentTimeMillis(),
            "rating" to nota
        )

        repository.registrarServicioFinalizado(servicioFinalizado,
            onSuccess = {
                val feedback = hashMapOf<String, Any?>(
                    "emisorId" to miId,
                    "emisorNombre" to miNombre,
                    "puntos" to nota,
                    "comentario" to comentario,
                    "categoria" to categoriaServicio,
                    "timestamp" to System.currentTimeMillis()
                )

                repository.guardarResenaUsuario(idReceptorResena, feedback,
                    onSuccess = {
                        repository.actualizarMetricasUsuario(idReceptorResena, categoriaServicio, nota,
                            onSuccess = {
                                val textoCierre = "Servicio finalizado: S/ $montoFinal"
                                repository.desactivarNegociacionPorId(chatId, textoCierre) { exito ->
                                    if (exito) {
                                        _operacionExitosa.value = true
                                    } else {
                                        _mensajeError.value = "Historial guardado, pero falló remover el chat activo."
                                    }
                                }
                            },
                            onFailure = { e -> _mensajeError.value = "Error al actualizar métricas: ${e.message}" }
                        )
                    },
                    onFailure = { e -> _mensajeError.value = "Error al guardar reseña: ${e.message}" }
                )
            },
            onFailure = { e -> _mensajeError.value = "Error al registrar el historial: ${e.message}" }
        )
    }
}