package com.example.domingo.model

data class Socio(
    val id: String = "",
    val nombre: String = "",
    val fotoPerfilB64: String = "",
    val rating: Double = 0.0,
    val trabajosRealizados: Int = 0,
    val tarifaSugerida: Double = 0.0,
    val descripcion: String = "",
    val descripcionesPorCategoria: Map<String, String> = emptyMap(),
    val receptorId: String = "",
    val categorias: List<String> = emptyList(),
    val activo: Boolean = true,
    val ultimoEmisorId: String = ""
)