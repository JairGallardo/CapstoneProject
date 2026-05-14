package com.example.domingo.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.ui.PerfilActivity
import com.example.domingo.R
import com.example.domingo.SocioAdapter
import com.example.domingo.model.Socio
import com.example.domingo.ui.ListadoTrabajadoresActivity
import com.example.domingo.ui.NegociacionActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.jvm.java

class MainActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var bandejaAdapter: SocioAdapter
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val requestLocationPermissionLauncher = registerForActivityResult(

        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocation || coarseLocation) {
            val switchEstado = findViewById<SwitchCompat>(R.id.switchEstado)
            if (switchEstado != null) actualizarDisponibilidad(true, switchEstado)
        } else {
            Toast.makeText(this, "Se requiere ubicación para trabajar", Toast.LENGTH_LONG).show()
            findViewById<SwitchCompat>(R.id.switchEstado)?.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupListeners()
        setupBuscador()
        verificarRolTrabajador()
    }

    private fun setupBuscador() {
        val etBuscar = findViewById<EditText>(R.id.etBuscarServicio)
        etBuscar?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val textoABuscar = etBuscar.text.toString().trim()
                if (textoABuscar.isNotEmpty()) abrirServicio(textoABuscar)
                else Toast.makeText(this, "Escribe algo para buscar", Toast.LENGTH_SHORT).show()
                true
            } else false
        }
    }

    private fun verificarRolTrabajador() {

        val uid = auth.currentUser?.uid ?: return

        val layoutTrabajador = findViewById<CardView>(R.id.layoutTrabajador)
        val switchEstado = findViewById<SwitchCompat>(R.id.switchEstado)
        val rvBandeja = findViewById<RecyclerView>(R.id.rvBandejaEntrada)
        val gridCategorias = findViewById<ScrollView>(R.id.scrollViewGrid)

        db.collection("usuarios").document(uid).get().addOnSuccessListener { doc ->
            val rol = doc.getString("rol")
            if (rol == "trabajador") {
                layoutTrabajador?.visibility = View.VISIBLE
                val estaDisponible = doc.getBoolean("disponible") ?: false
                switchEstado?.isChecked = estaDisponible
                switchEstado?.text = if (estaDisponible) "Disponible" else "Desconectado"
            } else {
                layoutTrabajador?.visibility = View.GONE
            }
        }

        findViewById<ImageButton>(R.id.btnVerMensajes)?.setOnClickListener {
            val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
            val searchCard = findViewById<androidx.cardview.widget.CardView>(R.id.searchCard)

            if (rvBandeja.visibility == View.GONE) {
                rvBandeja.visibility = View.VISIBLE
                gridCategorias.visibility = View.GONE
                searchCard.visibility = View.GONE // Ocultamos buscador en bandeja
                tvGreeting.text = "Bandeja de Entrada" // Cambiamos el título
                cargarBandejaEntrada()
            } else {
                rvBandeja.visibility = View.GONE
                gridCategorias.visibility = View.VISIBLE
                searchCard.visibility = View.VISIBLE // Mostramos buscador de nuevo
                tvGreeting.text = "¡Hola! ¿Qué necesitas hoy?" // Restauramos saludo
            }
        }
    }
    private fun cargarBandejaEntrada() {
        val uid = auth.currentUser?.uid ?: return
        val rvBandeja = findViewById<RecyclerView>(R.id.rvBandejaEntrada)
        rvBandeja.layoutManager = LinearLayoutManager(this)

        db.collection("usuarios").document(uid).get().addOnSuccessListener { userDoc ->
            val esPerfilTrabajador = userDoc.getString("rol") == "trabajador"

            val query = if (esPerfilTrabajador) {
                db.collection("negociaciones")
                    .whereEqualTo("trabajadorId", uid)
                    .whereEqualTo("activo", true)
            } else {
                db.collection("negociaciones")
                    .whereEqualTo("clienteId", uid)
                    .whereEqualTo("activo", true)
            }

            query.addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val listaSocios = mutableListOf<Socio>()
                val totalDocumentos = snapshot?.size() ?: 0

                if (totalDocumentos == 0) {
                    actualizarAdapter(listaSocios, rvBandeja, esPerfilTrabajador)
                    return@addSnapshotListener
                }

                var procesados = 0
                snapshot?.forEach { doc ->
                    val receptorId = if (esPerfilTrabajador) doc.getString("clienteId") ?: ""
                    else doc.getString("trabajadorId") ?: ""

                    val ultimoMsg = doc.getString("ultimoMensaje") ?: "Nuevo mensaje"

                    db.collection("usuarios").document(receptorId).get().addOnSuccessListener { userRef ->
                        val nombreReal = userRef.getString("nombre") ?: "Usuario DominGO"
                        val fotoReal = userRef.getString("fotoPerfilB64") ?: ""

                        listaSocios.add(
                            Socio(
                                id = doc.id,
                                nombre = nombreReal,
                                descripcion = ultimoMsg,
                                receptorId = receptorId,
                                fotoPerfilB64 = fotoReal
                            )
                        )

                        procesados++
                        if (procesados == totalDocumentos) {
                            actualizarAdapter(listaSocios, rvBandeja, esPerfilTrabajador)
                        }
                    }.addOnFailureListener {
                        procesados++
                        if (procesados == totalDocumentos) actualizarAdapter(listaSocios, rvBandeja, esPerfilTrabajador)
                    }
                }
            }
        }
    }

    private fun actualizarAdapter(lista: List<Socio>, rv: RecyclerView, esTrabajador: Boolean) {

        if (!::bandejaAdapter.isInitialized) {
            bandejaAdapter = SocioAdapter(lista.toMutableList()) { socio ->

                val intent = Intent(this, NegociacionActivity::class.java).apply {
                    putExtra("CHAT_ID", socio.id)
                    putExtra("RECEPTOR_ID", socio.receptorId)
                    putExtra("ES_TRABAJADOR", esTrabajador)
                    putExtra("SOCIO_NOMBRE", socio.nombre)
                }
                startActivity(intent)
            }
            rv.adapter = bandejaAdapter
        } else {
            bandejaAdapter.actualizarLista(lista)
        }
    }

    private fun actualizarDisponibilidad(disponible: Boolean, switch: SwitchCompat) {

        val uid = auth.currentUser?.uid ?: return
        if (disponible) {
            val googleApiAvailability = GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)

            if (resultCode != ConnectionResult.SUCCESS) {
                switch.isChecked = false
                googleApiAvailability.makeGooglePlayServicesAvailable(this)
                return
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                switch.isChecked = false
                requestLocationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                return
            }

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val updates = mutableMapOf<String, Any>("disponible" to true)
                if (location != null) {
                    updates["latitud"] = location.latitude
                    updates["longitud"] = location.longitude
                }

                db.collection("usuarios").document(uid).update(updates).addOnSuccessListener { switch.text = "Disponible" }
            }
        } else {
            db.collection("usuarios").document(uid).update("disponible", false).addOnSuccessListener { switch.text = "Desconectado" }
        }
    }

    private fun setupListeners() {

        findViewById<CardView>(R.id.btnWashApp)?.setOnClickListener { abrirServicio("Lavandería") }
        findViewById<CardView>(R.id.btnCleanHome)?.setOnClickListener { abrirServicio("Limpieza") }
        findViewById<CardView>(R.id.btnFixIt)?.setOnClickListener { abrirServicio("Gasfitero") }
        findViewById<CardView>(R.id.btnPetCare)?.setOnClickListener { abrirServicio("Mascotas") }
        findViewById<CardView>(R.id.btnElectricista)?.setOnClickListener { abrirServicio("Electricista") }
        findViewById<CardView>(R.id.btnPintor)?.setOnClickListener { abrirServicio("Pintor") }
        findViewById<CardView>(R.id.btnCarpintero)?.setOnClickListener { abrirServicio("Carpintero") }
        findViewById<CardView>(R.id.btnJardineria)?.setOnClickListener { abrirServicio("Jardinería") }
        findViewById<CardView>(R.id.btnTecnicoPC)?.setOnClickListener { abrirServicio("Técnico") }
        findViewById<CardView>(R.id.btnSoporteTecnico)?.setOnClickListener { abrirServicio("Soporte Técnico") }
        findViewById<ImageButton>(R.id.btnIrAPerfil)?.setOnClickListener { startActivity(
            Intent(
                this,
                PerfilActivity::class.java
            )
        ) }
        val switchEstado = findViewById<SwitchCompat>(R.id.switchEstado)
        switchEstado?.setOnCheckedChangeListener { _, isChecked -> actualizarDisponibilidad(isChecked, switchEstado) }
    }

    private fun abrirServicio(nombreCategoria: String) {

        val intent = Intent(this, ListadoTrabajadoresActivity::class.java)
        intent.putExtra("CATEGORIA_SELECCIONADA", nombreCategoria)
        startActivity(intent)
    }
}