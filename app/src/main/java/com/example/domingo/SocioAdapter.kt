package com.example.domingo

import Socio
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.ui.NegociacionActivity
import java.util.Locale

class SocioAdapter(private val listaSocios: List<Socio>) :
    RecyclerView.Adapter<SocioAdapter.SocioViewHolder>() {

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

        // --- PUNTO B: Lógica de Estrellas y Trabajos ---
        if (socio.trabajosRealizados == 0) {
            holder.rating.text = "Nuevo (Sin trabajos)"
            holder.rating.setTextColor(holder.itemView.context.getColor(android.R.color.holo_blue_dark))
        } else {
            holder.rating.text = String.format(Locale.getDefault(), "⭐ %.1f (%d trabajos)", socio.rating, socio.trabajosRealizados)
            holder.rating.setTextColor(holder.itemView.context.getColor(android.R.color.black))
        }

        holder.precio.text = String.format(Locale.getDefault(), "Total: S/ %.2f", socio.tarifaSugerida)
        holder.distancia.text = String.format(Locale.getDefault(), "A %.1f km de ti", socio.distancia)

        if (!socio.fotoPerfilB64.isNullOrEmpty()) {
            try {
                val imageBytes = Base64.decode(socio.fotoPerfilB64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                if (bitmap != null) {
                    holder.foto.setImageBitmap(bitmap)
                } else {
                    holder.foto.setImageResource(R.drawable.ic_food) // Placeholder si el decode falla
                }
            } catch (e: Exception) {
                e.printStackTrace()
                holder.foto.setImageResource(R.drawable.ic_food)
            }
        } else {
            // Imagen por defecto si no hay string Base64
            holder.foto.setImageResource(R.drawable.ic_food)
        }

        holder.btnNegociar.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, NegociacionActivity::class.java)
            intent.putExtra("SOCIO_ID", socio.id)
            intent.putExtra("SOCIO_NOMBRE", socio.nombre)
            intent.putExtra("SOCIO_TARIFA", socio.tarifaSugerida)
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = listaSocios.size
}