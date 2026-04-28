package com.example.domingo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

// ... tus imports actuales ...
import com.google.firebase.firestore.FirebaseFirestore

class FinalizarServicioActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finalizar_servicio)

        val btnWhatsapp = findViewById<Button>(R.id.btnAbrirWhatsapp)
        val btnFinalizar = findViewById<Button>(R.id.btnFinalizarApp)

        // 1. Recibimos los datos que enviamos desde NegociacionActivity
        val trabajadorId = intent.getStringExtra("RECEPTOR_ID")
        val monto = intent.getDoubleExtra("MONTO_FINAL", 0.0)
        val metodo = intent.getStringExtra("METODO_PAGO") ?: "Efectivo"

        btnWhatsapp.setOnClickListener {
            if (trabajadorId != null) {
                // 2. Buscamos el teléfono real del trabajador en Firestore
                db.collection("usuarios").document(trabajadorId).get()
                    .addOnSuccessListener { doc ->
                        val telefonoReal = doc.getString("telefono")
                        if (!telefonoReal.isNullOrEmpty()) {
                            abrirWhatsApp(telefonoReal, monto, metodo)
                        } else {
                            Toast.makeText(this, "El trabajador no tiene teléfono registrado", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al conectar con el servidor", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        btnFinalizar.setOnClickListener {
            Toast.makeText(this, "¡Servicio finalizado con éxito!", Toast.LENGTH_LONG).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun abrirWhatsApp(telefono: String, monto: Double, metodo: String) {
        try {
            // Limpiamos el teléfono por si tiene espacios o el "+"
            val telfLimpio = telefono.replace(" ", "").replace("+", "")
            // Aseguramos el prefijo de Perú (51) si el número tiene 9 dígitos
            val destino = if (telfLimpio.length == 9) "51$telfLimpio" else telfLimpio

            val mensaje = "Hola! Te contacto desde DominGO. Confirmamos el servicio por S/ $monto pagado vía $metodo."

            val intent = Intent(Intent.ACTION_VIEW)
            val url = "https://api.whatsapp.com/send?phone=$destino&text=${android.net.Uri.encode(mensaje)}"
            intent.data = android.net.Uri.parse(url)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp no instalado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compartirVoucher(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartir Voucher de DominGO"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}