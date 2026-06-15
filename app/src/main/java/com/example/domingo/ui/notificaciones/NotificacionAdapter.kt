package com.example.domingo.ui.notificaciones

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.R
import com.example.domingo.model.Notificacion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificacionAdapter(
    private val lista: MutableList<Notificacion>,
    private val onClick: (Notificacion) -> Unit
) : RecyclerView.Adapter<NotificacionAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo    : TextView = view.findViewById(R.id.tvNotifTitulo)
        val tvCuerpo    : TextView = view.findViewById(R.id.tvNotifCuerpo)
        val tvTimestamp : TextView = view.findViewById(R.id.tvNotifTimestamp)
        val indicadorNo : View     = view.findViewById(R.id.viewIndicadorNoLeida)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notificacion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notif = lista[position]

        holder.tvTitulo.text    = notif.titulo
        holder.tvCuerpo.text    = notif.cuerpo
        holder.tvTimestamp.text = formatearTiempo(notif.timestamp)

        val pesoTitulo = if (!notif.leida) Typeface.BOLD else Typeface.NORMAL
        holder.tvTitulo.setTypeface(null, pesoTitulo)
        holder.indicadorNo.visibility = if (!notif.leida) View.VISIBLE else View.INVISIBLE

        val colorFondo = when (notif.tipo) {
            "BLOQUEO" -> Color.parseColor("#FFEBEE")
            "AVISO_RATING" -> {
                if (notif.cuerpo.contains("suspensión", ignoreCase = true) ||
                    notif.cuerpo.contains("suspendida", ignoreCase = true)) {
                    Color.parseColor("#FFF3E0")
                } else {
                    Color.parseColor("#FFFDE7")
                }
            }
            else -> Color.TRANSPARENT
        }
        holder.itemView.setBackgroundColor(colorFondo)

        holder.itemView.setOnClickListener { onClick(notif) }
    }

    override fun getItemCount() = lista.size

    fun actualizarLista(nuevaLista: List<Notificacion>) {
        lista.clear()
        lista.addAll(nuevaLista)
        notifyDataSetChanged()
    }

    private fun formatearTiempo(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val ahora      = System.currentTimeMillis()
        val diferencia = ahora - timestamp

        return when {
            diferencia < 60_000L      -> "Ahora"
            diferencia < 3_600_000L   -> "${diferencia / 60_000} min"
            diferencia < 86_400_000L  -> "${diferencia / 3_600_000} h"
            diferencia < 604_800_000L -> "${diferencia / 86_400_000} d"
            else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
        }
    }
}