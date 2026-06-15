package com.example.domingo.ui.listadotrabajadores

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.domingo.data.repository.ListadoTrabajadoresRepository
import com.example.domingo.model.Socio
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class ListadoTrabajadoresViewModel : ViewModel() {

    private val repository = ListadoTrabajadoresRepository()
    private val db         = FirebaseFirestore.getInstance()

    private val _categoriaFormateada = MutableLiveData<String>()
    val categoriaFormateada: LiveData<String> get() = _categoriaFormateada

    private val _trabajadores = MutableLiveData<List<Socio>>()
    val trabajadores: LiveData<List<Socio>> get() = _trabajadores

    private val _notificarVacio = MutableLiveData<Boolean>()
    val notificarVacio: LiveData<Boolean> get() = _notificarVacio

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    private val mapaCategoriasOficiales = mapOf(
        "gasfitero"       to "Gasfitero",
        "electricista"    to "Electricista",
        "limpieza"        to "Limpieza",
        "lavanderia"      to "Lavandería",
        "lavandería"      to "Lavandería",
        "mascotas"        to "Mascotas",
        "pintor"          to "Pintor",
        "carpintero"      to "Carpintero",
        "jardineria"      to "Jardinería",
        "jardinería"      to "Jardinería",
        "soporte tecnico" to "Soporte Técnico",
        "soporte técnico" to "Soporte Técnico"
    )

    fun establecerYNormalizarCategoria(categoriaRecibida: String) {
        val claveLimpia      = categoriaRecibida.trim().lowercase(Locale.getDefault())
        val categoriaOficial = mapaCategoriasOficiales[claveLimpia]
            ?: categoriaRecibida.trim().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        _categoriaFormateada.value = categoriaOficial
    }

    fun cargarEspecialistas() {
        val categoria = _categoriaFormateada.value ?: return

        repository.buscarTrabajadoresDisponibles(categoria, { documentos ->
            if (documentos.isEmpty) {
                _notificarVacio.value = true
                _trabajadores.value   = emptyList()
                return@buscarTrabajadoresDisponibles
            }

            val listaSociosProvisional = mutableListOf<Socio>()
            var procesados = 0
            val total      = documentos.size()

            for (doc in documentos) {
                val trabajadorId        = doc.id
                val nombre              = doc.getString("nombre")       ?: "Socio DominGO"
                val fotoB64             = doc.getString("fotoPerfilB64") ?: ""
                val precioBase          = doc.getDouble("precioBase")   ?: 0.0
                val descripcionGeneral  = doc.getString("descripcion")  ?: ""

                @Suppress("UNCHECKED_CAST")
                val descripcionesPorCategoria =
                    doc.get("descripcionesPorCategoria") as? Map<String, String>
                val descEspecializada   = descripcionesPorCategoria?.get(categoria)
                val descripcionFinal    =
                    if (!descEspecializada.isNullOrEmpty()) descEspecializada else descripcionGeneral

                val ratingGlobal = doc.getDouble("rating") ?: 0.0

                db.collection("servicios_finalizados")
                    .whereEqualTo("trabajadorId", trabajadorId)
                    .whereEqualTo("categoria", categoria)
                    .get()
                    .addOnSuccessListener { serviciosSnap ->
                        val trabajosEnCategoria = serviciosSnap.size()

                        val ratingCategoria = if (!serviciosSnap.isEmpty) {
                            val suma = serviciosSnap.documents.sumOf {
                                it.getDouble("rating") ?: 0.0
                            }
                            if (suma > 0.0) suma / serviciosSnap.size()
                            else ratingGlobal
                        } else {
                            if (trabajosEnCategoria == 0) 0.0 else ratingGlobal
                        }

                        listaSociosProvisional.add(
                            Socio(
                                id                 = trabajadorId,
                                nombre             = nombre,
                                fotoPerfilB64      = fotoB64,
                                rating             = ratingCategoria,
                                trabajosRealizados = trabajosEnCategoria,
                                tarifaSugerida     = precioBase,
                                descripcion        = descripcionFinal
                            )
                        )

                        procesados++
                        if (procesados == total) {
                            val listaOrdenada = listaSociosProvisional
                                .sortedWith(
                                    compareByDescending<Socio> { it.trabajosRealizados > 0 }
                                        .thenByDescending { it.rating }
                                )
                            _trabajadores.value = listaOrdenada
                        }
                    }
                    .addOnFailureListener {
                        listaSociosProvisional.add(
                            Socio(
                                id                 = trabajadorId,
                                nombre             = nombre,
                                fotoPerfilB64      = fotoB64,
                                rating             = ratingGlobal,
                                trabajosRealizados = 0,
                                tarifaSugerida     = precioBase,
                                descripcion        = descripcionFinal
                            )
                        )
                        procesados++
                        if (procesados == total) {
                            _trabajadores.value = listaSociosProvisional
                                .sortedByDescending { it.rating }
                        }
                    }
            }
        }, { exception ->
            _error.value = exception.message ?: "Error al conectar con la base de datos"
        })
    }

    fun generarIntentNegociacion(socio: Socio, onResultado: (String?, String?) -> Unit) {
        val uidActual = repository.obtenerUsuarioId()
        if (uidActual == null) {
            onResultado(null, "Error de sesión. Vuelve a iniciar sesión.")
            return
        }

        val categoria = _categoriaFormateada.value ?: "Servicios"

        repository.buscarNegociacionActiva(uidActual, socio.id, categoria) { chatIdActivo ->
            if (chatIdActivo != null) {
                onResultado(chatIdActivo, null)
            } else {
                val nuevoChatId = repository.generarNuevoChatId()
                repository.crearNegociacionInicial(
                    chatId      = nuevoChatId,
                    clienteId   = uidActual,
                    trabajadorId = socio.id,
                    categoria   = categoria
                )
                onResultado(nuevoChatId, null)
            }
        }
    }
}