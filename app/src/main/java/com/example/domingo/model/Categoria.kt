package com.example.domingo.model

data class Categoria(
    val nombre: String,
    val iconoResId: Int,
    val trabajosRealizados: Int = 0
)