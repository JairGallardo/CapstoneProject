package com.example.domingo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.io.File

class VerificarSocioActivity : AppCompatActivity() {

    private lateinit var photoUri: Uri
    private var imagenPerfil: Uri? = null
    private var imagenDniFrontal: Uri? = null
    private var imagenDniTrasero: Uri? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) abrirCamara() else Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show()
    }

    private val camaraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            imagenPerfil = photoUri
            findViewById<ImageView>(R.id.ivPreviewFoto)?.setImageURI(photoUri)
            validarFormulario()
        }
    }

    private val dniFrontalLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { imagenDniFrontal = it; validarFormulario() }
    }

    private val dniTraseroLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { imagenDniTrasero = it; validarFormulario() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verificar_socio)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Verificación de Identidad"

        findViewById<Button>(R.id.btnCamaraPerfil)?.setOnClickListener { verificarPermisoYCamara() }
        findViewById<Button>(R.id.btnDniFrontal)?.setOnClickListener { dniFrontalLauncher.launch("image/*") }
        findViewById<Button>(R.id.btnDniTrasero)?.setOnClickListener { dniTraseroLauncher.launch("image/*") }

        findViewById<CheckBox>(R.id.cbDeclaracion)?.setOnCheckedChangeListener { _, _ -> validarFormulario() }

        findViewById<Button>(R.id.btnEnviarRevision)?.setOnClickListener {
            procesarYEnviarBase64()
        }
    }
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun procesarYEnviarBase64() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val btnEnviar = findViewById<Button>(R.id.btnEnviarRevision)

        btnEnviar.isEnabled = false
        btnEnviar.text = "Procesando documentos..."

        Thread {
            val perfilB64 = imagenPerfil?.let { convertirUriABase64(it) }
            val frontalB64 = imagenDniFrontal?.let { convertirUriABase64(it) }
            val traseroB64 = imagenDniTrasero?.let { convertirUriABase64(it) }

            runOnUiThread {
                if (perfilB64 != null && frontalB64 != null && traseroB64 != null) {
                    val datos = mapOf(
                        "fotoPerfilB64" to perfilB64,
                        "dniFrontalB64" to frontalB64,
                        "dniTraseroB64" to traseroB64,
                        "verificado" to "en_revision",
                        "fechaSolicitud" to Timestamp.now()
                    )

                    FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                        .update(datos)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Documentos enviados. Espera la verificación.", Toast.LENGTH_LONG).show()
                            FirebaseAuth.getInstance().signOut()
                            finish()
                        }
                        .addOnFailureListener {
                            btnEnviar.isEnabled = true
                            btnEnviar.text = "Reintentar"
                            Toast.makeText(this, "Error al subir documentos", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }.start()
    }

    private fun validarFormulario() {
        val btnEnviar = findViewById<Button>(R.id.btnEnviarRevision)
        val cb = findViewById<CheckBox>(R.id.cbDeclaracion)
        val fotosListas = imagenPerfil != null && imagenDniFrontal != null && imagenDniTrasero != null
        btnEnviar?.isEnabled = fotosListas && cb?.isChecked == true
    }

    private fun convertirUriABase64(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val escalaMax = 480
            val ratio = bitmap.height.toFloat() / bitmap.width.toFloat()
            val bitmapReducido = Bitmap.createScaledBitmap(bitmap, escalaMax, (escalaMax * ratio).toInt(), false)
            val outputStream = ByteArrayOutputStream()
            bitmapReducido.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) { null }
    }

    private fun verificarPermisoYCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) abrirCamara()
        else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun abrirCamara() {
        val file = File(filesDir, "temp_perfil.jpg")
        photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        camaraLauncher.launch(photoUri)
    }
}