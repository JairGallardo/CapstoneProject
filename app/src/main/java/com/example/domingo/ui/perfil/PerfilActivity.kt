package com.example.domingo.ui.perfil

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.ui.perfil.PortafolioAdapter
import com.example.domingo.R
import com.example.domingo.model.FotoTrabajo
import com.example.domingo.ui.login.LoginActivity
import java.io.ByteArrayOutputStream

class PerfilActivity : AppCompatActivity() {

    private val viewModel: PerfilViewModel by viewModels()

    private lateinit var adapterPortafolio: PortafolioAdapter
    private var fotoTemporalB64: String? = null
    private var modoCaptura = 0
    private var idUsuarioAVer: String? = null
    private var categoriaFiltro: String? = null

    private val categoriasDisponibles = arrayOf("Gasfitero", "Electricista", "Limpieza", "Lavandería", "Mascotas", "Pintor", "Carpintero", "Jardinería", "Soporte Técnico")

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                val scaledBitmap = Bitmap.createScaledBitmap(it, 480, 480, true)
                val b64 = bitmapToBase64(scaledBitmap)
                if (modoCaptura == 1) {
                    confirmarNuevaFotoPortafolio(b64)
                } else {
                    findViewById<ImageView>(R.id.ivPerfilFoto).setImageBitmap(it)
                    fotoTemporalB64 = b64
                }
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriToBitmap(it)?.let { bm ->
                val scaledBitmap = Bitmap.createScaledBitmap(bm, 600, 600, true)
                val b64 = bitmapToBase64(scaledBitmap)
                if (findViewById<RadioButton>(R.id.rbYapePerfil).isChecked) viewModel.qrYapeB64 = b64 else viewModel.qrPlinB64 = b64
                mostrarImagenBase64(b64, findViewById(R.id.ivQrActual))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        idUsuarioAVer = intent.getStringExtra("VER_USUARIO_ID")
        categoriaFiltro = intent.getStringExtra("CATEGORIA_FILTRO")
        val esModoLectura = idUsuarioAVer != null
        val targetUid = idUsuarioAVer ?: viewModel.obtenerUidActual()

        configurarInterfaz(esModoLectura)
        configurarObservadores()

        targetUid?.let { uid ->
            viewModel.cargarPerfil(uid)
            if (esModoLectura) viewModel.cargarResenas(uid)
        }

        vincularEventosUI(esModoLectura, targetUid)
    }

    private fun configurarObservadores() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is PerfilUiState.Loading -> { /* Mostrar Spinner */ }
                is PerfilUiState.SuccessCargarPerfil -> mapearDatosEnCampos(state.document)
                is PerfilUiState.SuccessCargarResenas -> pintarContenedorCalificaciones(state.resenas)
                is PerfilUiState.SuccessOperacion -> Toast.makeText(this, state.mensaje, Toast.LENGTH_SHORT).show()
                is PerfilUiState.Error -> Toast.makeText(this, state.mensaje, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun vincularEventosUI(esModoLectura: Boolean, targetUid: String?) {
        val containerDatos = findViewById<LinearLayout>(R.id.containerDatosPersonales)
        val containerPortafolio = findViewById<LinearLayout>(R.id.containerPortafolio)
        val containerPago = findViewById<LinearLayout>(R.id.containerPago)
        val containerCalificaciones = findViewById<LinearLayout>(R.id.containerCalificaciones)

        findViewById<TextView>(R.id.headerDatosPersonales).setOnClickListener { toggleSection(containerDatos, it as TextView, "Datos Personales") }
        findViewById<TextView>(R.id.headerPortafolio).setOnClickListener { toggleSection(containerPortafolio, it as TextView, "Portafolio y Oferta") }
        findViewById<TextView>(R.id.headerPago).setOnClickListener { toggleSection(containerPago, it as TextView, "Método de Pago") }
        findViewById<TextView>(R.id.headerCalificaciones).setOnClickListener { toggleSection(containerCalificaciones, it as TextView, "Calificaciones y Reseñas") }

        findViewById<Button>(R.id.btnActualizarQR).setOnClickListener { modoCaptura = 2; galleryLauncher.launch("image/*") }
        findViewById<Button>(R.id.btnSubirFotoPortafolio).setOnClickListener { modoCaptura = 1; cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) }
        findViewById<ImageButton>(R.id.btnCambiarFoto).setOnClickListener { modoCaptura = 0; cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) }

