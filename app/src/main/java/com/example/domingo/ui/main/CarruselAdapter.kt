package com.example.domingo.ui.main

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class CarruselAdapter(private val imagenes: List<Int>) :
    RecyclerView.Adapter<CarruselAdapter.CarruselViewHolder>() {

    class CarruselViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarruselViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        return CarruselViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: CarruselViewHolder, position: Int) {
        holder.imageView.setImageResource(imagenes[position])
    }

    override fun getItemCount(): Int = imagenes.size
}