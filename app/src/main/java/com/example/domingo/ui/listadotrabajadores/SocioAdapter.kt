package com.example.domingo.ui.listadotrabajadores

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
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
import com.example.domingo.model.Socio
import com.example.domingo.ui.perfil.PerfilActivity
import java.util.Locale

class SocioAdapter(
    private val listaSocios: MutableList<Socio> = mutableListOf(),
    private val categoriaActual: String? = null,
    private val onLongClick: (Socio) -> Unit = {},
    private val onClick: (Socio) -> Unit = {}
) : RecyclerView.Adapter<SocioAdapter.SocioViewHolder>() {

    class SocioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val foto: ImageView       = view.findViewById(R.id.ivFotoSocio)
        val nombre: TextView      = view.findViewById(R.id.tvNombreSocio)
        val rating: TextView      = view.findViewById(R.id.tvRating)
        val distancia: TextView   = view.findViewById(R.id.tvDistanciaSocio)
        val descripcion: TextView = view.findViewById(R.id.tvDescripcionDinamica)
        val btnNegociar: Button   = view.findViewById(R.id.btnNegociar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SocioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trabajador, parent, false)
        return SocioViewHolder(view)
    }

    override fun onBindViewHolder(holder: SocioViewHolder, position: Int) {
        val socio = listaSocios[position]

        holder.nombre.text = socio.nombre.ifEmpty { "Trabajador" }

        val esBandeja = categoriaActual == null

        if (socio.descripcion.isNotEmpty()) {
            holder.descripcion.text       = socio.descripcion
            holder.descripcion.visibility = View.VISIBLE
        } else {
            holder.descripcion.visibility = View.GONE
        }

        if (esBandeja) {
            holder.rating.visibility = View.GONE
        } else {
            holder.rating.visibility = View.VISIBLE
            if (socio.trabajosRealizados == 0 && socio.rating == 0.0) {
                holder.rating.text = "Nuevo en esta categoría"
                holder.rating.setTextColor(Color.parseColor("#1565C0"))
            } else {
                val label = if (socio.trabajosRealizados == 1) "trabajo" else "trabajos"
                holder.rating.text = String.format(
                    Locale.getDefault(),
                    "★ %.1f · %d %s",
                    socio.rating,
                    socio.trabajosRealizados,
                    label
                )
                holder.rating.setTextColor(colorParaRating(socio.rating))
            }
        }

        holder.distancia.visibility = View.GONE

        if (!socio.fotoPerfilB64.isNullOrBlank()) {
            try {
                val bytes  = Base64.decode(socio.fotoPerfilB64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) holder.foto.setImageBitmap(bitmap)
                else                holder.foto.setImageResource(R.drawable.ic_food)
            } catch (e: Exception) {
                Log.e("SocioAdapter", "Error foto: ${e.message}")
                holder.foto.setImageResource(R.drawable.ic_food)
            }
        } else {
            holder.foto.setImageResource(R.drawable.ic_food)
        }

        if (esBandeja) {
            holder.btnNegociar.text = if (socio.activo) "Abrir Chat" else "Ver Resumen"

            val abrirChat = View.OnClickListener { onClick(socio) }
            holder.btnNegociar.setOnClickListener(abrirChat)
            holder.itemView.setOnClickListener(abrirChat)

            holder.foto.setOnClickListener {
                abrirPerfilConFiltro(holder.itemView.context, socio, null)
            }

            holder.itemView.setOnLongClickListener {
                onLongClick(socio)
                true
            }
        } else {

            holder.btnNegociar.text = "Ofertar"

            val iniciarChat = View.OnClickListener { onClick(socio) }
            holder.btnNegociar.setOnClickListener(iniciarChat)
            holder.itemView.setOnClickListener(iniciarChat)

            holder.foto.setOnClickListener {
                abrirPerfilConFiltro(holder.itemView.context, socio, categoriaActual)
            }

            holder.itemView.setOnLongClickListener(null)
        }
    }

    override fun getItemCount() = listaSocios.size

    fun actualizarLista(nuevaLista: List<Socio>) {
        listaSocios.clear()
        listaSocios.addAll(nuevaLista)
        notifyDataSetChanged()
    }

    private fun colorParaRating(rating: Double): Int = when {
        rating < 3.0 -> Color.parseColor("#D32F2F")
        rating < 4.0 -> Color.parseColor("#F57C00")
        else         -> Color.parseColor("#388E3C")
    }

    private fun abrirPerfilConFiltro(context: Context, socio: Socio, categoria: String?) {
        val intent = Intent(context, PerfilActivity::class.java).apply {
            val idVer = if (socio.receptorId.isNullOrEmpty()) socio.id else socio.receptorId
            putExtra("VER_USUARIO_ID", idVer)
            if (!categoria.isNullOrEmpty()) putExtra("CATEGORIA_FILTRO", categoria)
        }
        context.startActivity(intent)
    }
}