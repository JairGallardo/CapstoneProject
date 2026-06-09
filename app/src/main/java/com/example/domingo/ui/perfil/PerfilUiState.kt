package com.example.domingo.ui.perfil

import com.google.firebase.firestore.DocumentSnapshot

sealed class PerfilUiState {
    object Loading : PerfilUiState()
    data class SuccessCargarPerfil(val document: DocumentSnapshot) : PerfilUiState()
    data class SuccessCargarResenas(val resenas: List<Map<String, Any>>) : PerfilUiState()
    data class SuccessOperacion(val mensaje: String) : PerfilUiState()
    data class Error(val mensaje: String) : PerfilUiState()

}