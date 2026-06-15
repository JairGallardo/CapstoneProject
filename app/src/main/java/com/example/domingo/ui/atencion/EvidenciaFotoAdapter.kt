package com.example.domingo.ui.atencion

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.R

class EvidenciaFotoAdapter(
    private val fotos: MutableList<String>,
    private val maxFotos: Int = 3,
    private val onAgregarClick: () -> Unit,
    private val onQuitarClick: (position: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TIPO_FOTO = 0
        const val TIPO_AGREGAR = 1
    }

    inner class FotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFoto: ImageView = view.findViewById(R.id.ivFotoEvidencia)
        val btnQuitar: ImageButton = view.findViewById(R.id.btnQuitarFoto)
        val tvAgregar: TextView = view.findViewById(R.id.tvAgregarFoto)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < fotos.size) TIPO_FOTO else TIPO_AGREGAR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_foto_evidencia, parent, false)
        return FotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val h = holder as FotoViewHolder

        if (getItemViewType(position) == TIPO_FOTO) {
            val b64 = fotos[position]
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                h.ivFoto.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            } catch (e: Exception) {
                h.ivFoto.setImageResource(android.R.drawable.ic_menu_camera)
            }

            h.btnQuitar.visibility = View.VISIBLE
            h.tvAgregar.visibility = View.GONE
            h.itemView.setOnClickListener(null)
            h.btnQuitar.setOnClickListener { onQuitarClick(position) }
        } else {
            h.ivFoto.setImageResource(android.R.drawable.ic_menu_camera)
            h.btnQuitar.visibility = View.GONE
            h.tvAgregar.visibility = View.VISIBLE
            h.itemView.setOnClickListener { onAgregarClick() }
            h.btnQuitar.setOnClickListener(null)
        }
    }

    override fun getItemCount(): Int {
        return if (fotos.size < maxFotos) fotos.size + 1 else fotos.size
    }

    fun actualizar() {
        notifyDataSetChanged()
    }
}