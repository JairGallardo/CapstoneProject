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
    private val onOfertaClick: (Mensaje, String) -> Unit,
    private val onMensajeLongClick: (Mensaje) -> Unit,
    private val onFinalizarClick: (Mensaje) -> Unit,
    private val onPagarClick: (Mensaje) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var mensajes = mutableListOf<Mensaje>()

    private val TYPE_EMISOR = 1
    private val TYPE_RECEPTOR = 2
    private val TYPE_OFERTA = 3
    private val TYPE_TICKET = 4
    private val TYPE_SISTEMA_ACCION = 5

    fun actualizarMensajes(nuevaLista: List<Mensaje>) {
        mensajes.clear()
        mensajes.addAll(nuevaLista)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val mensaje = mensajes[position]
        return when (mensaje.tipo) {
            "OFERTA" -> TYPE_OFERTA
            "TICKET" -> TYPE_TICKET
            "FIN_TRABAJO", "PAGO_REALIZADO" -> TYPE_SISTEMA_ACCION
            else -> if (mensaje.emisorId == userId) TYPE_EMISOR else TYPE_RECEPTOR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_EMISOR -> TextoViewHolder(inflater.inflate(R.layout.item_chat_texto_emisor, parent, false))
            TYPE_RECEPTOR -> TextoViewHolder(inflater.inflate(R.layout.item_chat_texto_receptor, parent, false))
            TYPE_OFERTA -> OfertaViewHolder(inflater.inflate(R.layout.item_chat_oferta, parent, false))
            TYPE_TICKET -> TicketViewHolder(inflater.inflate(R.layout.item_chat_oferta, parent, false))
            TYPE_SISTEMA_ACCION -> AccionViewHolder(inflater.inflate(R.layout.item_chat_oferta, parent, false))
            else -> TextoViewHolder(inflater.inflate(R.layout.item_chat_texto_emisor, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val mensaje = mensajes[position]

        holder.itemView.setOnLongClickListener {
            onMensajeLongClick(mensaje)
            true
        }

        when (holder) {
            is TextoViewHolder -> {
                holder.tvMensaje.text = mensaje.contenido
            }
            is TicketViewHolder -> {
                holder.tvTitulo.visibility = View.GONE
                holder.tvMonto.visibility = View.GONE
                holder.btnOculto.visibility = View.GONE

                holder.tvEstado.text = mensaje.contenido
                holder.tvEstado.visibility = View.VISIBLE

                if (esTrabajador) {
                    holder.btnFinalizar.text = "TERMINAR TRABAJO"
                    holder.btnFinalizar.visibility = View.VISIBLE
                    holder.btnFinalizar.setOnClickListener { onFinalizarClick(mensaje) }
                } else {
                    holder.btnFinalizar.visibility = View.GONE
                }
            }

            is OfertaViewHolder -> {
                holder.tvTitulo.visibility = View.VISIBLE
                holder.tvMonto.visibility = View.VISIBLE
                holder.tvMonto.text = String.format("S/ %.2f", mensaje.montoOferta)

                if (mensaje.estadoOferta == "PENDIENTE") {
                    holder.tvEstado.visibility = View.GONE
                    if (mensaje.emisorId != userId) {
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
                    holder.tvEstado.text = "Oferta ${mensaje.estadoOferta}"
                }
                holder.btnAceptar.setOnClickListener { onOfertaClick(mensaje, "ACEPTADO") }
                holder.btnRechazar.setOnClickListener { onOfertaClick(mensaje, "RECHAZADO") }
            }

            is AccionViewHolder -> {
                holder.tvTitulo.visibility = View.GONE
                holder.tvMonto.visibility = View.GONE
                holder.btnRechazar.visibility = View.GONE

                if (mensaje.tipo == "FIN_TRABAJO") {
                    holder.tvEstado.text = "¡TRABAJO TERMINADO!"
                    holder.tvEstado.visibility = View.VISIBLE

                    if (!esTrabajador) {
                        holder.btnAceptar.text = "PAGAR SERVICIO"
                        holder.btnAceptar.visibility = View.VISIBLE
                        holder.btnAceptar.setOnClickListener { onPagarClick(mensaje) }
                    } else {
                        holder.btnAceptar.visibility = View.GONE
                    }
                } else if (mensaje.tipo == "PAGO_REALIZADO") {
                    if (esTrabajador && mensaje.estadoOferta == "PENDIENTE") {
                        holder.tvEstado.text = "EL CLIENTE DICE QUE YA PAGÓ"
                        holder.tvEstado.visibility = View.VISIBLE
                        holder.btnAceptar.text = "CONFIRMAR RECEPCIÓN"
                        holder.btnAceptar.visibility = View.VISIBLE
                        holder.btnAceptar.setOnClickListener {
                            onOfertaClick(mensaje, "PAGO_CONFIRMADO")
                        }
                    } else if (mensaje.estadoOferta == "PAGO_CONFIRMADO") {
                        holder.tvEstado.text = "✅ PAGO CONFIRMADO"
                        holder.tvEstado.visibility = View.VISIBLE
                        holder.btnAceptar.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = mensajes.size

    class TextoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMensaje: TextView = view.findViewById(R.id.tvMensajeChat)
    }

    class OfertaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tvTituloOferta)
        val tvMonto: TextView = view.findViewById(R.id.tvMontoOferta)
        val btnAceptar: Button = view.findViewById(R.id.btnAceptarOferta)
        val btnRechazar: Button = view.findViewById(R.id.btnRechazarOferta)
        val tvEstado: TextView = view.findViewById(R.id.tvEstadoOferta)
    }

    class TicketViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tvTituloOferta)
        val tvMonto: TextView = view.findViewById(R.id.tvMontoOferta)
        val btnFinalizar: Button = view.findViewById(R.id.btnAceptarOferta)
        val btnOculto: Button = view.findViewById(R.id.btnRechazarOferta)
        val tvEstado: TextView = view.findViewById(R.id.tvEstadoOferta)
    }

    class AccionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tvTituloOferta)
        val tvMonto: TextView = view.findViewById(R.id.tvMontoOferta)
        val btnAceptar: Button = view.findViewById(R.id.btnAceptarOferta)
        val btnRechazar: Button = view.findViewById(R.id.btnRechazarOferta)
        val tvEstado: TextView = view.findViewById(R.id.tvEstadoOferta)
    }
}