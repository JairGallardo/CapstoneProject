package com.example.domingo.ui

import android.app.AlertDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.domingo.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TicketClienteActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var chatId: String? = null
    private var montoServicio: Double = 0.0
    private var trabajadorId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ticket_cliente)

        chatId = intent.getStringExtra("CHAT_ID")
        montoServicio = intent.getDoubleExtra("MONTO_SERVICIO", 0.0)
        trabajadorId = intent.getStringExtra("TRABAJADOR_ID")
        val socioNombre = intent.getStringExtra("SOCIO_NOMBRE") ?: "Trabajador"

        findViewById<TextView>(R.id.tvMontoServicio).text = "S/ ${String.format("%.2f", montoServicio)}"
        findViewById<TextView>(R.id.tvTrabajador).text = socioNombre

        findViewById<EditText>(R.id.etEmailCliente).isEnabled = true

        cargarDatosCliente()

        findViewById<Button>(R.id.btnConfirmarTicket).setOnClickListener {
            guardarTicketDirecto()
        }
    }

    private fun guardarTicketDirecto() {
        val direccion = findViewById<EditText>(R.id.etDireccion).text.toString().trim()
        val telefono = findViewById<EditText>(R.id.etTelefonoCliente).text.toString().trim()

        if (direccion.isEmpty() || telefono.isEmpty()) {
            Toast.makeText(this, "Completa dirección y teléfono", Toast.LENGTH_SHORT).show()
            return
        }
        guardarTicketYEnviarChat("PENDIENTE_AL_FINAL")
    }

    private fun cargarDatosCliente() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { doc ->
                findViewById<EditText>(R.id.etNombreCliente).setText(doc.getString("nombre") ?: "")
                findViewById<EditText>(R.id.etApellidoCliente).setText(doc.getString("apellido") ?: "")
                findViewById<EditText>(R.id.etEmailCliente).setText(doc.getString("email") ?: "")
                findViewById<EditText>(R.id.etTelefonoCliente).setText(doc.getString("telefono") ?: "")
            }
    }

    private fun guardarTicketYEnviarChat(metodoPago: String) {
        val direccion = findViewById<EditText>(R.id.etDireccion).text.toString().trim()
        val telefono = findViewById<EditText>(R.id.etTelefonoCliente).text.toString().trim()
        val referencias = findViewById<EditText>(R.id.etReferencias).text.toString().trim()
        val email = findViewById<EditText>(R.id.etEmailCliente).text.toString().trim()

        val distanciaApprox = 5.2
        val costoTotal = montoServicio + (distanciaApprox * 2.0)

        val ticketData = hashMapOf(
            "chatId" to chatId,
            "clienteId" to auth.currentUser?.uid,
            "trabajadorId" to trabajadorId,
            "montoServicio" to montoServicio,
            "distanciaApprox" to distanciaApprox,
            "costoTotal" to costoTotal,
            "direccion" to direccion,
            "telefono" to telefono,
            "email" to email,
            "referencias" to referencias,
            "metodoPago" to metodoPago,
            "estado" to "TRABAJO_EN_CURSO",
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("tickets").add(ticketData)
            .addOnSuccessListener { ticketDoc ->
                enviarTicketAlChat(ticketDoc.id, direccion, costoTotal, metodoPago)
                Toast.makeText(this, "Servicio iniciado.", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun enviarTicketAlChat(ticketId: String, direccion: String, costoTotal: Double, metodoPago: String) {
        val uid = auth.currentUser?.uid ?: return

        val mensajeTicket = """
            ╔══════════════════════╗
               TICKET DE SERVICIO
            ╚══════════════════════╝
            
            RESUMEN DEL PAGO
            --------------------------------------
            Total a pagar: S/ ${String.format("%.2f", costoTotal)}
            Método: $metodoPago
            
            DETALLES DE ENTREGA
            --------------------------------------
            Dirección: $direccion
            Teléfono: ${findViewById<EditText>(R.id.etTelefonoCliente).text}
            Ref: ${findViewById<EditText>(R.id.etReferencias).text}
            
            --------------------------------------
            ID: $ticketId
            ¡El servicio ha comenzado!
        """.trimIndent()

        val mensajeData = hashMapOf(
            "emisorId" to uid,
            "contenido" to mensajeTicket,
            "tipo" to "TICKET",
            "timestamp" to System.currentTimeMillis(),
            "ticketId" to ticketId,
            "montoOferta" to costoTotal
        )

        db.collection("negociaciones").document(chatId!!).collection("mensajes").add(mensajeData)
    }
}