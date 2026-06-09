package com.example.domingo.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.domingo.R
import com.example.domingo.ui.negociacion.NegociacionActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class DomingoFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID   = "domingo_chat_channel"
        const val CHANNEL_NAME = "Chat Domingo"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val titulo       = remoteMessage.notification?.title ?: remoteMessage.data["titulo"] ?: "Domingo"
        val cuerpo       = remoteMessage.notification?.body  ?: remoteMessage.data["cuerpo"] ?: ""
        val chatId       = remoteMessage.data["chatId"]       ?: return
        val esTrabajador = remoteMessage.data["esTrabajador"]?.toBoolean() ?: false
        val nombreSocio  = remoteMessage.data["nombreSocio"]  ?: "Socio"

        crearCanalSiNecesario()
        mostrarNotificacion(titulo, cuerpo, chatId, esTrabajador, nombreSocio)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("usuarios")
            .document(uid)
            .set(mapOf("fcmToken" to token), SetOptions.merge())
    }

    private fun crearCanalSiNecesario() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de chat y servicio"
                enableVibration(true)
                enableLights(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun mostrarNotificacion(
        titulo: String,
        cuerpo: String,
        chatId: String,
        esTrabajador: Boolean,
        nombreSocio: String
    ) {
        val intent = Intent(this, NegociacionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("CHAT_ID",       chatId)
            putExtra("ES_TRABAJADOR", esTrabajador)
            putExtra("SOCIO_NOMBRE",  nombreSocio)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sonido = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setAutoCancel(true)
            .setSound(sonido)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(chatId.hashCode(), notification)
    }
}