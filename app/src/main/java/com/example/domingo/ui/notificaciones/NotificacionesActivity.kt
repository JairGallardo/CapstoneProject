package com.example.domingo.ui.notificaciones

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.R
import com.google.android.material.tabs.TabLayout

class NotificacionesActivity : AppCompatActivity() {

    private val viewModel: NotificacionesViewModel by viewModels()

    private lateinit var rvNotificaciones: RecyclerView
    private lateinit var layoutVacio: View
    private lateinit var tvVacio: TextView
    private lateinit var tabsNotif: TabLayout

    private val tabsFiltros = listOf("Todas", "No leídas", "Alertas", "Sistema")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notificaciones)

        bindViews()
        setupTabs()
        setupListeners()
        configurarObservadores()

        viewModel.iniciar()
    }

    private fun bindViews() {
        rvNotificaciones = findViewById(R.id.rvNotificaciones)
        layoutVacio      = findViewById(R.id.layoutVacioNotif)
        tvVacio          = findViewById(R.id.tvVacioNotif)
        tabsNotif        = findViewById(R.id.tabsNotif)

        rvNotificaciones.layoutManager = LinearLayoutManager(this)
    }

    private fun setupTabs() {
        tabsFiltros.forEach { etiqueta ->
            tabsNotif.addTab(tabsNotif.newTab().setText(etiqueta))
        }

        tabsNotif.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewModel.cambiarFiltro(tab?.text?.toString() ?: "Todas")
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.btnVolverNotif)?.setOnClickListener { finish() }

        findViewById<TextView>(R.id.btnMarcarTodasLeidas)?.setOnClickListener {
            viewModel.marcarTodasComoLeidas()
            Toast.makeText(this, "Todas marcadas como leídas", Toast.LENGTH_SHORT).show()
        }
    }

    private fun configurarObservadores() {
        viewModel.notificaciones.observe(this) { lista ->
            if (rvNotificaciones.adapter == null) {
                rvNotificaciones.adapter = NotificacionAdapter(lista.toMutableList()) { notif ->
                    if (!notif.leida) viewModel.marcarComoLeida(notif.id)
                }
            } else {
                (rvNotificaciones.adapter as NotificacionAdapter).actualizarLista(lista)
            }
        }

        viewModel.estaVacio.observe(this) { vacio ->
            rvNotificaciones.visibility = if (vacio) View.GONE  else View.VISIBLE
            layoutVacio.visibility      = if (vacio) View.VISIBLE else View.GONE
        }

        viewModel.textoVacio.observe(this) { texto ->
            tvVacio.text = texto
        }

        viewModel.errorMensaje.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}