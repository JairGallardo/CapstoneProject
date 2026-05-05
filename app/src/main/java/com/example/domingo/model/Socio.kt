package com.example.domingo.model

data class Socio(
    val id: String = "",
    val nombre: String = "",
    val fotoPerfilB64: String = "",
    val rating: Double = 0.0,
    val trabajosRealizados: Int = 0,
    val tarifaSugerida: Double = 0.0,
    val distancia: Double = 0.0,
    val descripcion: String = "",
    val receptorId: String = ""
)