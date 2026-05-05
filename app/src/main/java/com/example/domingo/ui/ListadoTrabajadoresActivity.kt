package com.example.domingo.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.CalculadoraCostos
import com.example.domingo.R
import com.example.domingo.SocioAdapter
import com.example.domingo.model.Socio
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ListadoTrabajadoresActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val clienteLat = -7.1583
    private val clienteLon = -78.5153

    private var categoriaActual: String = "Servicios"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_listado_trabajadores)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        categoriaActual = intent.getStringExtra("CATEGORIA_SELECCIONADA") ?: "Servicios"
        supportActionBar?.title = "Especialistas: $categoriaActual"

        findViewById<TextView>(R.id.tvTituloCategoria).text = "Especialistas en $categoriaActual"

        val rv = findViewById<RecyclerView>(R.id.rvTrabajadores)
        rv.layoutManager = LinearLayoutManager(this)

        cargarTrabajadores(categoriaActual, rv)
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
            .whereArrayContains("categorias", categoriaSeleccionada)
            .get()
            .addOnSuccessListener { documentos ->
                val listaSociosProvisional = mutableListOf<Socio>()

                if (documentos.isEmpty) {
                    Toast.makeText(this, "No hay especialistas en línea para $categoriaSeleccionada", Toast.LENGTH_LONG).show()
                }

                for (doc in documentos) {
                    val id = doc.id
                    val nombre = doc.getString("nombre") ?: "Socio DominGO"
                    val fotoB64 = doc.getString("fotoPerfilB64") ?: ""
                    val rating = doc.getDouble("rating") ?: 5.0
                    val trabajos = doc.getLong("trabajosRealizados")?.toInt() ?: 0
                    val descripcion = doc.getString("descripcion") ?: ""
                    val tLat = doc.getDouble("latitud") ?: 0.0
                    val tLon = doc.getDouble("longitud") ?: 0.0
                    val precioBase = doc.getDouble("precioBase") ?: 0.0

                    val distancia = CalculadoraCostos.calcularDistanciaKm(
                        clienteLat, clienteLon, tLat, tLon
                    )
                    val presupuesto = CalculadoraCostos.obtenerPresupuesto(distancia, precioBase)
                    val totalFinal = presupuesto["totalFinal"] ?: 0.0

                    listaSociosProvisional.add(
                        Socio(
                            id = id,
                            nombre = nombre,
                            fotoPerfilB64 = fotoB64,
                            rating = rating,
                            trabajosRealizados = trabajos,
                            tarifaSugerida = totalFinal,
                            distancia = distancia,
                            descripcion = descripcion
                        )
                    )
                }

                val listaOrdenada = listaSociosProvisional.sortedBy { it.distancia }.toMutableList()
                recyclerView.adapter = SocioAdapter(
                    listaSocios = listaOrdenada,
                    onClick = { socio -> abrirNegociacion(socio) }
                )

                recyclerView.post {
                    (recyclerView.layoutManager as LinearLayoutManager).scrollToPosition(0)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al cargar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun abrirNegociacion(socio: Socio) {
        val uidActual = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Error de sesión. Vuelve a iniciar sesión.", Toast.LENGTH_SHORT).show()
            return
        }

        val catLimpia = categoriaActual.replace(" ", "_")
        val chatIdUnico = "chat_${uidActual}_${socio.id}_$catLimpia"

        val intent = Intent(this, NegociacionActivity::class.java).apply {
            putExtra("CHAT_ID", chatIdUnico)
            putExtra("RECEPTOR_ID", socio.id)
            putExtra("ES_TRABAJADOR", false)
            putExtra("SOCIO_NOMBRE", socio.nombre)
            putExtra("CATEGORIA", categoriaActual) // Pasamos la categoría para mostrarla en el chat
        }

        startActivity(intent)
        Toast.makeText(this, "Iniciando servicio de $categoriaActual con ${socio.nombre}", Toast.LENGTH_SHORT).show()
    }
}