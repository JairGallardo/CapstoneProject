package com.example.domingo.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.domingo.R
import com.example.domingo.ui.MainActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.bumptech.glide.Glide

class FinalizarServicioActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finalizar_servicio)

        val ticketId = intent.getStringExtra("TICKET_ID")
        val trabajadorId = intent.getStringExtra("RECEPTOR_ID")
        val chatId = intent.getStringExtra("CHAT_ID")
        val costoTotal = intent.getDoubleExtra("COSTO_TOTAL", 0.0)
        val metodoPago = intent.getStringExtra("METODO_PAGO") ?: "Efectivo"
        val direccion = intent.getStringExtra("DIRECCION") ?: ""

        val btnWhatsapp = findViewById<Button>(R.id.btnAbrirWhatsapp)
        val btnFinalizar = findViewById<Button>(R.id.btnFinalizarApp)
        val ratingBar = findViewById<RatingBar>(R.id.ratingBar)

        findViewById<TextView>(R.id.tvGracias).text = "¡Servicio Confirmado!\nTotal: S/ ${String.format("%.2f", costoTotal)}"

        if (trabajadorId != null) {
            cargarDatosYapeTrabajador(trabajadorId, btnWhatsapp)
        }

        btnWhatsapp.setOnClickListener {
            mostrarMetodosPago(costoTotal, trabajadorId!!, btnWhatsapp)
        }

        btnFinalizar.setOnClickListener {
            finalizarServicioConRating(ticketId, chatId, ratingBar.rating.toInt())
        }
    }

    private fun cargarDatosYapeTrabajador(trabajadorId: String, btnWhatsapp: Button) {
        db.collection("usuarios").document(trabajadorId).get()
            .addOnSuccessListener { doc ->
                val nombreYape = doc.getString("yapeNombre") ?: "No configurado"
                val numeroYape = doc.getString("yapeNumero") ?: "No configurado"
                val qrUrl = doc.getString("yapeQrUrl")

                btnWhatsapp.text = "Yape: $nombreYape\n$numeroYape"
                btnWhatsapp.isAllCaps = false

                qrUrl?.let { url ->
                    Toast.makeText(this, "QR cargado: $url", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error cargando Yape del trabajador", Toast.LENGTH_SHORT).show()
            }
    }

    private fun mostrarMetodosPago(monto: Double, trabajadorId: String, btnWhatsapp: Button) {
        val view = layoutInflater.inflate(R.layout.layout_metodo_pago, null)
        val rgMetodos = view.findViewById<RadioGroup>(R.id.rgMetodosPago)
        val btnConfirmar = view.findViewById<Button>(R.id.btnConfirmarPago)

        AlertDialog.Builder(this)
            .setTitle("Realiza el pago (S/ ${String.format("%.2f", monto)})")
            .setView(view)
            .setNegativeButton("Cancelar", null)
            .show()

        btnConfirmar.setOnClickListener {
            val metodoSeleccionado = when (rgMetodos.checkedRadioButtonId) {
                R.id.rbYape -> "Yape"
                R.id.rbPlin -> "Plin"
                R.id.rbEfectivo -> "Efectivo"
                else -> "Efectivo"
            }

            val updates = mapOf(
                "metodoPago" to metodoSeleccionado,
                "estado" to "PAGADO"
            )

            intent.getStringExtra("TICKET_ID")?.let { ticketId ->
                db.collection("tickets").document(ticketId).update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Pago registrado: $metodoSeleccionado", Toast.LENGTH_SHORT).show()
                        btnWhatsapp.text = "PAGO $metodoSeleccionado REGISTRADO"
                    }
            }
        }
    }

    private fun finalizarServicioConRating(ticketId: String?, chatId: String?, estrellas: Int) {
        if (ticketId != null && chatId != null) {
            val updatesNegociacion = mapOf(
                "estado" to "FINALIZADO",
                "activo" to false,
                "estrellasCliente" to estrellas
            )

            val updatesTicket = mapOf(
                "estado" to "COMPLETADO",
                "estrellasCliente" to estrellas
            )

            db.collection("negociaciones").document(chatId).update(updatesNegociacion)

            db.collection("tickets").document(ticketId).update(updatesTicket)
                .addOnSuccessListener {
                    db.collection("negociaciones").document(chatId)
                        .collection("mensajes").get()
                        .addOnSuccessListener { snapshot ->
                            val batch = db.batch()
                            for (doc in snapshot) {
                                batch.delete(doc.reference)
                            }
                            batch.commit().addOnSuccessListener {
                                Toast.makeText(this, "¡Gracias por usar DominGO! ⭐$estrellas", Toast.LENGTH_LONG).show()

                                val intent = Intent(this, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                        }
                }
        }
    }
}