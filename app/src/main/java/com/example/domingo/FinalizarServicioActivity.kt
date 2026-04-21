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

class FinalizarServicioActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finalizar_servicio)

        val btnWhatsapp = findViewById<Button>(R.id.btnAbrirWhatsapp)
        val btnFinalizar = findViewById<Button>(R.id.btnFinalizarApp)

        // Supongamos que el número del trabajador viene de la pantalla anterior
        val numeroTelefono = "51900000000" // Formato: CodigoPais + Numero (Ej: 51 para Perú)
        val mensaje = "Hola, te contacto desde DominGO para coordinar el servicio."

        btnWhatsapp.setOnClickListener {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                val url = "https://api.whatsapp.com/send?phone=$numeroTelefono&text=${android.net.Uri.encode(mensaje)}"
                intent.data = android.net.Uri.parse(url)
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "WhatsApp no instalado", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        btnFinalizar.setOnClickListener {
            android.widget.Toast.makeText(this, "¡Gracias por tu calificación!", android.widget.Toast.LENGTH_LONG).show()

            val intent = android.content.Intent(this, MainActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
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