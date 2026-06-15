package com.example.domingo.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FinalizarServicioRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun obtenerUsuarioId(): String? = auth.currentUser?.uid
    fun obtenerNombreUsuario(): String? = auth.currentUser?.displayName

    fun registrarServicioFinalizado(
        servicioData: Map<String, Any?>,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("servicios_finalizados").add(servicioData)
            .addOnSuccessListener { docRef -> onSuccess(docRef.id) }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun guardarResenaUsuario(
        usuarioReceptorId: String,
        feedbackData: Map<String, Any?>,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("usuarios").document(usuarioReceptorId)
            .collection("resenas").add(feedbackData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun actualizarMetricasUsuario(
        usuarioReceptorId: String,
        categoria: String,
        nota: Float,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val usuarioRef = db.collection("usuarios").document(usuarioReceptorId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(usuarioRef)

            val trabajosActuales = snapshot.getLong("trabajosRealizados") ?: 0L
            val ratingActual = snapshot.getDouble("rating") ?: 0.0
            val nuevosTrabajos = trabajosActuales + 1
            val nuevoRating = ((ratingActual * trabajosActuales) + nota) / nuevosTrabajos

            transaction.update(usuarioRef, "trabajosRealizados", nuevosTrabajos)
            transaction.update(usuarioRef, "rating", nuevoRating)

            @Suppress("UNCHECKED_CAST")
            val statsPorCategoria = snapshot.get("statsPorCategoria") as? Map<String, Map<String, Any>> ?: emptyMap()
            val statsActuales = statsPorCategoria[categoria]
            val trabajosCat = (statsActuales?.get("trabajos") as? Long) ?: 0L
            val ratingCat = (statsActuales?.get("rating") as? Double) ?: 0.0

            val nuevosTrabajosCat = trabajosCat + 1
            val nuevoRatingCat = ((ratingCat * trabajosCat) + nota) / nuevosTrabajosCat

            val nuevoStatsCategoria = mapOf(
                "rating" to nuevoRatingCat,
                "trabajos" to nuevosTrabajosCat
            )

            val nuevoMapaCompleto = statsPorCategoria.toMutableMap()
            nuevoMapaCompleto[categoria] = nuevoStatsCategoria

            transaction.update(usuarioRef, "statsPorCategoria", nuevoMapaCompleto)
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun desactivarNegociacionPorId(chatId: String, ultimoMsg: String, completado: (Boolean) -> Unit) {
        val actualizaciones = mapOf(
            "activo" to false,
            "ultimoMensaje" to ultimoMsg,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("negociaciones")
            .document(chatId)
            .update(actualizaciones)
            .addOnSuccessListener { completado(true) }
            .addOnFailureListener { completado(false) }
    }
}