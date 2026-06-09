package com.example.domingo.model
data class Mensaje(
    val emisorId: String = "",
    val contenido: String = "",
    val timestamp: Long = 0,
    val tipo: String = "TEXTO",
    val montoOferta: Double = 0.0,
    val estadoOferta: String = "PENDIENTE",
    val imagenBase64: String = "",
    val imagenEditadaBase64: String = ""
)