        findViewById<Button>(R.id.btnSeleccionarCategorias)?.setOnClickListener { mostrarDialogoCategorias() }
        findViewById<Button>(R.id.btnGuardarCambios).setOnClickListener {
            val nombre = findViewById<EditText>(R.id.etPerfilNombre).text.toString().trim()
            val telefono = findViewById<EditText>(R.id.etPerfilTelefono).text.toString().trim()
            viewModel.guardarCambiosPerfil(nombre, telefono, fotoTemporalB64)
        }

        findViewById<Button>(R.id.btnCerrarSesion).setOnClickListener {
            viewModel.cerrarSesion()
            startActivity(Intent(this, LoginActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        }

        val ivQrActual = findViewById<ImageView>(R.id.ivQrActual)
        findViewById<RadioGroup>(R.id.rgTipoQR).setOnCheckedChangeListener { _, checkedId ->
            val b64 = if (checkedId == R.id.rbYapePerfil) viewModel.qrYapeB64 else viewModel.qrPlinB64
            if (!b64.isNullOrEmpty()) mostrarImagenBase64(b64, ivQrActual) else ivQrActual.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        if (!esModoLectura) {
            ivQrActual.setOnLongClickListener {
                val rbYape = findViewById<RadioButton>(R.id.rbYapePerfil)
                val tipoBilletera = if (rbYape.isChecked) "Yape" else "Plin"
                val qrExistente = if (rbYape.isChecked) viewModel.qrYapeB64 else viewModel.qrPlinB64

                if (!qrExistente.isNullOrEmpty()) {
                    mostrarDialogoEliminarQR(tipoBilletera, rbYape.isChecked)
                } else {
                    Toast.makeText(this, "No hay imagen registrada para $tipoBilletera", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }

        targetUid?.let { setupPortafolioDinamico(it, esModoLectura) }
    }

    private fun mapearDatosEnCampos(doc: com.google.firebase.firestore.DocumentSnapshot) {
        val telefono = doc.getString("telefono") ?: ""
        findViewById<EditText>(R.id.etPerfilNombre).setText(doc.getString("nombre"))
        findViewById<EditText>(R.id.etPerfilTelefono).setText(telefono)
        findViewById<TextView>(R.id.tvPerfilCorreo).text = doc.getString("correo")

        if (idUsuarioAVer != null && telefono.isNotEmpty()) {
            val clickCall = View.OnClickListener { realizarLlamada(telefono) }
            findViewById<ImageButton>(R.id.btnLlamarTrabajador).setOnClickListener(clickCall)
            findViewById<EditText>(R.id.etPerfilTelefono).setOnClickListener(clickCall)
        }

        doc.getString("fotoPerfilB64")?.let { mostrarImagenBase64(it, findViewById(R.id.ivPerfilFoto)) }

        val ivQrActual = findViewById<ImageView>(R.id.ivQrActual)
        val b64Inicial = if (findViewById<RadioButton>(R.id.rbYapePerfil).isChecked) viewModel.qrYapeB64 else viewModel.qrPlinB64
        if (!b64Inicial.isNullOrEmpty()) mostrarImagenBase64(b64Inicial, ivQrActual)

        findViewById<View>(R.id.cardCalificaciones).visibility = View.VISIBLE

        if (viewModel.userRol == "trabajador") {
            findViewById<View>(R.id.cardPortafolio).visibility = View.VISIBLE

            if (idUsuarioAVer == null) {
                findViewById<View>(R.id.cardMetodoPago)?.visibility = View.VISIBLE
            } else {
                findViewById<View>(R.id.cardMetodoPago)?.visibility = View.GONE
            }

            if (idUsuarioAVer != null) {
                findViewById<View>(R.id.layoutDescripcionCategoriaLectura).visibility = View.VISIBLE
                if (!categoriaFiltro.isNullOrEmpty()) {
                    findViewById<TextView>(R.id.tvTituloDescCategoria).text = "Experiencia en $categoriaFiltro:"
                    viewModel.descripcionesMap[categoriaFiltro]?.let {
                        findViewById<TextView>(R.id.tvContenidoDescCategoria).text = it
                    }
                }
            }
        }
    }

    private fun mostrarDialogoEliminarQR(tipo: String, esYape: Boolean) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar QR de $tipo")
            .setMessage("¿Estás seguro de que deseas eliminar la imagen del código QR de $tipo?")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.eliminarQRBilletera(esYape)
                findViewById<ImageView>(R.id.ivQrActual).setImageResource(android.R.drawable.ic_menu_gallery)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoCategorias() {
        val rv = findViewById<RecyclerView>(R.id.rvEspecialidadesOculto)
        val btnEspecialidades = findViewById<Button>(R.id.btnSeleccionarCategorias)
        val containerEspecialidades = findViewById<LinearLayout>(R.id.containerEspecialidades)

        btnEspecialidades?.visibility = View.GONE
        containerEspecialidades?.visibility = View.VISIBLE

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return object : RecyclerView.ViewHolder(layoutInflater.inflate(R.layout.item_especialidad_edit, parent, false)) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val cat = categoriasDisponibles[position]
                val cb = holder.itemView.findViewById<CheckBox>(R.id.cbEspecialidad)
                cb.text = cat
                cb.setOnCheckedChangeListener(null)
                cb.isChecked = viewModel.misCategorias.contains(cat)

                cb.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        if (!viewModel.misCategorias.contains(cat)) viewModel.misCategorias.add(cat)
                    } else {
                        viewModel.misCategorias.remove(cat)
                        viewModel.descripcionesMap.remove(cat)
                    }
                }

                holder.itemView.findViewById<ImageButton>(R.id.btnEditarDesc).setOnClickListener { mostrarDialogoEditarDescripcion(cat) }
                holder.itemView.findViewById<ImageButton>(R.id.btnBorrarDesc).setOnClickListener {
                    if (viewModel.descripcionesMap.containsKey(cat)) {
                        AlertDialog.Builder(this@PerfilActivity)
                            .setTitle("Borrar Descripción")
                            .setMessage("¿Deseas borrar la descripción de $cat?")
                            .setPositiveButton("Sí") { _, _ -> viewModel.descripcionesMap.remove(cat) }
                            .setNegativeButton("Cancelar", null).show()
                    }
                }
            }
            override fun getItemCount() = categoriasDisponibles.size
        }

        findViewById<Button>(R.id.btnHechoEspecialidades)?.setOnClickListener {
            containerEspecialidades?.visibility = View.GONE
            btnEspecialidades?.visibility = View.VISIBLE
            val nombre = findViewById<EditText>(R.id.etPerfilNombre).text.toString().trim()
            val telefono = findViewById<EditText>(R.id.etPerfilTelefono).text.toString().trim()
            viewModel.guardarCambiosPerfil(nombre, telefono, fotoTemporalB64)
        }
    }

    private fun mostrarDialogoEditarDescripcion(categoria: String) {
        val et = EditText(this).apply { setText(viewModel.descripcionesMap[categoria] ?: "") }
        AlertDialog.Builder(this).setTitle("Descripción para $categoria").setView(et)
            .setPositiveButton("Guardar") { _, _ -> viewModel.descripcionesMap[categoria] = et.text.toString().trim() }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun pintarContenedorCalificaciones(listaResenas: List<Map<String, Any>>) {
        val rvCalificaciones = findViewById<RecyclerView>(R.id.rvCalificaciones)
        rvCalificaciones.layoutManager = LinearLayoutManager(this)
        rvCalificaciones.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val layout = LinearLayout(parent.context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    orientation = LinearLayout.VERTICAL
                    setPadding(20, 20, 20, 20)
                }
                return object : RecyclerView.ViewHolder(layout) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val container = holder.itemView as LinearLayout
                container.removeAllViews()

                val resena = listaResenas[position]
                val puntos = (resena["puntos"] as? Number)?.toFloat() ?: 0f
                val comentario = resena["comentario"] as? String ?: ""
                val emisorNombre = resena["emisorNombre"] as? String ?: "Usuario"

                container.addView(TextView(container.context).apply {
                    text = "⭐ ".repeat(puntos.toInt()) + " (${puntos}/5) - $emisorNombre"
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#FFB300"))
                })
                container.addView(TextView(container.context).apply {
                    text = if (comentario.isNotEmpty()) "\"$comentario\"" else "Sin comentarios."
                    setPadding(0, 8, 0, 8)
                    setTextColor(Color.parseColor("#444444"))
                })
                container.addView(View(container.context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 10, 0, 10) }
                    setBackgroundColor(Color.parseColor("#E0E0E0"))
                })
            }
            override fun getItemCount(): Int = listaResenas.size
        }
    }

    private fun setupPortafolioDinamico(uid: String, esModoLectura: Boolean) {
        adapterPortafolio = PortafolioAdapter(onLongClick = { foto ->
            if (!esModoLectura) {
                AlertDialog.Builder(this).setTitle("Eliminar imagen").setMessage("¿Deseas eliminar esta foto de tu portafolio?")
                    .setPositiveButton("Eliminar") { _, _ -> viewModel.eliminarFotoDePortafolio(foto) }
                    .setNegativeButton("Cancelar", null).show()
            }
        })
        findViewById<RecyclerView>(R.id.rvPortafolio).adapter = adapterPortafolio
        viewModel.recuperarPortafolioDinamico(uid, esModoLectura, categoriaFiltro) { adapterPortafolio.actualizar(it) }
    }

    private fun confirmarNuevaFotoPortafolio(b64: String) {
        val categoriesArray = viewModel.misCategorias.toTypedArray()
        if (categoriesArray.isEmpty()) {
            Toast.makeText(this, "Primero selecciona tus especialidades", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this).setTitle("¿A qué especialidad pertenece?")
            .setItems(categoriesArray) { _, which ->
                val uid = viewModel.obtenerUidActual() ?: return@setItems
                viewModel.subirFotoPortafolio(b64, categoriesArray[which], uid)
            }.show()
    }

    private fun configurarInterfaz(esModoLectura: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<EditText>(R.id.etPerfilNombre).apply { isFocusable = false; isClickable = false }

        if (esModoLectura) {
            supportActionBar?.title = "Perfil"
            findViewById<ImageButton>(R.id.btnLlamarTrabajador).visibility = View.VISIBLE
            findViewById<ImageButton>(R.id.btnCambiarFoto).visibility = View.GONE
            findViewById<Button>(R.id.btnGuardarCambios).visibility = View.GONE
            findViewById<Button>(R.id.btnCerrarSesion).visibility = View.GONE
            findViewById<Button>(R.id.btnSubirFotoPortafolio).visibility = View.GONE
            findViewById<View>(R.id.cardMetodoPago)?.visibility = View.GONE
            findViewById<Button>(R.id.btnSeleccionarCategorias)?.visibility = View.GONE
            findViewById<Button>(R.id.btnCambiarPass)?.visibility = View.GONE
            findViewById<EditText>(R.id.etPerfilTelefono).apply { isFocusable = false; isClickable = true }
            findViewById<RecyclerView>(R.id.rvPortafolio).layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        } else {
            supportActionBar?.title = "Mi Perfil"
            findViewById<ImageButton>(R.id.btnLlamarTrabajador).visibility = View.GONE
            findViewById<RecyclerView>(R.id.rvPortafolio).layoutManager = GridLayoutManager(this, 3)
            findViewById<Button>(R.id.btnSeleccionarCategorias)?.visibility = View.VISIBLE
            findViewById<EditText>(R.id.etPerfilTelefono).apply { isFocusable = true; isFocusableInTouchMode = true; isClickable = true }
        }
    }

    private fun realizarLlamada(numero: String) {
        startActivity(Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$numero") })
    }

    private fun toggleSection(container: View, header: TextView, title: String) {
        val isVis = container.visibility == View.VISIBLE
        container.visibility = if (isVis) View.GONE else View.VISIBLE
        header.text = "${if (isVis) "▶" else "▼"} $title"
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }
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