package com.example.domingo.model

data class Notificacion(
    val id: String = "",
    val titulo: String = "",
    val cuerpo: String = "",
    val tipo: String = "SISTEMA",
    val timestamp: Long = 0L,
    val leida: Boolean = false,
    val accionUrl: String = ""
)