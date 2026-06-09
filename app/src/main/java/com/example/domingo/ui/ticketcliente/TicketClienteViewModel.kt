package com.example.domingo.ui.ticketcliente

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.domingo.data.repository.TicketRepository
import com.google.firebase.firestore.DocumentSnapshot

class TicketClienteViewModel : ViewModel() {

    private val repository = TicketRepository()
    private val _datosCliente = MutableLiveData<DocumentSnapshot>()
    val datosCliente: LiveData<DocumentSnapshot> get() = _datosCliente
    private val _operacionExitosa = MutableLiveData<Boolean>()
    val operacionExitosa: LiveData<Boolean> get() = _operacionExitosa
    private val _mensajeError = MutableLiveData<String>()
    val mensajeError: LiveData<String> get() = _mensajeError

    fun cargarDatosCliente() {
        repository.obtenerDatosCliente(
            onSuccess = { document -> _datosCliente.value = document },
            onFailure = { e -> _mensajeError.value = "Error al cargar datos: ${e.message}" }
        )
    }

    fun procesarTicket(
        chatId: String?,
        trabajadorId: String?,
        montoServicio: Double,
        direccion: String,
        telefono: String,
        referencias: String,
        email: String
    ) {
        if (direccion.isEmpty() || telefono.isEmpty()) {
            _mensajeError.value = "Completa dirección y teléfono"
            return
        }

        val clienteId = repository.obtenerUsuarioId() ?: repository.obtenerUsuarioId() ?: return

        val ticketData = hashMapOf<String, Any?>(
            "chatId" to chatId,
            "clienteId" to clienteId,
            "trabajadorId" to trabajadorId,
            "montoServicio" to montoServicio,
            "costoTotal" to montoServicio,
            "direccion" to direccion,
            "telefono" to telefono,
            "email" to email,
            "referencias" to referencias,
            "metodoPago" to "PENDIENTE_AL_FINAL",
            "estado" to "TRABAJO_EN_CURSO",
            "timestamp" to System.currentTimeMillis()
        )

        repository.guardarTicket(ticketData,
            onSuccess = { ticketId ->
                prepararYEnviarChat(chatId, clienteId, ticketId, direccion, telefono, referencias, montoServicio)
            },
            onFailure = { e -> _mensajeError.value = "Error al crear ticket: ${e.message}" }
        )
    }

    private fun prepararYEnviarChat(
        chatId: String?,
        clienteId: String,
        ticketId: String,
        direccion: String,
        telefono: String,
        referencias: String,
        costoTotal: Double
    ) {
        if (chatId == null) return

        val mensajeTicket = """
            ╔══════════════════════╗
               TICKET DE SERVICIO
            ╚══════════════════════╝
            
            RESUMEN DEL PAGO
            --------------------------------------
            Total a pagar: S/ ${String.format("%.2f", costoTotal)}
            
            DETALLES DE ENTREGA
            --------------------------------------
            Dirección: $direccion
            Teléfono: $telefono
            Ref: $referencias
            
            --------------------------------------
            ID: $ticketId
            ¡El servicio ha comenzado!
        """.trimIndent()

        val mensajeData = hashMapOf<String, Any?>(
            "emisorId" to clienteId,
            "contenido" to mensajeTicket,
            "tipo" to "TICKET",
            "timestamp" to System.currentTimeMillis(),
            "ticketId" to ticketId,
            "montoOferta" to costoTotal
        )

        repository.enviarMensajeChat(chatId, mensajeData,
            onSuccess = { _operacionExitosa.value = true },
            onFailure = { e -> _mensajeError.value = "Ticket guardado, pero falló envío al chat: ${e.message}" }
        )
    }
}