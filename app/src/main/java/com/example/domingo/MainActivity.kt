package com.example.domingo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import com.example.domingo.ui.ListadoTrabajadoresActivity
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

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
        setupBuscador() // <-- NUEVA FUNCIÓN
        verificarRolTrabajador()
    }

    private fun setupBuscador() {
        val etBuscar = findViewById<EditText>(R.id.etBuscarServicio)

        etBuscar?.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val textoABuscar = etBuscar.text.toString().trim()

                if (textoABuscar.isNotEmpty()) {
                    abrirServicio(textoABuscar) // Reutilizamos abrirServicio para buscar
                } else {
                    Toast.makeText(this, "Escribe algo para buscar", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        }
    }

    private fun verificarRolTrabajador() {
        val uid = auth.currentUser?.uid ?: return
        val layoutTrabajador = findViewById<CardView>(R.id.layoutTrabajador)
        val switchEstado = findViewById<SwitchCompat>(R.id.switchEstado)

        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && doc.getString("rol") == "trabajador") {
                    layoutTrabajador?.visibility = View.VISIBLE
                    val estaActivo = doc.getBoolean("disponible") ?: false
                    switchEstado?.isChecked = estaActivo
                    switchEstado?.text = if (estaActivo) "Disponible" else "Desconectado"

                    switchEstado?.setOnCheckedChangeListener { _, isChecked ->
                        actualizarDisponibilidad(isChecked, switchEstado)
                    }
                }
            }
    }

    private fun actualizarDisponibilidad(disponible: Boolean, switch: SwitchCompat) {
        val uid = auth.currentUser?.uid ?: return

        if (disponible) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                switch.isChecked = false
                requestLocationPermissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
                return
            }

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val updates = mutableMapOf<String, Any>("disponible" to true)
                if (location != null) {
                    updates["latitud"] = location.latitude
                    updates["longitud"] = location.longitude
                }

                db.collection("usuarios").document(uid).update(updates)
                    .addOnSuccessListener {
                        switch.text = "Disponible"
                        Toast.makeText(this, "¡En línea!", Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            db.collection("usuarios").document(uid).update("disponible", false)
                .addOnSuccessListener {
                    switch.text = "Desconectado"
                    Toast.makeText(this, "Modo desconectado", Toast.LENGTH_SHORT).show()
                }
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

        findViewById<ImageButton>(R.id.btnIrAPerfil)?.setOnClickListener {
            startActivity(Intent(this, PerfilActivity::class.java))
        }
    }

    private fun abrirServicio(nombreCategoria: String) {
        val intent = Intent(this, ListadoTrabajadoresActivity::class.java)
        intent.putExtra("CATEGORIA_SELECCIONADA", nombreCategoria)
        startActivity(intent)
    }
}