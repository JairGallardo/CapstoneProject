package com.example.domingo.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class TicketRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun obtenerUsuarioId(): String? = auth.currentUser?.uid

    fun obtenerDatosCliente(onSuccess: (DocumentSnapshot) -> Unit, onFailure: (Exception) -> Unit) {
        val uid = obtenerUsuarioId() ?: return
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { onSuccess(it) }
            .addOnFailureListener { onFailure(it) }
    }

    fun guardarTicket(ticketData: HashMap<String, Any?>, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("tickets").add(ticketData)
            .addOnSuccessListener { ticketDoc -> onSuccess(ticketDoc.id) }
            .addOnFailureListener { onFailure(it) }
    }

    fun enviarMensajeChat(chatId: String, mensajeData: HashMap<String, Any?>, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("negociaciones").document(chatId).collection("mensajes").add(mensajeData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }
}