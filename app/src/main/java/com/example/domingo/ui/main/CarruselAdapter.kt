package com.example.domingo.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.domingo.R

class CarruselAdapter(private val imagenes: List<Int>) : RecyclerView.Adapter<CarruselAdapter.CarruselViewHolder>() {

    class CarruselViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.vpCarrusel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarruselViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            id = R.id.vpCarrusel
        }
        return CarruselViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: CarruselViewHolder, position: Int) {
        holder.imageView.setImageResource(imagenes[position])
    }

    override fun getItemCount(): Int = imagenes.size
}