package com.example.domingo

import kotlin.math.*

object CalculadoraCostos {

    // Tarifas base (puedes ajustarlas aquí y afectará a toda la app)
    private const val COMISION_FIJA_APP = 5.0

    /**
     * Calcula el desglose total del servicio con tarifas dinámicas por distancia
     */
    fun obtenerPresupuesto(distanciaKm: Double, precioBaseTrabajador: Double): Map<String, Double> {

        // 1. Lógica de costo variable según la distancia (Tarifario dinámico)
        val tarifaPorKm = when {
            distanciaKm <= 10 -> 1.50   // Zona urbana cercana
            distanciaKm <= 30 -> 2.20   // Trayectos interurbanos
            else -> 3.00                // Zonas rurales o muy alejadas
        }

        val costoEnvio = distanciaKm * tarifaPorKm

        // 2. Bono de Viáticos: Si es más de 30km, se añade un extra por el tiempo de viaje
        val bonoZonaAlejada = if (distanciaKm > 30) 15.0 else 0.0

        // 3. Cálculo del Total Final
        val totalFinal = precioBaseTrabajador + costoEnvio + bonoZonaAlejada + COMISION_FIJA_APP

        // Retornamos un mapa con todos los detalles para que la UI pueda mostrarlos por separado
        return mapOf(
            "precioTrabajador" to precioBaseTrabajador,
            "costoEnvio" to costoEnvio,
            "bonoAlejado" to bonoZonaAlejada,
            "comisionApp" to COMISION_FIJA_APP,
            "totalFinal" to totalFinal,
            "tarifaAplicada" to tarifaPorKm // Opcional: para mostrar cuánto se cobra por km
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