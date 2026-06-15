package com.example.domingo.ui.finalizarservicio

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.domingo.R
import com.example.domingo.ui.main.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FinalizarServicioActivity : AppCompatActivity() {

    private val viewModel: FinalizarServicioViewModel by viewModels()
    private var montoFinal: Double = 0.0

    private var clienteId: String? = null
    private var trabajadorId: String? = null
    private var miRol: String = "cliente"
    private var nombreOtraPersona: String = "Socio"

    private var miNombreReal: String = "Usuario DominGO"

    private var categoriaServicio: String = "Servicio"
    private var metodoPago: String = "Efectivo"
    private var chatId: String? = null

    private lateinit var btnTerminar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finalizar_servicio)

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId != null) {
            FirebaseFirestore.getInstance().collection("usuarios").document(currentUserId)
                .get()
                .addOnSuccessListener { doc ->
                    miNombreReal = doc.getString("nombre") ?: doc.getString("nombreCompleto") ?: "Usuario DominGO"
                }
        }

        montoFinal = intent.getDoubleExtra("MONTO_FINAL", 0.0)
        clienteId = intent.getStringExtra("CLIENTE_ID")
        trabajadorId = intent.getStringExtra("TRABAJADOR_ID")
        miRol = intent.getStringExtra("MI_ROL") ?: "cliente"
        nombreOtraPersona = intent.getStringExtra("SOCIO_NOMBRE") ?: "Socio"
        categoriaServicio = intent.getStringExtra("CATEGORIA_SERVICIO") ?: "Servicio"
        metodoPago = intent.getStringExtra("METODO_PAGO") ?: "Efectivo"
        chatId = intent.getStringExtra("CHAT_ID")

        val tvMonto = findViewById<TextView>(R.id.tvMontoFinal)
        tvMonto.text = "Total: S/ ${String.format(Locale.getDefault(), "%.2f", montoFinal)}"

        val rbCalificacion = findViewById<RatingBar>(R.id.rbCalificacion)
        val etComentario = findViewById<EditText>(R.id.etComentarioFeedback)
        val btnVoucher = findViewById<Button>(R.id.btnDescargarVoucher)
        btnTerminar = findViewById(R.id.btnTerminarVolver)

        etComentario.hint = "¿Cómo fue tu experiencia con $nombreOtraPersona?"

        configurarObservadores()

        btnVoucher.setOnClickListener {
            generarPDFComprobante(montoFinal, nombreOtraPersona, metodoPago)
        }

        btnTerminar.setOnClickListener {
            val calificacion = rbCalificacion.rating
            val comentario = etComentario.text.toString().trim()

            if (calificacion > 0) {
                btnTerminar.isEnabled = false

                viewModel.guardarFeedbackYFinalizar(
                    chatId = chatId ?: "",
                    trabajadorId = trabajadorId,
                    clienteIdChat = clienteId,
                    miRol = miRol,
                    nombreOtraPersona = nombreOtraPersona,
                    categoriaServicio = categoriaServicio,
                    montoFinal = montoFinal,
                    nota = calificacion,
                    comentario = comentario
                )
            } else {
                Toast.makeText(this, "Por favor, califica el servicio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configurarObservadores() {
        viewModel.operacionExitosa.observe(this) { exitoso ->
            if (exitoso) {
                Toast.makeText(this, "¡Servicio Finalizado con éxito!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }

        viewModel.mensajeError.observe(this) { mensaje ->

            if(mensaje.contains("falló remover el chat activo")) {
                Toast.makeText(this, "¡Servicio Finalizado con éxito!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                btnTerminar.isEnabled = true
                Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generarPDFComprobante(monto: Double, socioNombrePDF: String, metodo: String) {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val titlePaint = Paint()

        val pageInfo = PdfDocument.PageInfo.Builder(350, 650, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        titlePaint.textAlign = Paint.Align.CENTER
        titlePaint.textSize = 20f
        titlePaint.color = Color.parseColor("#0D47A1")
        titlePaint.isFakeBoldText = true
        canvas.drawText("Comprobante DominGO", 175f, 60f, titlePaint)

        paint.textSize = 35f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        canvas.drawText("S/ ${String.format(Locale.getDefault(), "%.2f", monto)}", 175f, 120f, paint)

        paint.textSize = 14f
        paint.textAlign = Paint.Align.LEFT
        var currentY = 220f
        val marginLabel = 40f
        val marginValue = 150f

        val rolDestino = if (miRol == "cliente") "Trabajador:" else "Cliente:"
        val rolOrigen = if (miRol == "cliente") "Cliente:" else "Trabajador:"

        val items = listOf(
            rolDestino to socioNombrePDF,
            rolOrigen to miNombreReal,
            "Método:" to metodo,
            "Fecha:" to SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
            "Operación:" to System.currentTimeMillis().toString().takeLast(8)
        )

        for (item in items) {
            paint.isFakeBoldText = true
            paint.color = Color.BLACK
            canvas.drawText(item.first, marginLabel, currentY, paint)

            paint.isFakeBoldText = false
            paint.color = Color.DKGRAY
            canvas.drawText(item.second, marginValue, currentY, paint)

            currentY += 45f
        }

        canvas.drawLine(40f, currentY, 310f, currentY, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12f
        paint.color = Color.GRAY
        canvas.drawText("Este es un comprobante oficial de la app DominGO", 175f, currentY + 50f, paint)

        pdfDocument.finishPage(page)

        val nombreArchivo = "Voucher_DominGO_${System.currentTimeMillis()}.pdf"
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), nombreArchivo)

        try {
            pdfDocument.writeTo(FileOutputStream(file))
            Toast.makeText(this, "Voucher guardado en descargas", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al guardar PDF", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }
}