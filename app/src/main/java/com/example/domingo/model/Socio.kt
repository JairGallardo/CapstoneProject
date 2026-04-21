data class Socio(
    val id: String = "",
    val nombre: String = "",
    val fotoPerfilB64: String = "", // Para no usar Storage
    val rating: Double = 0.0,
    val trabajosRealizados: Int = 0,
    val tarifaSugerida: Double = 0.0,
    val distancia: Double = 0.0,    // La calculamos antes de pasarla al adapter
    val descripcion: String = ""
)