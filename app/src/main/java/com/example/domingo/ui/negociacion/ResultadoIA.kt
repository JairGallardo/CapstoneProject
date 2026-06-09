package com.example.domingo.ui.negociacion
data class ResultadoIA(
    val propuestaTexto: String,
    val imagenOriginalBase64: String,
    val imagenEditadaBase64: String,
    val instruccion: String
)