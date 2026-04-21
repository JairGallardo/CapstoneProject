package com.example.domingo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.model.Mensaje

class ChatAdapter(
    private val userId: String,
    private val esTrabajador: Boolean,
    private val onOfertaClick: (Mensaje, String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var mensajes = mutableListOf<Mensaje>()
    private val TYPE_EMISOR = 1
    private val TYPE_RECEPTOR = 2
    private val TYPE_OFERTA = 3

    fun actualizarMensajes(nuevaLista: List<Mensaje>) {
        mensajes.clear()
        mensajes.addAll(nuevaLista)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val mensaje = mensajes[position]
        return if (mensaje.tipo == "OFERTA") {
            TYPE_OFERTA
        } else if (mensaje.emisorId == userId) {
            TYPE_EMISOR
        } else {
            TYPE_RECEPTOR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_EMISOR -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_texto_emisor, parent, false)
                TextoViewHolder(view)
            }
            TYPE_RECEPTOR -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_texto_receptor, parent, false)
                TextoViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_oferta, parent, false)
                OfertaViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val mensaje = mensajes[position]

        if (holder is TextoViewHolder) {
            holder.tvMensaje.text = mensaje.contenido
        } else if (holder is OfertaViewHolder) {
            holder.tvMonto.text = "S/ ${String.format("%.2f", mensaje.montoOferta)}"

            // Lógica de botones de oferta
            if (mensaje.estadoOferta == "PENDIENTE") {
                holder.tvEstado.visibility = View.GONE
                // Solo el trabajador puede aceptar o rechazar si él NO envió la oferta
                if (esTrabajador && mensaje.emisorId != userId) {
                    holder.btnAceptar.visibility = View.VISIBLE
                    holder.btnRechazar.visibility = View.VISIBLE
                } else {
                    holder.btnAceptar.visibility = View.GONE
                    holder.btnRechazar.visibility = View.GONE
                }
            } else {
                holder.btnAceptar.visibility = View.GONE
                holder.btnRechazar.visibility = View.GONE
                holder.tvEstado.visibility = View.VISIBLE
                holder.tvEstado.text = "Estado: ${mensaje.estadoOferta}"
            }

            holder.btnAceptar.setOnClickListener { onOfertaClick(mensaje, "ACEPTADO") }
            holder.btnRechazar.setOnClickListener { onOfertaClick(mensaje, "RECHAZADO") }
        }
    }

    override fun getItemCount(): Int = mensajes.size

    class TextoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMensaje: TextView = view.findViewById(R.id.tvMensajeChat)
    }

    class OfertaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMonto: TextView = view.findViewById(R.id.tvMontoOferta)
        val btnAceptar: Button = view.findViewById(R.id.btnAceptarOferta)
        val btnRechazar: Button = view.findViewById(R.id.btnRechazarOferta)
        val tvEstado: TextView = view.findViewById(R.id.tvEstadoOferta)
    }
}