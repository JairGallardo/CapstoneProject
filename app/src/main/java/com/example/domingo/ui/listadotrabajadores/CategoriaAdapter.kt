package com.example.domingo.ui.listadotrabajadores

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.R
import com.example.domingo.model.Categoria

class CategoriaAdapter(
    private var lista: List<Categoria>,
    private val esTrabajador: Boolean = false,
    private val onClick: (Categoria) -> Unit
) : RecyclerView.Adapter<CategoriaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icono: ImageView = view.findViewById(R.id.ivIconoCategoria)
        val nombre: TextView = view.findViewById(R.id.tvNombreCategoria)
        val conteo: TextView = view.findViewById(R.id.tvConteoTrabajos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_categoria, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cat = lista[position]
        holder.nombre.text = cat.nombre
        holder.icono.setImageResource(cat.iconoResId)
        holder.itemView.setOnClickListener { onClick(cat) }

        if (esTrabajador && cat.trabajosRealizados > 0) {
            holder.conteo.visibility = View.VISIBLE
            holder.conteo.text = if (cat.trabajosRealizados > 99) "99+" else cat.trabajosRealizados.toString()
        } else {
            holder.conteo.visibility = View.GONE
        }
    }

    override fun getItemCount() = lista.size

    fun actualizarLista(nuevaLista: List<Categoria>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }
}