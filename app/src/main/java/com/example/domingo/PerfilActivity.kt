package com.example.domingo

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.model.FotoTrabajo
import com.example.domingo.ui.Login.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import kotlin.jvm.java

class PerfilActivity : AppCompatActivity() {

    private lateinit var adapterPortafolio: PortafolioAdapter
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var fotoTemporalB64: String? = null
    private var userRol: String? = null

    // --- VARIABLES PARA MULTI-CATEGORÍA ---
    private val categoriasDisponibles = arrayOf("Gasfitero", "Electricista", "Limpieza", "Lavandería", "Mascotas", "Pintor", "Carpintero")
    private val seleccionados = BooleanArray(categoriasDisponibles.size)
    private val misCategorias = mutableListOf<String>()

    // Lanzador de cámara
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                val scaledBitmap = Bitmap.createScaledBitmap(it, 480, 480, true)
                findViewById<ImageView>(R.id.ivPerfilFoto).setImageBitmap(scaledBitmap)
                val b64 = bitmapToBase64(scaledBitmap)

                if (userRol == "trabajador") confirmarNuevaFotoTrabajador(b64)
                else fotoTemporalB64 = b64
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Mi Perfil"

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val userId = auth.currentUser?.uid

        // Referencias a la UI
        val etNombre = findViewById<EditText>(R.id.etPerfilNombre)
        val etTelefono = findViewById<EditText>(R.id.etPerfilTelefono)
        val tvCorreo = findViewById<TextView>(R.id.tvPerfilCorreo)
        val ivPerfilFoto = findViewById<ImageView>(R.id.ivPerfilFoto)
        val btnCambiarFoto = findViewById<ImageButton>(R.id.btnCambiarFoto)
        val btnSeleccionarCats = findViewById<Button>(R.id.btnSeleccionarCategorias)
        val tvCategoriasVer = findViewById<TextView>(R.id.tvCategoriasSeleccionadas)
        val btnGuardar = findViewById<Button>(R.id.btnGuardarCambios)
        val btnSalir = findViewById<Button>(R.id.btnCerrarSesion)

