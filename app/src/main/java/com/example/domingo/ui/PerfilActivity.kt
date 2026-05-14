package com.example.domingo.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.PortafolioAdapter
import com.example.domingo.R
import com.example.domingo.model.FotoTrabajo
import com.example.domingo.ui.Login.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

class PerfilActivity : AppCompatActivity() {

    private lateinit var adapterPortafolio: PortafolioAdapter
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var fotoTemporalB64: String? = null
    private var qrYapeB64: String? = null
    private var qrPlinB64: String? = null
    private var userRol: String? = null
    private var modoCaptura = 0
    private var idUsuarioAVer: String? = null

    private val categoriasDisponibles = arrayOf("Gasfitero", "Electricista", "Limpieza", "Lavandería", "Mascotas", "Pintor", "Carpintero", "Jardinería", "Soporte Técnico")
    private val seleccionados = BooleanArray(categoriasDisponibles.size)
    private val misCategorias = mutableListOf<String>()

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                val scaledBitmap = Bitmap.createScaledBitmap(it, 480, 480, true)
                val b64 = bitmapToBase64(scaledBitmap)
                when (modoCaptura) {
                    1 -> confirmarNuevaFotoPortafolio(b64)
                    else -> {
                        findViewById<ImageView>(R.id.ivPerfilFoto).setImageBitmap(it)
                        if (userRol == "trabajador" && modoCaptura == 0) {
                            fotoTemporalB64 = b64
                        } else {
                            fotoTemporalB64 = b64
                        }
                    }
                }
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriToBitmap(it)?.let { bm ->
                val scaledBitmap = Bitmap.createScaledBitmap(bm, 600, 600, true)
                actualizarEstadoQRLocal(bitmapToBase64(scaledBitmap))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        idUsuarioAVer = intent.getStringExtra("VER_USUARIO_ID")
        val esModoLectura = idUsuarioAVer != null
        val targetUid = idUsuarioAVer ?: auth.currentUser?.uid

        configurarInterfaz(esModoLectura)

        targetUid?.let { uid ->
            cargarDatosUsuario(uid, esModoLectura)
        }

        val containerDatos = findViewById<LinearLayout>(R.id.containerDatosPersonales)
        val containerPortafolio = findViewById<LinearLayout>(R.id.containerPortafolio)
        val containerPago = findViewById<LinearLayout>(R.id.containerPago)

        findViewById<TextView>(R.id.headerDatosPersonales).setOnClickListener { toggleSection(containerDatos, it as TextView, "Datos Personales") }
        findViewById<TextView>(R.id.headerPortafolio).setOnClickListener { toggleSection(containerPortafolio, it as TextView, "Portafolio y Oferta") }
        findViewById<TextView>(R.id.headerPago).setOnClickListener { toggleSection(containerPago, it as TextView, "Método de Pago") }

        findViewById<Button>(R.id.btnActualizarQR).setOnClickListener { modoCaptura = 2; galleryLauncher.launch("image/*") }
        findViewById<Button>(R.id.btnSubirFotoPortafolio).setOnClickListener { modoCaptura = 1; cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) }
        findViewById<ImageButton>(R.id.btnCambiarFoto).setOnClickListener { modoCaptura = 0; cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) }

        findViewById<Button>(R.id.btnSeleccionarCategorias)?.setOnClickListener {
            mostrarDialogoCategorias()
        }

        findViewById<Button>(R.id.btnGuardarCambios).setOnClickListener { guardarCambiosPerfil() }

        findViewById<Button>(R.id.btnCerrarSesion).setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        }

        findViewById<RadioGroup>(R.id.rgTipoQR).setOnCheckedChangeListener { _, checkedId ->
            val ivQr = findViewById<ImageView>(R.id.ivQrActual)
            val b64 = if (checkedId == R.id.rbYapePerfil) qrYapeB64 else qrPlinB64
            if (!b64.isNullOrEmpty()) mostrarImagenBase64(b64, ivQr) else ivQr.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    private fun mostrarDialogoCategorias() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Selecciona tus especialidades")
        builder.setMultiChoiceItems(categoriasDisponibles, seleccionados) { _, which, isChecked ->
            seleccionados[which] = isChecked
        }
        builder.setPositiveButton("Aceptar") { _, _ ->
            misCategorias.clear()
            for (i in categoriasDisponibles.indices) {
                if (seleccionados[i]) misCategorias.add(categoriasDisponibles[i])
            }
            Toast.makeText(this, "Especialidades seleccionadas: ${misCategorias.size}", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun configurarInterfaz(esModoLectura: Boolean) {
        val btnLlamar = findViewById<ImageButton>(R.id.btnLlamarTrabajador)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (esModoLectura) {
            supportActionBar?.title = "Perfil"
            btnLlamar.visibility = View.VISIBLE

            findViewById<ImageButton>(R.id.btnCambiarFoto).visibility = View.GONE
            findViewById<Button>(R.id.btnGuardarCambios).visibility = View.GONE
            findViewById<Button>(R.id.btnCerrarSesion).visibility = View.GONE
            findViewById<Button>(R.id.btnSubirFotoPortafolio).visibility = View.GONE
            findViewById<View>(R.id.cardMetodoPago)?.visibility = View.GONE
            findViewById<Button>(R.id.btnSeleccionarCategorias)?.visibility = View.GONE
            findViewById<Button>(R.id.btnCambiarPass)?.visibility = View.GONE

            findViewById<EditText>(R.id.etPerfilNombre).apply { isFocusable = false; isClickable = false }
            findViewById<EditText>(R.id.etPerfilTelefono).apply { isFocusable = false; isClickable = true }
            findViewById<EditText>(R.id.etDescripcionPerfil).isEnabled = false

            findViewById<RecyclerView>(R.id.rvPortafolio).layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        } else {
            supportActionBar?.title = "Mi Perfil"
            btnLlamar.visibility = View.GONE
            findViewById<RecyclerView>(R.id.rvPortafolio).layoutManager = GridLayoutManager(this, 3)
            findViewById<Button>(R.id.btnSeleccionarCategorias)?.visibility = View.VISIBLE
        }
    }

    private fun cargarDatosUsuario(uid: String, esModoLectura: Boolean) {
        db.collection("usuarios").document(uid).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                userRol = doc.getString("rol")
                val telefono = doc.getString("telefono") ?: ""
                findViewById<EditText>(R.id.etPerfilNombre).setText(doc.getString("nombre"))
                findViewById<EditText>(R.id.etPerfilTelefono).setText(telefono)
                findViewById<TextView>(R.id.tvPerfilCorreo).text = doc.getString("correo")

                val cats = doc.get("categorias") as? List<String>
                if (cats != null) {
                    misCategorias.clear()
                    misCategorias.addAll(cats)
                    for (i in categoriasDisponibles.indices) {
                        seleccionados[i] = misCategorias.contains(categoriasDisponibles[i])
                    }
                }

                if (esModoLectura && telefono.isNotEmpty()) {
                    val btnLlamar = findViewById<ImageButton>(R.id.btnLlamarTrabajador)
                    btnLlamar.setOnClickListener { realizarLlamada(telefono) }
                    findViewById<EditText>(R.id.etPerfilTelefono).setOnClickListener { realizarLlamada(telefono) }
                }

                qrYapeB64 = doc.getString("qrYapeB64")
                qrPlinB64 = doc.getString("qrPlinB64")
                doc.getString("fotoPerfilB64")?.let { mostrarImagenBase64(it, findViewById(R.id.ivPerfilFoto)) }

                if (userRol == "trabajador") {
                    findViewById<View>(R.id.cardPortafolio).visibility = View.VISIBLE
                    findViewById<EditText>(R.id.etDescripcionPerfil).setText(doc.getString("descripcion") ?: "")
                    setupPortafolioDinamico(uid, esModoLectura)
                }
            }
        }
    }

    private fun realizarLlamada(numero: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$numero")
        startActivity(intent)
    }

    private fun setupPortafolioDinamico(uid: String, esModoLectura: Boolean) {
        adapterPortafolio = PortafolioAdapter()
        findViewById<RecyclerView>(R.id.rvPortafolio).adapter = adapterPortafolio

        val query = db.collection("usuarios").document(uid).collection("portafolio")
        if (esModoLectura) {
            query.get().addOnSuccessListener { snap -> adapterPortafolio.actualizar(snap.toObjects(FotoTrabajo::class.java)) }
        } else {
            query.addSnapshotListener { snap, _ -> snap?.let { adapterPortafolio.actualizar(it.toObjects(FotoTrabajo::class.java)) } }
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }
    }

    private fun actualizarEstadoQRLocal(b64: String) {
        if (findViewById<RadioButton>(R.id.rbYapePerfil).isChecked) qrYapeB64 = b64 else qrPlinB64 = b64
        mostrarImagenBase64(b64, findViewById(R.id.ivQrActual))
    }

    private fun guardarCambiosPerfil() {
        val uid = auth.currentUser?.uid ?: return
        val updates = mutableMapOf<String, Any>(
            "nombre" to findViewById<EditText>(R.id.etPerfilNombre).text.toString().trim(),
            "telefono" to findViewById<EditText>(R.id.etPerfilTelefono).text.toString().trim()
        )

        fotoTemporalB64?.let { updates["fotoPerfilB64"] = it }
        qrYapeB64?.let { updates["qrYapeB64"] = it }
        qrPlinB64?.let { updates["qrPlinB64"] = it }

        if (userRol == "trabajador") {
            updates["descripcion"] = findViewById<EditText>(R.id.etDescripcionPerfil).text.toString().trim()
            updates["categorias"] = misCategorias // Guardamos la lista de especialidades
        }

        db.collection("usuarios").document(uid).update(updates).addOnSuccessListener {
            Toast.makeText(this, "Perfil actualizado con éxito", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "Error al guardar: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleSection(container: View, header: TextView, title: String) {
        val isVis = container.visibility == View.VISIBLE
        container.visibility = if (isVis) View.GONE else View.VISIBLE
        header.text = "${if (isVis) "▶" else "▼"} $title"
    }

    private fun confirmarNuevaFotoPortafolio(b64: String) {
        val uid = auth.currentUser?.uid ?: return
        val ref = db.collection("usuarios").document(uid).collection("portafolio").document()
        ref.set(FotoTrabajo(id = ref.id, urlB64 = b64))
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun mostrarImagenBase64(base64String: String, imageView: ImageView) {
        try {
            val bytes = Base64.decode(base64String, Base64.DEFAULT)
            imageView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        } catch (e: Exception) { }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}