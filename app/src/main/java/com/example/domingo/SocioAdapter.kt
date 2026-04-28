package com.example.domingo

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.model.Socio
import com.example.domingo.ui.NegociacionActivity
import java.util.Locale

class SocioAdapter(
    private val listaSocios: MutableList<Socio> = mutableListOf(),
    private val onClick: (Socio) -> Unit = {}
) : RecyclerView.Adapter<SocioAdapter.SocioViewHolder>() {

    class SocioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val foto: ImageView = view.findViewById(R.id.ivFotoSocio)
        val nombre: TextView = view.findViewById(R.id.tvNombreSocio)
        val rating: TextView = view.findViewById(R.id.tvRating)
        val precio: TextView = view.findViewById(R.id.tvPrecioBase)
        val distancia: TextView = view.findViewById(R.id.tvDistanciaSocio)
        val btnNegociar: Button = view.findViewById(R.id.btnNegociar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SocioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trabajador, parent, false)
        return SocioViewHolder(view)
    }

    override fun onBindViewHolder(holder: SocioViewHolder, position: Int) {
        val socio = listaSocios[position]

        holder.nombre.text = socio.nombre

        // Rating y trabajos
        if (socio.trabajosRealizados == 0) {
            holder.rating.text = "Nuevo (Sin trabajos)"
            holder.rating.setTextColor(holder.itemView.context.getColor(android.R.color.holo_blue_dark))
        } else {
            holder.rating.text = String.format(Locale.getDefault(), "⭐ %.1f (%d trabajos)", socio.rating, socio.trabajosRealizados)
            holder.rating.setTextColor(holder.itemView.context.getColor(android.R.color.black))
        }

        holder.precio.text = String.format(Locale.getDefault(), "Total: S/ %.2f", socio.tarifaSugerida)
        holder.distancia.text = String.format(Locale.getDefault(), "A %.1f km de ti", socio.distancia)

        // Foto con manejo de errores mejorado
        if (!socio.fotoPerfilB64.isNullOrEmpty()) {
            try {
                val imageBytes = Base64.decode(socio.fotoPerfilB64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                holder.foto.setImageBitmap(bitmap)
            } catch (e: Exception) {
                Log.e("SocioAdapter", "Error decodificando foto: ${e.message}")
                holder.foto.setImageResource(R.drawable.ic_food)
            }
        } else {
            holder.foto.setImageResource(R.drawable.ic_food)
        }

        // ✅ FIXED: Botón negociar usa callback SEGURO
        holder.btnNegociar.setOnClickListener {
            try {
                onClick(socio)  // ← Usa el callback del ListadoTrabajadoresActivity
            } catch (e: Exception) {
                Log.e("SocioAdapter", "Error en click: ${e.message}")
                // Fallback directo (por si acaso)
                abrirNegociacionDirecto(holder.itemView.context, socio)
            }
        }

        // ✅ BONUS: Click en toda la card también funciona
        holder.itemView.setOnClickListener {
            onClick(socio)
        }
    }

    override fun getItemCount() = listaSocios.size

    // ✅ FIXED: Método para actualizar lista (llamado desde ListadoTrabajadoresActivity)
    fun actualizarLista(nuevaLista: List<Socio>) {
        listaSocios.clear()
        listaSocios.addAll(nuevaLista)
        notifyDataSetChanged()
    }

    private fun abrirNegociacionDirecto(context: android.content.Context, socio: Socio) {
        val intent = Intent(context, NegociacionActivity::class.java).apply {
            // ✅ FIXED: Parámetros CORRECTOS que espera NegociacionActivity
            putExtra("CHAT_ID", "chat_${socio.id}_${System.currentTimeMillis()}")
            putExtra("RECEPTOR_ID", socio.id)
            putExtra("ES_TRABAJADOR", false)
            putExtra("SOCIO_NOMBRE", socio.nombre)
        }
        context.startActivity(intent)
        Toast.makeText(context, "Chat con ${socio.nombre}", Toast.LENGTH_SHORT).show()
    }
}