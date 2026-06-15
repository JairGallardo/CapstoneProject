package com.example.domingo.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.domingo.R
import com.example.domingo.ui.listadotrabajadores.SocioAdapter
import com.example.domingo.model.Categoria
import com.example.domingo.ui.listadotrabajadores.CategoriaAdapter
import com.example.domingo.ui.perfil.PerfilActivity
import com.example.domingo.ui.listadotrabajadores.ListadoTrabajadoresActivity
import com.example.domingo.ui.negociacion.NegociacionActivity
import com.example.domingo.ui.atencion.AtencionClienteActivity
import com.example.domingo.ui.notificaciones.NotificacionesActivity
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var bandejaAdapter: SocioAdapter
    private val sugerenciasCategorias = arrayOf(
        "Gasfitero", "Electricista", "Limpieza", "Lavandería",
        "Mascotas", "Pintor", "Carpintero", "Jardinería", "Soporte Técnico"
    )

    private lateinit var viewPagerCarrusel: ViewPager2
    private lateinit var layoutIndicadores: LinearLayout
    private val carruselHandler = Handler(Looper.getMainLooper())
    private var cantidadImagenes = 0

    private val listaMaestraCategorias = listOf(
        Categoria("Gasfitero",       R.drawable.gasfiteria),
        Categoria("Electricista",    R.drawable.electricista),
        Categoria("Limpieza",        R.drawable.limpieza),
        Categoria("Lavandería",      R.drawable.lavanderia),
        Categoria("Mascotas",        R.drawable.mascotas),
        Categoria("Pintor",          R.drawable.pintura),
        Categoria("Carpintero",      R.drawable.carpinteria),
        Categoria("Jardinería",      R.drawable.jardineria),
        Categoria("Soporte Técnico", R.drawable.pc)
    )

    private var categoriaAdapter: CategoriaAdapter? = null
    private var estaExpandidoCategorias = false

    private val carruselRunnable = object : Runnable {
        override fun run() {
            if (cantidadImagenes > 0 && ::viewPagerCarrusel.isInitialized) {
                val siguienteItem = (viewPagerCarrusel.currentItem + 1) % cantidadImagenes
                viewPagerCarrusel.currentItem = siguienteItem
                carruselHandler.postDelayed(this, 4000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPagerCarrusel = findViewById(R.id.vpCarrusel)
        layoutIndicadores = findViewById(R.id.layoutIndicadores)

        findViewById<RecyclerView>(R.id.rvHistorialServicios)?.layoutManager = LinearLayoutManager(this)
        findViewById<RecyclerView>(R.id.rvBandejaEntrada)?.layoutManager    = LinearLayoutManager(this)

        setupCategorias()
        setupBuscador()
        setupListeners()
        configurarObservadores()

        viewModel.inicializarDatos()
    }

    private fun configurarObservadores() {
        val layoutTrabajador = findViewById<CardView>(R.id.layoutTrabajador)
        val switchEstado     = findViewById<SwitchCompat>(R.id.switchEstado)
        val rvHistorial      = findViewById<RecyclerView>(R.id.rvHistorialServicios)

        viewModel.esTrabajador.observe(this) { esTrabajador ->
            layoutTrabajador?.visibility = if (esTrabajador) View.VISIBLE else View.GONE
            actualizarAdapterCategorias(
                esTrabajador = esTrabajador,
                conteos      = viewModel.conteoPorCategoria.value ?: emptyMap()
            )
        }

        viewModel.nombreUsuario.observe(this) { nombre ->
            findViewById<TextView>(R.id.tvGreeting)?.text = "Hola, $nombre"
        }

        viewModel.disponible.observe(this) { disponible ->
            if (switchEstado != null && switchEstado.isChecked != disponible) {
                switchEstado.isChecked = disponible
            }
            switchEstado?.text = if (disponible) "Disponible" else "Desconectado"
        }

        viewModel.historial.observe(this) { lista ->
            val esTrabajador = viewModel.esTrabajador.value ?: false
            rvHistorial?.layoutManager = LinearLayoutManager(this)
            rvHistorial?.adapter       = HistorialAdapter(lista, esTrabajador)
        }

        viewModel.conteoPorCategoria.observe(this) { conteos ->
            val esTrabajador = viewModel.esTrabajador.value ?: false
            actualizarAdapterCategorias(esTrabajador = esTrabajador, conteos = conteos)
        }

        viewModel.bandejaSocios.observe(this) { listaSocios ->
            val rvBandeja = findViewById<RecyclerView>(R.id.rvBandejaEntrada)

            if (!::bandejaAdapter.isInitialized) {
                bandejaAdapter = SocioAdapter(
                    listaSocios.toMutableList(),
                    categoriaActual = null,
                    onLongClick = { socioABorrar ->
                        AlertDialog.Builder(this)
                            .setTitle("Eliminar Conversación")
                            .setMessage("¿Estás seguro de que deseas eliminar este chat de tu bandeja por completo? Esta acción no se puede deshacer.")
                            .setPositiveButton("Eliminar") { _, _ ->
                                viewModel.eliminarConversacionCompleta(socioABorrar.id)
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    },
                    onClick = { socio ->
                        val esTrabajadorAlMomentoDeClick = viewModel.esTrabajador.value ?: false
                        startActivity(Intent(this, NegociacionActivity::class.java).apply {
                            putExtra("CHAT_ID",      socio.id)
                            putExtra("RECEPTOR_ID",  socio.receptorId)
                            putExtra("ES_TRABAJADOR", esTrabajadorAlMomentoDeClick)
                            putExtra("SOCIO_NOMBRE",  socio.nombre)
                            putExtra("SOLO_LECTURA",  !socio.activo)
                        })
                    }
                )
                rvBandeja.adapter = bandejaAdapter
            } else {
                bandejaAdapter.actualizarLista(listaSocios)
            }
        }

        viewModel.errorMensaje.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        viewModel.imagenesCarrusel.observe(this) { listaImagenes ->
            if (listaImagenes.isNotEmpty()) {
                cantidadImagenes = listaImagenes.size
                viewPagerCarrusel.adapter = CarruselAdapter(listaImagenes)
                setupIndicadores(listaImagenes.size)
                actualizarIndicador(0)
                viewPagerCarrusel.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        actualizarIndicador(position)
                        carruselHandler.removeCallbacks(carruselRunnable)
                        carruselHandler.postDelayed(carruselRunnable, 4000)
                    }
                })
            }
        }
    }

    private fun actualizarAdapterCategorias(esTrabajador: Boolean, conteos: Map<String, Int>) {
        val listaConConteos = listaMaestraCategorias.map { cat ->
            cat.copy(trabajosRealizados = conteos[cat.nombre] ?: 0)
        }

        val listaVisible = if (estaExpandidoCategorias) listaConConteos
        else listaConConteos.take(8)

        val rvCategorias = findViewById<RecyclerView>(R.id.rvCategorias) ?: return

        if (categoriaAdapter == null) {
            categoriaAdapter = CategoriaAdapter(
                lista        = listaVisible,
                esTrabajador = esTrabajador,
                onClick      = { categoria -> abrirServicio(categoria.nombre) }
            )
            rvCategorias.adapter = categoriaAdapter
        } else {
            categoriaAdapter = CategoriaAdapter(
                lista        = listaVisible,
                esTrabajador = esTrabajador,
                onClick      = { categoria -> abrirServicio(categoria.nombre) }
            )
            rvCategorias.adapter = categoriaAdapter
        }
    }

    private fun setupCategorias() {
        val rvCategorias = findViewById<RecyclerView>(R.id.rvCategorias) ?: return
        val btnVerMas    = findViewById<TextView>(R.id.btnVerMasCategorias)

        rvCategorias.layoutManager = GridLayoutManager(this, 4)

        if (listaMaestraCategorias.size > 8) {
            btnVerMas?.visibility = View.VISIBLE
            btnVerMas?.text       = "Ver más categorías"
            btnVerMas?.setOnClickListener {
                val esTrabajador = viewModel.esTrabajador.value ?: false
                val conteos      = viewModel.conteoPorCategoria.value ?: emptyMap()
                val listaConConteos = listaMaestraCategorias.map { cat ->
                    cat.copy(trabajosRealizados = conteos[cat.nombre] ?: 0)
                }

                if (!estaExpandidoCategorias) {
                    categoriaAdapter = CategoriaAdapter(
                        lista        = listaConConteos,
                        esTrabajador = esTrabajador,
                        onClick      = { categoria -> abrirServicio(categoria.nombre) }
                    )
                    rvCategorias.adapter = categoriaAdapter
                    btnVerMas.text = "Ver menos"
                    estaExpandidoCategorias = true
                } else {
                    categoriaAdapter = CategoriaAdapter(
                        lista        = listaConConteos.take(8),
                        esTrabajador = esTrabajador,
                        onClick      = { categoria -> abrirServicio(categoria.nombre) }
                    )
                    rvCategorias.adapter = categoriaAdapter
                    btnVerMas.text = "Ver más categorías"
                    estaExpandidoCategorias = false
                }
            }
        } else {
            btnVerMas?.visibility = View.GONE
        }
    }

    private fun setupIndicadores(tamano: Int) {
        layoutIndicadores.removeAllViews()
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(8, 0, 8, 0) }

        for (i in 0 until tamano) {
            val iv = ImageView(applicationContext).apply {
                setImageDrawable(ContextCompat.getDrawable(applicationContext, android.R.drawable.radiobutton_off_background))
                layoutParams = params
            }
            layoutIndicadores.addView(iv)
        }
    }

    private fun actualizarIndicador(posicion: Int) {
        for (i in 0 until layoutIndicadores.childCount) {
            val iv = layoutIndicadores.getChildAt(i) as ImageView
            val resId = if (i == posicion) android.R.drawable.radiobutton_on_background
            else               android.R.drawable.radiobutton_off_background
            iv.setImageDrawable(ContextCompat.getDrawable(applicationContext, resId))
        }
    }

    override fun onResume() {
        super.onResume()
        carruselHandler.postDelayed(carruselRunnable, 1000)

        val rvBandeja = findViewById<RecyclerView>(R.id.rvBandejaEntrada)
        if (rvBandeja != null && rvBandeja.visibility == View.VISIBLE) {
            viewModel.conmutarBandejaEntrada(true)
        }

        if (viewModel.esTrabajador.value == true) {
            val mainRepo = com.example.domingo.data.repository.MainRepository()
            mainRepo.obtenerUsuarioId()?.let { viewModel.cargarConteoPorCategoria(it) }
        }
    }

    override fun onPause() {
        super.onPause()
        carruselHandler.removeCallbacks(carruselRunnable)
    }

    private fun setupBuscador() {
        val etBuscar = findViewById<AutoCompleteTextView>(R.id.etBuscarServicio) ?: return
        val adapterSugerencias = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sugerenciasCategorias)
        etBuscar.setAdapter(adapterSugerencias)
        etBuscar.setOnItemClickListener { parent, _, position, _ ->
            abrirServicio(parent.getItemAtPosition(position).toString())
        }
        etBuscar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val texto = etBuscar.text.toString().trim()
                if (texto.isNotEmpty()) abrirServicio(texto)
                else Toast.makeText(this, "Escribe algo para buscar", Toast.LENGTH_SHORT).show()
                true
            } else false
        }
    }

    private fun setupFiltrosBandeja() {
        val tabLayout    = findViewById<TabLayout>(R.id.tabFiltrosBandeja) ?: return
        val esTrabajador = viewModel.esTrabajador.value ?: false

        tabLayout.visibility = View.VISIBLE

        if (tabLayout.tabCount == 0) {
            tabLayout.addTab(tabLayout.newTab().setText("Mensajes"))
            tabLayout.addTab(tabLayout.newTab().setText("Finalizados"))
        }

        tabLayout.getTabAt(0)?.select()

        tabLayout.clearOnTabSelectedListeners()
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewModel.actualizarFiltro(tab?.text?.toString() ?: "Mensajes")
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun mostrarBandeja() {
        val rvBandeja       = findViewById<RecyclerView>(R.id.rvBandejaEntrada)
        val scrollView      = findViewById<NestedScrollView>(R.id.mainScrollView)
        val headerPrincipal = findViewById<View>(R.id.headerBackground)
        val headerBandeja   = findViewById<View>(R.id.headerBandeja)

        rvBandeja.visibility = View.VISIBLE
        setupFiltrosBandeja()

        viewModel.actualizarFiltro("Mensajes")

        scrollView?.visibility      = View.GONE
        headerPrincipal?.visibility = View.GONE
        headerBandeja?.visibility   = View.VISIBLE

        viewModel.conmutarBandejaEntrada(true)
    }

    private fun ocultarBandeja() {
        val rvBandeja       = findViewById<RecyclerView>(R.id.rvBandejaEntrada)
        val scrollView      = findViewById<NestedScrollView>(R.id.mainScrollView)
        val headerPrincipal = findViewById<View>(R.id.headerBackground)
        val headerBandeja   = findViewById<View>(R.id.headerBandeja)

        rvBandeja.visibility = View.GONE
        findViewById<TabLayout>(R.id.tabFiltrosBandeja)?.visibility = View.GONE

        scrollView?.visibility      = View.VISIBLE
        headerPrincipal?.visibility = View.VISIBLE
        headerBandeja?.visibility   = View.GONE

        viewModel.conmutarBandejaEntrada(false)
    }

    private fun setupListeners() {
        val rvBandeja = findViewById<RecyclerView>(R.id.rvBandejaEntrada)

        findViewById<ImageButton>(R.id.btnAtencionCliente)?.setOnClickListener {
            startActivity(Intent(this, AtencionClienteActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnNotificaciones)?.setOnClickListener {
            startActivity(Intent(this, NotificacionesActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnIrAPerfil)?.setOnClickListener {
            startActivity(Intent(this, PerfilActivity::class.java))
        }

        findViewById<SwitchCompat>(R.id.switchEstado)?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.cambiarDisponibilidad(isChecked)
        }

        findViewById<ImageButton>(R.id.btnVerMensajes)?.setOnClickListener {
            if (rvBandeja.visibility == View.GONE) mostrarBandeja() else ocultarBandeja()
        }

        findViewById<ImageButton>(R.id.btnVolverBandeja)?.setOnClickListener {
            ocultarBandeja()
        }
    }

    override fun onBackPressed() {
        val rvBandeja = findViewById<RecyclerView>(R.id.rvBandejaEntrada)
        if (rvBandeja.visibility == View.VISIBLE) {
            ocultarBandeja()
        } else {
            super.onBackPressed()
        }
    }

    private fun abrirServicio(nombreCategoria: String) {
        val categoriaFormateada = nombreCategoria.trim().lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        startActivity(Intent(this, ListadoTrabajadoresActivity::class.java).apply {
            putExtra("CATEGORIA_SELECCIONADA", categoriaFormateada)
        })
    }
}