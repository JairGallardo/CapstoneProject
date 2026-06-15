package com.example.domingo.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.R
import com.example.domingo.model.ServicioFinalizado
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistorialAdapter(
    private val lista: List<ServicioFinalizado>,
    private val esTrabajador: Boolean
) : RecyclerView.Adapter<HistorialAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCategoria: TextView = view.findViewById(R.id.tvHistorialCategoria)
        val tvNombre: TextView = view.findViewById(R.id.tvHistorialNombre)
        val tvFecha: TextView = view.findViewById(R.id.tvHistorialFecha)
        val tvMonto: TextView = view.findViewById(R.id.tvHistorialMonto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_historial, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]

        holder.tvCategoria.text = item.categoria.ifBlank { "Servicio" }

        holder.tvNombre.text = if (esTrabajador)
            "Cliente: ${item.clienteNombre.ifBlank { "Usuario" }}"
        else
            "Trabajador: ${item.trabajadorNombre.ifBlank { "Usuario" }}"

        holder.tvFecha.text = if (item.fecha > 0L) {
            SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale("es", "ES")).format(Date(item.fecha))
        } else {
            "Fecha no disponible"
        }

        holder.tvMonto.text = "S/ ${String.format("%.2f", item.monto)}"
    }

    override fun getItemCount() = lista.size
}