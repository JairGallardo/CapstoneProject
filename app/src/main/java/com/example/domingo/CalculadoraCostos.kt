package com.example.domingo

import kotlin.math.*

object CalculadoraCostos {

    private const val COMISION_FIJA_APP = 5.0

    /**
     * Calcula el desglose total del servicio con tarifas dinámicas por distancia
     */
    fun obtenerPresupuesto(distanciaKm: Double, precioBaseTrabajador: Double): Map<String, Double> {

        val tarifaPorKm = when {
            distanciaKm <= 10 -> 1.50
            distanciaKm <= 30 -> 2.20
            else -> 3.00
        }

        val costoEnvio = distanciaKm * tarifaPorKm

        val bonoZonaAlejada = if (distanciaKm > 30) 15.0 else 0.0

        val totalFinal = precioBaseTrabajador + costoEnvio + bonoZonaAlejada + COMISION_FIJA_APP

        return mapOf(
            "precioTrabajador" to precioBaseTrabajador,
            "costoEnvio" to costoEnvio,
            "bonoAlejado" to bonoZonaAlejada,
            "comisionApp" to COMISION_FIJA_APP,
            "totalFinal" to totalFinal,
            "tarifaAplicada" to tarifaPorKm
        )
    }

    /**
     * Fórmula de Haversine para calcular distancia entre dos coordenadas GPS
     * (Cálculo matemático puro sin costo de API)
     */
    fun calcularDistanciaKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371 // Radio de la Tierra en KM
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}