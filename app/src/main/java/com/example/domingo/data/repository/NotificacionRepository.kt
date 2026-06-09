package com.example.domingo.data.repository

import com.example.domingo.model.Notificacion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

class NotificacionRepository {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid(): String? = auth.currentUser?.uid

    fun escucharNotificaciones(
        onDatos: (List<Notificacion>) -> Unit
    ): ListenerRegistration? {
        val uid = uid() ?: return null
        return db.collection("usuarios").document(uid)
            .collection("notificaciones")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                val lista = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Notificacion::class.java)?.copy(id = doc.id)
                }
                onDatos(lista)
            }
    }

    fun contarNoLeidas(onResultado: (Int) -> Unit) {
        val uid = uid() ?: run { onResultado(0); return }
        db.collection("usuarios").document(uid)
            .collection("notificaciones")
            .whereEqualTo("leida", false)
            .get()
            .addOnSuccessListener { onResultado(it.size()) }
            .addOnFailureListener  { onResultado(0) }
    }

    fun marcarComoLeida(notifId: String) {
        val uid = uid() ?: return
        db.collection("usuarios").document(uid)
            .collection("notificaciones").document(notifId)
            .update("leida", true)
    }

    fun marcarTodasComoLeidas(onCompletado: () -> Unit) {
        val uid = uid() ?: run { onCompletado(); return }
        db.collection("usuarios").document(uid)
            .collection("notificaciones")
            .whereEqualTo("leida", false)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                for (doc in snapshot.documents) {
                    batch.update(doc.reference, "leida", true)
                }
                batch.commit().addOnSuccessListener { onCompletado() }
            }
    }

    fun crearNotificacion(
        destinatarioUid: String,
        titulo: String,
        cuerpo: String,
        tipo: String,
        accionUrl: String = ""
    ) {
        val notif = hashMapOf(
            "titulo"     to titulo,
            "cuerpo"     to cuerpo,
            "tipo"       to tipo,
            "timestamp"  to System.currentTimeMillis(),
            "leida"      to false,
            "accionUrl"  to accionUrl
        )
        db.collection("usuarios").document(destinatarioUid)
            .collection("notificaciones")
            .add(notif)
    }

    fun verificarYAvisarRating(uid: String) {
        val usuarioRef = db.collection("usuarios").document(uid)
        usuarioRef.get().addOnSuccessListener { doc ->
            val rating = doc.getDouble("rating") ?: return@addOnSuccessListener

            when {
                rating <= 1.5 -> {
                    // Bloquear cuenta
                    usuarioRef.update("cuentaBloqueada", true)
                    crearNotificacion(
                        destinatarioUid = uid,
                        titulo = "🔒 Cuenta suspendida",
                        cuerpo = "Tu cuenta ha sido suspendida por acumular demasiadas penalizaciones (rating: ${"%.1f".format(rating)}). Contacta a atención al cliente para apelar.",
                        tipo   = "BLOQUEO"
                    )
                }
                rating <= 2.0 -> {
                    crearNotificacion(
                        destinatarioUid = uid,
                        titulo = "🚨 Advertencia: suspensión inminente",
                        cuerpo = "Tu calificación bajó a ${"%.1f".format(rating)}/5. Si continúa bajando, tu cuenta será suspendida. Contacta atención al cliente si crees que fue un error.",
                        tipo   = "AVISO_RATING"
                    )
                }
                rating <= 3.0 -> {
                    crearNotificacion(
                        destinatarioUid = uid,
                        titulo = "⚠️ Tu calificación está en observación",
                        cuerpo = "Tu calificación actual es ${"%.1f".format(rating)}/5. Las cuentas por debajo de 3 estrellas están bajo revisión y pueden ser suspendidas. Sigue brindando un buen servicio para recuperarla.",
                        tipo   = "AVISO_RATING"
                    )
                }
            }
        }
    }

    fun enviarTicketSoporte(
        uid: String,
        motivo: String,
        descripcion: String,
        idTicketRelacionado: String,
        onExito: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val ticket = hashMapOf(
            "uid"                  to uid,
            "motivo"               to motivo,
            "descripcion"          to descripcion,
            "idTicketRelacionado"  to idTicketRelacionado,
            "timestamp"            to System.currentTimeMillis(),
            "estado"               to "PENDIENTE"
        )
        db.collection("soporte_tickets").add(ticket)
            .addOnSuccessListener { onExito() }
            .addOnFailureListener { onError(it) }
    }
}