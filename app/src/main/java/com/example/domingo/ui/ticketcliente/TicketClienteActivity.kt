package com.example.domingo.ui.ticketcliente

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.domingo.R
import java.util.Locale

class TicketClienteActivity : AppCompatActivity() {

    private val viewModel: TicketClienteViewModel by viewModels()
    private var chatId: String? = null
    private var montoServicio: Double = 0.0
    private var trabajadorId: String? = null
    private lateinit var etNombre: EditText
    private lateinit var etApellido: EditText
    private lateinit var etEmail: EditText
    private lateinit var etTelefono: EditText
    private lateinit var etDireccion: EditText
    private lateinit var etReferencias: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ticket_cliente)

        chatId = intent.getStringExtra("CHAT_ID")
        montoServicio = intent.getDoubleExtra("MONTO_SERVICIO", 0.0)
        trabajadorId = intent.getStringExtra("TRABAJADOR_ID")
        val socioNombre = intent.getStringExtra("SOCIO_NOMBRE") ?: "Trabajador"

        etNombre = findViewById(R.id.etNombreCliente)
        etApellido = findViewById(R.id.etApellidoCliente)
        etEmail = findViewById(R.id.etEmailCliente)
        etTelefono = findViewById(R.id.etTelefonoCliente)
        etDireccion = findViewById(R.id.etDireccion)
        etReferencias = findViewById(R.id.etReferencias)

        findViewById<TextView>(R.id.tvMontoServicio).text = String.format(Locale.getDefault(), "S/ %.2f", montoServicio)
        findViewById<TextView>(R.id.tvTrabajador).text = socioNombre

        configurarObservadores()

        viewModel.cargarDatosCliente()

        findViewById<Button>(R.id.btnConfirmarTicket).setOnClickListener {
            viewModel.procesarTicket(
                chatId = chatId,
                trabajadorId = trabajadorId,
                montoServicio = montoServicio,
                direccion = etDireccion.text.toString().trim(),
                telefono = etTelefono.text.toString().trim(),
                referencias = etReferencias.text.toString().trim(),
                email = etEmail.text.toString().trim()
            )
        }
    }

    private fun configurarObservadores() {
        viewModel.datosCliente.observe(this) { doc ->
            etNombre.setText(doc.getString("nombre") ?: "")
            etApellido.setText(doc.getString("apellido") ?: "")
            etEmail.setText(doc.getString("email") ?: "")
            etTelefono.setText(doc.getString("telefono") ?: "")
        }

        viewModel.operacionExitosa.observe(this) { exitoso ->
            if (exitoso) {
                Toast.makeText(this, "Servicio iniciado.", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        viewModel.mensajeError.observe(this) { mensaje ->
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        }
    }
}