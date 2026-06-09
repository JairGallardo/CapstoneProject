package com.example.domingo.ui.negociacion

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.R
import com.example.domingo.model.Mensaje

class ChatAdapter(
    private val userId: String,
    private val esTrabajador: Boolean,
    private val onOfertaClick: (Mensaje, String) -> Unit,
    private val onMensajeLongClick: (Mensaje) -> Unit,
    private val onLlegadaClick: (Mensaje) -> Unit,
    private val onFinalizarClick: (Mensaje) -> Unit,
    private val onPagarClick: (Mensaje) -> Unit,
    private val onImagenClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val _mensajes = mutableListOf<Mensaje>()
    val mensajes: List<Mensaje> get() = _mensajes

    companion object {
        private const val TYPE_EMISOR          = 1
        private const val TYPE_RECEPTOR        = 2
        private const val TYPE_OFERTA          = 3
        private const val TYPE_TICKET          = 4
        private const val TYPE_SISTEMA_ACCION  = 5
        private const val TYPE_PROPUESTA_IA    = 6
        private const val TYPE_IMAGEN_SIMPLE   = 7
        private const val TYPE_LLEGADA         = 8
    }

    fun actualizarMensajes(nuevaLista: List<Mensaje>) {
        _mensajes.clear()
        _mensajes.addAll(nuevaLista)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val m = _mensajes[position]
        return when (m.tipo) {
            "PROPUESTA_IA"                   -> TYPE_PROPUESTA_IA
            "IMAGEN"                         -> TYPE_IMAGEN_SIMPLE
            "OFERTA"                         -> TYPE_OFERTA
            "TICKET"                         -> TYPE_TICKET
            "LLEGADA_DOMICILIO"              -> TYPE_LLEGADA
            "FIN_TRABAJO", "PAGO_REALIZADO"  -> TYPE_SISTEMA_ACCION
            else -> if (m.emisorId == userId) TYPE_EMISOR else TYPE_RECEPTOR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_EMISOR         -> TextoViewHolder(inf.inflate(R.layout.item_chat_texto_emisor,    parent, false))
            TYPE_RECEPTOR       -> TextoViewHolder(inf.inflate(R.layout.item_chat_texto_receptor,  parent, false))
            TYPE_OFERTA         -> OfertaViewHolder(inf.inflate(R.layout.item_chat_oferta,         parent, false))
            TYPE_TICKET         -> TicketViewHolder(inf.inflate(R.layout.item_chat_oferta,         parent, false))
            TYPE_LLEGADA        -> LlegadaViewHolder(inf.inflate(R.layout.item_chat_oferta,        parent, false))
            TYPE_SISTEMA_ACCION -> AccionViewHolder(inf.inflate(R.layout.item_chat_oferta,         parent, false))
            TYPE_PROPUESTA_IA   -> PropuestaIAViewHolder(inf.inflate(R.layout.item_chat_imagen_ia, parent, false))
            TYPE_IMAGEN_SIMPLE  -> ImagenSimpleViewHolder(inf.inflate(R.layout.item_chat_imagen_simple, parent, false))
            else                -> TextoViewHolder(inf.inflate(R.layout.item_chat_texto_emisor,    parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val mensaje = _mensajes[position]

        holder.itemView.setOnLongClickListener {
            onMensajeLongClick(mensaje)
            true
        }

        when (holder) {
            is TextoViewHolder -> {
                holder.tvMensaje.text = mensaje.contenido
            }

            is ImagenSimpleViewHolder -> {
                if (mensaje.imagenBase64.isNotEmpty()) {
                    try {
                        val bytes = Base64.decode(mensaje.imagenBase64, Base64.DEFAULT)
                        val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        holder.ivImagen.setImageBitmap(bmp)
                        holder.ivImagen.visibility = View.VISIBLE
                        holder.ivImagen.setOnClickListener { onImagenClick(mensaje.imagenBase64) }
                    } catch (e: Exception) {
                        Log.e("CHAT_IMG", "${e.message}")
                        holder.ivImagen.visibility = View.GONE
                    }
                } else {
                    holder.ivImagen.visibility = View.GONE
                }
            }

            is PropuestaIAViewHolder -> {
                if (mensaje.imagenBase64.isNotEmpty()) {
                    try {
                        val bytes = Base64.decode(mensaje.imagenBase64, Base64.DEFAULT)
                        val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        holder.ivImagen.setImageBitmap(bmp)
                        holder.ivImagen.visibility = View.VISIBLE
                        holder.ivImagen.setOnClickListener { onImagenClick(mensaje.imagenBase64) }
                    } catch (e: Exception) {
                        holder.ivImagen.visibility = View.GONE
                    }
                } else {
                    holder.ivImagen.visibility = View.GONE
                }

                val cardEditada = holder.itemView.findViewById<androidx.cardview.widget.CardView>(R.id.cardImagenEditada)
                if (mensaje.imagenEditadaBase64.isNotEmpty()) {
                    try {
                        val bytes = Base64.decode(mensaje.imagenEditadaBase64, Base64.DEFAULT)
                        val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        holder.ivImagenEditada.setImageBitmap(bmp)
                        cardEditada.visibility            = View.VISIBLE
                        holder.ivImagenEditada.visibility = View.VISIBLE
                        holder.ivImagenEditada.setOnClickListener { onImagenClick(mensaje.imagenEditadaBase64) }
                    } catch (e: Exception) {
                        cardEditada.visibility = View.GONE
                    }
                } else {
                    cardEditada.visibility = View.GONE
                }

                val partes = mensaje.contenido.split("\n\n🤖 Propuesta técnica:\n", limit = 2)
                holder.tvDescripcion.text = partes.getOrNull(0)?.removePrefix("📋 Solicitud: ") ?: mensaje.contenido
                holder.tvRespuestaIA.text = partes.getOrNull(1) ?: ""
            }

            is LlegadaViewHolder -> {
                holder.tvTitulo.visibility = View.GONE
                holder.tvMonto.visibility  = View.GONE
                holder.btnSecundario.visibility = View.GONE
                holder.tvEstado.text       = mensaje.contenido
                holder.tvEstado.visibility = View.VISIBLE

                if (esTrabajador) {
                    holder.btnPrimario.text       = "🏁 TERMINAR TRABAJO"
                    holder.btnPrimario.visibility = View.VISIBLE
                    holder.btnPrimario.setOnClickListener { onFinalizarClick(mensaje) }
                } else {
                    holder.btnPrimario.visibility = View.GONE
                }
            }

            is TicketViewHolder -> {
                holder.tvTitulo.visibility  = View.GONE
                holder.tvMonto.visibility   = View.GONE
                holder.btnOculto.visibility = View.GONE
                holder.tvEstado.text        = mensaje.contenido
                holder.tvEstado.visibility  = View.VISIBLE

                val yaLlego = _mensajes.any { it.tipo == "LLEGADA_DOMICILIO" }
                if (esTrabajador && !yaLlego) {
                    holder.btnFinalizar.text       = "📍 Ya llegué a su domicilio"
                    holder.btnFinalizar.visibility = View.VISIBLE
                    holder.btnFinalizar.setOnClickListener { onLlegadaClick(mensaje) }
                } else {
                    holder.btnFinalizar.visibility = View.GONE
                }
            }

            is OfertaViewHolder -> {
                holder.tvTitulo.visibility = View.VISIBLE
                holder.tvMonto.visibility  = View.VISIBLE
                holder.tvMonto.text        = String.format("S/ %.2f", mensaje.montoOferta)

                if (mensaje.estadoOferta == "PENDIENTE") {
                    holder.tvEstado.visibility = View.GONE
                    if (mensaje.emisorId != userId) {
                        holder.btnAceptar.visibility  = View.VISIBLE
                        holder.btnRechazar.visibility = View.VISIBLE
                    } else {
                        holder.btnAceptar.visibility  = View.GONE
                        holder.btnRechazar.visibility = View.GONE
                    }
                } else {
                    holder.btnAceptar.visibility  = View.GONE
                    holder.btnRechazar.visibility = View.GONE
                    holder.tvEstado.visibility    = View.VISIBLE
                    holder.tvEstado.text          = "Oferta ${mensaje.estadoOferta}"
                }
                holder.btnAceptar.setOnClickListener  { onOfertaClick(mensaje, "ACEPTADO") }
                holder.btnRechazar.setOnClickListener { onOfertaClick(mensaje, "RECHAZADO") }
            }

            is AccionViewHolder -> {
                holder.tvTitulo.visibility    = View.GONE
                holder.tvMonto.visibility     = View.GONE
                holder.btnRechazar.visibility = View.GONE
                holder.btnAceptar.visibility  = View.GONE
                holder.tvEstado.visibility    = View.GONE

                when (mensaje.tipo) {
                    "FIN_TRABAJO" -> {
                        holder.tvEstado.text       = "🏁 ¡TRABAJO TERMINADO!"
                        holder.tvEstado.visibility = View.VISIBLE
                        if (!esTrabajador) {
                            holder.btnAceptar.text       = "💳 PAGAR SERVICIO"
                            holder.btnAceptar.visibility = View.VISIBLE
                            holder.btnAceptar.setOnClickListener { onPagarClick(mensaje) }
                        }
                    }
                    "PAGO_REALIZADO" -> {
                        if (esTrabajador && mensaje.estadoOferta == "PENDIENTE") {
                            holder.tvEstado.text         = "💳 CLIENTE REPORTÓ PAGO"
                            holder.tvEstado.visibility   = View.VISIBLE
                            holder.btnAceptar.text       = "✅ CONFIRMAR RECEPCIÓN"
                            holder.btnAceptar.visibility = View.VISIBLE
                            holder.btnAceptar.setOnClickListener { onOfertaClick(mensaje, "PAGO_CONFIRMADO") }
                        } else if (mensaje.estadoOferta == "PAGO_CONFIRMADO") {
                            holder.tvEstado.text       = "✅ PAGO RECIBIDO"
                            holder.tvEstado.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = _mensajes.size

    class TextoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMensaje: TextView = view.findViewById(R.id.tvMensajeChat)
    }

    class ImagenSimpleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivImagen: ImageView = view.findViewById(R.id.ivImagenSimple)
    }

    class OfertaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView  = view.findViewById(R.id.tvTituloOferta)
        val tvMonto: TextView   = view.findViewById(R.id.tvMontoOferta)
        val btnAceptar: Button  = view.findViewById(R.id.btnAceptarOferta)
        val btnRechazar: Button = view.findViewById(R.id.btnRechazarOferta)
        val tvEstado: TextView  = view.findViewById(R.id.tvEstadoOferta)
    }

    class TicketViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView   = view.findViewById(R.id.tvTituloOferta)
        val tvMonto: TextView    = view.findViewById(R.id.tvMontoOferta)
        val btnFinalizar: Button = view.findViewById(R.id.btnAceptarOferta)
        val btnOculto: Button    = view.findViewById(R.id.btnRechazarOferta)
        val tvEstado: TextView   = view.findViewById(R.id.tvEstadoOferta)
    }

    class LlegadaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView    = view.findViewById(R.id.tvTituloOferta)
        val tvMonto: TextView     = view.findViewById(R.id.tvMontoOferta)
        val btnPrimario: Button   = view.findViewById(R.id.btnAceptarOferta)
        val btnSecundario: Button = view.findViewById(R.id.btnRechazarOferta)
        val tvEstado: TextView    = view.findViewById(R.id.tvEstadoOferta)
    }

    class AccionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView   = view.findViewById(R.id.tvTituloOferta)
        val tvMonto: TextView    = view.findViewById(R.id.tvMontoOferta)
        val btnAceptar: Button   = view.findViewById(R.id.btnAceptarOferta)
        val btnRechazar: Button  = view.findViewById(R.id.btnRechazarOferta)
        val tvEstado: TextView   = view.findViewById(R.id.tvEstadoOferta)
    }

    class PropuestaIAViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivImagen: ImageView        = view.findViewById(R.id.ivImagenPropuesta)
        val ivImagenEditada: ImageView = view.findViewById(R.id.ivImagenEditada)
        val tvDescripcion: TextView    = view.findViewById(R.id.tvDescripcionPropuesta)
        val tvRespuestaIA: TextView    = view.findViewById(R.id.tvRespuestaIA)
    }
}