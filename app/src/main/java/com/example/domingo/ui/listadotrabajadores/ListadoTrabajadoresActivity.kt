package com.example.domingo.ui.listadotrabajadores

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.R
import com.example.domingo.model.Socio
import com.example.domingo.ui.negociacion.NegociacionActivity

class ListadoTrabajadoresActivity : AppCompatActivity() {

    private val viewModel: ListadoTrabajadoresViewModel by viewModels()
    private lateinit var rvTrabajadores: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_listado_trabajadores)

        findViewById<ImageButton>(R.id.btnVolverListado).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        rvTrabajadores = findViewById(R.id.rvTrabajadores)
        rvTrabajadores.layoutManager = LinearLayoutManager(this)

        rvTrabajadores.adapter = SocioAdapter(
            listaSocios = mutableListOf(),
            categoriaActual = "Servicios",
            onClick = { }
        )

        configurarObservadores()

        val categoriaRecibida = intent.getStringExtra("CATEGORIA_SELECCIONADA") ?: "Servicios"
        viewModel.establecerYNormalizarCategoria(categoriaRecibida)
    }

    private fun configurarObservadores() {

        viewModel.categoriaFormateada.observe(this) { categoria ->
            findViewById<TextView>(R.id.tvTituloCategoria).text = "Especialistas en $categoria"
            viewModel.cargarEspecialistas()
        }

        viewModel.trabajadores.observe(this) { listaSocios ->
            val categoria = viewModel.categoriaFormateada.value ?: "Servicios"

            runOnUiThread {
                if (listaSocios.isNotEmpty()) {
                    rvTrabajadores.adapter = SocioAdapter(
                        listaSocios     = listaSocios.toMutableList(),
                        categoriaActual = categoria,
                        onClick         = { socio -> iniciarFlujoChat(socio) }
                    )
                    rvTrabajadores.post {
                        (rvTrabajadores.layoutManager as LinearLayoutManager).scrollToPosition(0)
                    }
                }
            }
        }

        viewModel.notificarVacio.observe(this) { campoVacio ->
            if (campoVacio == true) {
                val categoria = viewModel.categoriaFormateada.value ?: "Servicios"
                Toast.makeText(
                    this,
                    "No hay especialistas en línea para $categoria",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        viewModel.error.observe(this) { errorMsg ->
            Toast.makeText(this, "Error al cargar: $errorMsg", Toast.LENGTH_SHORT).show()
        }
    }

    private fun iniciarFlujoChat(socio: Socio) {
        viewModel.generarIntentNegociacion(socio) { chatIdUnico, error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                return@generarIntentNegociacion
            }

            val categoria = viewModel.categoriaFormateada.value ?: "Servicios"
            val intent = Intent(this, NegociacionActivity::class.java).apply {
                putExtra("CHAT_ID",            chatIdUnico)
                putExtra("RECEPTOR_ID",        socio.id)
                putExtra("ES_TRABAJADOR",      false)
                putExtra("SOCIO_NOMBRE",       socio.nombre)
                putExtra("CATEGORIA_SERVICIO", categoria)
            }
            startActivity(intent)
            Toast.makeText(
                this,
                "Iniciando servicio de $categoria con ${socio.nombre}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}