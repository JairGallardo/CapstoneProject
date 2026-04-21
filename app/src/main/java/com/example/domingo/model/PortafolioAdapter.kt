package com.example.domingo

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.model.FotoTrabajo

class PortafolioAdapter : RecyclerView.Adapter<PortafolioAdapter.PortafolioViewHolder>() {

    private var fotos: List<FotoTrabajo> = listOf()

    fun actualizar(nuevaLista: List<FotoTrabajo>) {
        this.fotos = nuevaLista
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortafolioViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_foto_portafolio, parent, false)
        return PortafolioViewHolder(view)
    }

    override fun onBindViewHolder(holder: PortafolioViewHolder, position: Int) {
        val foto = fotos[position]
        try {
            val decodedBytes = Base64.decode(foto.urlB64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            holder.ivFoto.setImageBitmap(bitmap)
        } catch (e: Exception) { }
    }

    override fun getItemCount() = fotos.size

    class PortafolioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFoto: ImageView = view.findViewById(R.id.ivItemPortafolio)
    }
}