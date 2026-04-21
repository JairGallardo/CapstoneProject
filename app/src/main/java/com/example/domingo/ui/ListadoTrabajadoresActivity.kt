package com.example.domingo.ui

import Socio
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.CalculadoraCostos
import com.example.domingo.R
import com.example.domingo.SocioAdapter
import com.google.firebase.firestore.FirebaseFirestore

class ListadoTrabajadoresActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    // Coordenadas de referencia (Cajamarca)
    // TODO: En el futuro, obtener estas de intent.getDoubleExtra o LocationServices
    private val clienteLat = -7.1583
    private val clienteLon = -78.5153

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_listado_trabajadores)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // IMPORTANTE: Asegúrate de que el Intent envíe "CATEGORIA_SELECCIONADA"
        val categoria = intent.getStringExtra("CATEGORIA_SELECCIONADA") ?: "Servicios"
        supportActionBar?.title = "Especialistas: $categoria"

        findViewById<TextView>(R.id.tvTituloCategoria).text = "Especialistas en $categoria"

        val rv = findViewById<RecyclerView>(R.id.rvTrabajadores)
        rv.layoutManager = LinearLayoutManager(this)

        cargarTrabajadores(categoria, rv)
    }
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun cargarTrabajadores(categoriaSeleccionada: String, recyclerView: RecyclerView) {
        db.collection("usuarios")
            .whereEqualTo("rol", "trabajador")
            .whereEqualTo("verificado", "si")
            .whereEqualTo("disponible", true)
            .whereEqualTo("categoria", categoriaSeleccionada)
            .get()
            .addOnSuccessListener { documentos ->
                val listaSociosProvisional = mutableListOf<Socio>()

                if (documentos.isEmpty) {
                    Toast.makeText(this, "No hay especialistas en línea para $categoriaSeleccionada", Toast.LENGTH_LONG).show()
                }

                for (doc in documentos) {
                    // 1. Datos básicos
                    val id = doc.id
                    val nombre = doc.getString("nombre") ?: "Socio DominGO"
                    val fotoB64 = doc.getString("fotoPerfilB64") ?: ""
                    val rating = doc.getDouble("rating") ?: 5.0
                    val trabajos = doc.getLong("trabajosRealizados")?.toInt() ?: 0
                    val descripcion = doc.getString("descripcion") ?: ""

                    // 2. Datos de ubicación y precio base
                    val tLat = doc.getDouble("latitud") ?: 0.0
                    val tLon = doc.getDouble("longitud") ?: 0.0
                    val precioBase = doc.getDouble("precioBase") ?: 0.0

                    // 3. Cálculo de distancia y presupuesto
                    val distancia = CalculadoraCostos.calcularDistanciaKm(
                        clienteLat, clienteLon, tLat, tLon
                    )
                    val presupuesto = CalculadoraCostos.obtenerPresupuesto(distancia, precioBase)
                    val totalFinal = presupuesto["totalFinal"] ?: 0.0

                    // 4. Creamos el objeto Socio con los nombres de campos correctos
                    listaSociosProvisional.add(
                        Socio(
                        id = id,
                        nombre = nombre,
                        fotoPerfilB64 = fotoB64,
                        rating = rating,
                        trabajosRealizados = trabajos,
                        tarifaSugerida = totalFinal, // Usamos Double para que el Adapter lo formatee
                        distancia = distancia,
                        descripcion = descripcion
                    ))
                }

                // 5. Ordenar por cercanía
                val listaOrdenada = listaSociosProvisional.sortedBy { it.distancia }

                // 6. Asignar el adaptador
                recyclerView.adapter = SocioAdapter(listaOrdenada)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al cargar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}