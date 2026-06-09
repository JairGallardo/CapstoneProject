package com.example.domingo.ui.perfil

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.domingo.data.repository.PerfilRepository
import com.example.domingo.model.FotoTrabajo

class PerfilViewModel : ViewModel() {

    private val repository = PerfilRepository()

    private val _uiState = MutableLiveData<PerfilUiState>()
    val uiState: LiveData<PerfilUiState> get() = _uiState
    val misCategorias = mutableListOf<String>()
    val descripcionesMap = mutableMapOf<String, String>()

    var qrYapeB64: String? = null
    var qrPlinB64: String? = null
    var userRol: String? = null

    fun obtenerUidActual(): String? = repository.obtenerUsuarioId()

    fun cerrarSesion() {
        repository.cerrarSesion()
    }

    fun cargarPerfil(uid: String) {
        _uiState.value = PerfilUiState.Loading
        repository.cargarDatosUsuario(uid,
            onSuccess = { doc ->
                userRol = doc.getString("rol")
                qrYapeB64 = doc.getString("qrYapeB64")
                qrPlinB64 = doc.getString("qrPlinB64")

                (doc.get("categorias") as? List<*>)?.filterIsInstance<String>()?.let {
                    misCategorias.clear()
                    misCategorias.addAll(it)
                }

                (doc.get("descripcionesPorCategoria") as? Map<*, *>)?.entries?.forEach { entry ->
                    val clave = entry.key as? String
                    val valor = entry.value as? String
                    if (clave != null && valor != null) {
                        descripcionesMap[clave] = valor
                    }
                }
                _uiState.value = PerfilUiState.SuccessCargarPerfil(doc)
            },
            onFailure = { e -> _uiState.value = PerfilUiState.Error("Error al cargar perfil: ${e.message}") }
        )
    }

    fun cargarResenas(usuarioId: String) {
        repository.cargarResenasUsuario(usuarioId,
            onSuccess = { lista -> _uiState.value = PerfilUiState.SuccessCargarResenas(lista) },
            onFailure = { e -> _uiState.value = PerfilUiState.Error("Error al cargar reseñas: ${e.message}") }
        )
    }

    fun guardarCambiosPerfil(nombre: String, telefono: String, fotoPerfilB64: String?) {
        val uid = obtenerUidActual() ?: return

        val updates = hashMapOf<String, Any>(
            "nombre" to nombre,
            "telefono" to telefono,
            "qrYapeB64" to (qrYapeB64 ?: ""),
            "qrPlinB64" to (qrPlinB64 ?: "")
        )

        fotoPerfilB64?.let { updates["fotoPerfilB64"] = it }

        if (userRol == "trabajador") {
            updates["categorias"] = misCategorias
            updates["descripcionesPorCategoria"] = descripcionesMap
        }

        repository.actualizarCamposPerfil(uid, updates,
            onSuccess = { _uiState.value = PerfilUiState.SuccessOperacion("Perfil actualizado con éxito") },
            onFailure = { e -> _uiState.value = PerfilUiState.Error("Error al guardar: ${e.message}") }
        )
    }

    fun eliminarQRBilletera(esYape: Boolean) {
        val uid = obtenerUidActual() ?: return
        val campoAEditar = if (esYape) "qrYapeB64" else "qrPlinB64"

        if (esYape) qrYapeB64 = null else qrPlinB64 = null

        val updates = hashMapOf<String, Any?>(campoAEditar to null)

        repository.actualizarCamposPerfil(uid, updates.filterValues { it != null }.mapValues { it.value!! },
            onSuccess = { _uiState.value = PerfilUiState.SuccessOperacion("QR eliminado correctamente") },
            onFailure = { e -> _uiState.value = PerfilUiState.Error("Error al eliminar QR: ${e.message}") }
        )
    }

    fun subirFotoPortafolio(b64: String, categoria: String, uid: String) {
        val fotoId = String.format("%s_%d", categoria, System.currentTimeMillis())
        val nuevaFoto = FotoTrabajo(id = fotoId, urlB64 = b64, categoria = categoria)

        repository.agregarFotoPortafolio(uid, fotoId, nuevaFoto,
            onSuccess = { _uiState.value = PerfilUiState.SuccessOperacion("Foto añadida a $categoria") },
            onFailure = { e -> _uiState.value = PerfilUiState.Error("Error al subir foto: ${e.message}") }
        )
    }

    fun eliminarFotoDePortafolio(foto: FotoTrabajo) {
        val uid = obtenerUidActual() ?: return
        repository.eliminarFotoPortafolio(uid, foto.id,
            onSuccess = { _uiState.value = PerfilUiState.SuccessOperacion("Foto eliminada de portafolio") },
            onFailure = { e -> _uiState.value = PerfilUiState.Error("Error al eliminar foto: ${e.message}") }
        )
    }

    fun recuperarPortafolioDinamico(uid: String, esModoLectura: Boolean, categoriaFiltro: String?, onResult: (List<FotoTrabajo>) -> Unit) {
        if (esModoLectura && !categoriaFiltro.isNullOrEmpty()) {
            repository.consultarPortafolioPorCategoria(uid, categoriaFiltro) { snap ->
                onResult(snap.toObjects(FotoTrabajo::class.java))
            }
        } else {
            repository.escucharPortafolioCompleto(uid) { repository.escucharPortafolioCompleto(uid) { snap ->
                snap?.let { onResult(it.toObjects(FotoTrabajo::class.java)) }
            }}
        }
    }
}