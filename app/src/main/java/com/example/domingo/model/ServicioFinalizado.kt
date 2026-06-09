package com.example.domingo.model

data class ServicioFinalizado(
    val categoria: String = "",
    val trabajadorNombre: String = "",
    val clienteNombre: String = "",
    val monto: Double = 0.0,
    val fecha: Long = 0,
    val trabajadorId: String = "",
    val clienteId: String = ""
)