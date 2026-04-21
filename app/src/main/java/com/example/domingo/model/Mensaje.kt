package com.example.domingo.model

data class Mensaje(
    val emisorId: String = "",
    val contenido: String = "",
    val timestamp: Long = 0,
    val tipo: String = "TEXTO", // "TEXTO" o "OFERTA"
    val montoOferta: Double = 0.0,
    val estadoOferta: String = "PENDIENTE" // "PENDIENTE", "ACEPTADO", "RECHAZADO"
)