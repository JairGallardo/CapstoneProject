package com.example.domingo.ui

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
import androidx.appcompat.app.AppCompatActivity
import com.example.domingo.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FinalizarServicioActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finalizar_servicio)

        val montoFinal = intent.getDoubleExtra("MONTO_FINAL", 0.0)
        val receptorId = intent.getStringExtra("RECEPTOR_ID")
        val chatId = intent.getStringExtra("CHAT_ID")

        val tvMonto = findViewById<TextView>(R.id.tvMontoFinal)
        tvMonto.text = "Total: S/ ${String.format("%.2f", montoFinal)}"

        val rbCalificacion = findViewById<RatingBar>(R.id.rbCalificacion)
        val etComentario = findViewById<EditText>(R.id.etComentarioFeedback)
        val btnVoucher = findViewById<Button>(R.id.btnDescargarVoucher)
        val btnTerminar = findViewById<Button>(R.id.btnTerminarVolver)

        btnVoucher.setOnClickListener {
            generarPDFComprobante(montoFinal, receptorId ?: "Socio DominGO", intent.getStringExtra("SOCIO_NOMBRE") ?: "Socio", intent.getStringExtra("METODO_PAGO") ?: "N/A")
        }

        btnTerminar.setOnClickListener {
            val calificacion = rbCalificacion.rating
            val comentario = etComentario.text.toString().trim()

            if (calificacion > 0) {
                guardarFeedbackYFinalizar(receptorId, calificacion, comentario)
            } else {
                Toast.makeText(this, "Por favor, califica el servicio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generarPDFComprobante(
        monto: Double,
        trabajador: String,
        metodo: String,
        string: String
    ) {
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
        canvas.drawText("S/ ${String.format("%.2f", monto)}", 175f, 120f, paint)

        paint.textSize = 14f
        paint.textAlign = Paint.Align.LEFT
        var currentY = 220f
        val marginLabel = 40f
        val marginValue = 150f

        val nombreCliente = FirebaseAuth.getInstance().currentUser?.displayName ?: "Cliente DominGO"

        val items = listOf(
            "Destino:" to trabajador,
            "Origen:" to nombreCliente,
            "Método:" to metodo,
            "Fecha:" to SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date()),
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

    private fun guardarFeedbackYFinalizar(trabajadorId: String?, nota: Float, comentario: String) {
        if (trabajadorId == null) return

        val feedback = hashMapOf(
            "clienteId" to FirebaseAuth.getInstance().currentUser?.uid,
            "puntos" to nota,
            "comentario" to comentario,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("usuarios").document(trabajadorId)
            .collection("resenas").add(feedback)
            .addOnSuccessListener {
                // 2. ACTUALIZACIÓN CRUCIAL: Sumar trabajo y actualizar rating
                val trabajadorRef = db.collection("usuarios").document(trabajadorId)

                db.runTransaction { transaction ->
                    val snapshot = transaction.get(trabajadorRef)
                    val trabajosActuales = snapshot.getLong("trabajosRealizados") ?: 0L
                    val ratingActual = snapshot.getDouble("rating") ?: 0.0

                    val nuevosTrabajos = trabajosActuales + 1
                    val nuevoRating = ((ratingActual * trabajosActuales) + nota) / nuevosTrabajos

                    transaction.update(trabajadorRef, "trabajosRealizados", nuevosTrabajos)
                    transaction.update(trabajadorRef, "rating", nuevoRating)
                }.addOnSuccessListener {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
    }
}