        userId?.let { uid ->
            // CARGAR DATOS DESDE FIRESTORE
            db.collection("usuarios").document(uid).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    userRol = doc.getString("rol")
                    etNombre.setText(doc.getString("nombre"))
                    etTelefono.setText(doc.getString("telefono") ?: "")
                    tvCorreo.text = doc.getString("correo")

                    val fotoB64 = doc.getString("fotoPerfilB64")
                    if (!fotoB64.isNullOrEmpty()) mostrarImagenBase64(fotoB64, ivPerfilFoto)

                    // Cargar Categorías si es trabajador
                    if (userRol == "trabajador") {
                        val catsGuardadas = doc.get("categorias") as? List<String>
                        catsGuardadas?.let {
                            misCategorias.clear()
                            misCategorias.addAll(it)
                            tvCategoriasVer.text = misCategorias.joinToString(", ")

                            // Sincronizar checks para el diálogo
                            for (i in categoriasDisponibles.indices) {
                                if (misCategorias.contains(categoriasDisponibles[i])) {
                                    seleccionados[i] = true
                                }
                            }
                        }
                    }
                }
            }
            verificarEstatusTrabajador(uid)
        }

        // Evento: Abrir selector de categorías
        btnSeleccionarCats.setOnClickListener { mostrarSelectorCategorias(tvCategoriasVer) }

        // Evento: Cambiar Foto
        btnCambiarFoto.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraLauncher.launch(intent)
        }

        // Evento: Guardar Cambios
        btnGuardar.setOnClickListener {
            val nuevoNombre = etNombre.text.toString().trim()
            val nuevoTelefono = etTelefono.text.toString().trim()
            val layoutServicio = findViewById<LinearLayout>(R.id.layoutConfiguracionServicio)

            if (nuevoNombre.isEmpty() || nuevoTelefono.isEmpty()) {
                Toast.makeText(this, "Nombre y Teléfono son obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updates = mutableMapOf<String, Any>(
                "nombre" to nuevoNombre,
                "telefono" to nuevoTelefono
            )

            fotoTemporalB64?.let { updates["fotoPerfilB64"] = it }

            if (layoutServicio.visibility == View.VISIBLE) {
                val precio = findViewById<EditText>(R.id.etPrecioBasePerfil).text.toString().toDoubleOrNull() ?: 0.0
                val desc = findViewById<EditText>(R.id.etDescripcionPerfil).text.toString()

                updates["precioBase"] = precio
                updates["descripcion"] = desc
                updates["categorias"] = misCategorias
                // Guardamos la primera como principal para filtros rápidos
                if (misCategorias.isNotEmpty()) updates["categoria"] = misCategorias[0]
            }

            userId?.let { uid ->
                db.collection("usuarios").document(uid).update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
                        fotoTemporalB64 = null
                    }
            }
        }

        btnSalir.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    private fun mostrarSelectorCategorias(textView: TextView) {
        AlertDialog.Builder(this)
            .setTitle("Selecciona tus especialidades")
            .setMultiChoiceItems(categoriasDisponibles, seleccionados) { _, which, isChecked ->
                seleccionados[which] = isChecked
                if (isChecked) {
                    if (!misCategorias.contains(categoriasDisponibles[which])) {
                        misCategorias.add(categoriasDisponibles[which])
                    }
                } else {
                    misCategorias.remove(categoriasDisponibles[which])
                }
            }
            .setPositiveButton("Aceptar") { _, _ ->
                textView.text = if (misCategorias.isEmpty()) "Ninguna seleccionada"
                else misCategorias.joinToString(", ")
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun confirmarNuevaFotoTrabajador(nuevaFotoB64: String) {
        AlertDialog.Builder(this)
            .setTitle("Revisión de Foto")
            .setMessage("Tu foto será revisada por un administrador. ¿Continuar?")
            .setPositiveButton("Enviar") { _, _ -> enviarNuevaFotoARevision(nuevaFotoB64) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun enviarNuevaFotoARevision(nuevaFotoB64: String) {
        val uid = auth.currentUser?.uid ?: return
        val updates = mapOf(
            "fotoPerfilTemporalB64" to nuevaFotoB64,
            "verificado" to "en_revision",
            "disponible" to false
        )
        db.collection("usuarios").document(uid).update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Enviado a revisión.", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
    }

    private fun mostrarImagenBase64(base64String: String, imageView: ImageView) {
        try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            imageView.setImageBitmap(BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size))
        } catch (e: Exception) { e.printStackTrace() }
    }
    // Configuración del RecyclerView en el portafolio
    private fun setupPortafolio() {
        val uid = auth.currentUser?.uid ?: return

        val rv = findViewById<RecyclerView>(R.id.rvPortafolio)
        rv.layoutManager = GridLayoutManager(this, 3)

        adapterPortafolio = PortafolioAdapter()
        rv.adapter = adapterPortafolio

        db.collection("usuarios").document(uid).collection("portafolio")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val listaFotos = snapshot.toObjects(FotoTrabajo::class.java)
                    adapterPortafolio.actualizar(listaFotos)
                }
            }
    }

    private fun verificarEstatusTrabajador(uid: String) {
        val layoutServicio = findViewById<LinearLayout>(R.id.layoutConfiguracionServicio)
        db.collection("usuarios").document(uid).get().addOnSuccessListener { doc ->
            val rol = doc.getString("rol")
            val verificado = doc.getString("verificado")

            if (rol == "trabajador" && verificado == "si") {
                layoutServicio.visibility = View.VISIBLE

                // Cargamos los datos adicionales del trabajador
                findViewById<EditText>(R.id.etPrecioBasePerfil).setText(doc.getDouble("precioBase")?.toString() ?: "")
                findViewById<EditText>(R.id.etDescripcionPerfil).setText(doc.getString("descripcion") ?: "")

                // Solo si es trabajador verificado, cargamos el portafolio
                setupPortafolio()
            }
        }
    }
}