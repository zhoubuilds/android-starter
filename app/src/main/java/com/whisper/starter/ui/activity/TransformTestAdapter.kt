package com.whisper.starter.ui.activity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.whisper.starter.databinding.ItemTransformTestBinding

class TransformTestAdapter(
    private val _itemCount: Int = 20
) : RecyclerView.Adapter<TransformTestAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemTransformTestBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemTransformTestBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val density = holder.itemView.resources.displayMetrics.density
        val cameraDistance = 8000f * density

        holder.binding.tvCaseTitle.text = "Transform hit test #$position"
        holder.binding.btnPlain.text = "Plain #$position"
        holder.binding.btnScale.text = "Scale #$position"
        holder.binding.btnRotate.text = "Rotate #$position"
        holder.binding.btnRotationX.text = "RotX #$position"
        holder.binding.btnRotationY.text = "RotY #$position"
        holder.binding.btnTranslate.text = "Move #$position"
        holder.binding.btnPivot.text = "Pivot #$position"
        holder.binding.btnCombo2d.text = "2D Combo #$position"
        holder.binding.btnCombo3d.text = "3D Combo #$position"
        holder.binding.btnOverlapBack.text = "Overlap Back #$position"
        holder.binding.btnOverlapFront.text = "Overlap Front #$position"
        holder.binding.btnNestedChild.text = "Nested #$position"

        holder.binding.btnRotationX.cameraDistance = cameraDistance
        holder.binding.btnRotationY.cameraDistance = cameraDistance
        holder.binding.btnCombo3d.cameraDistance = cameraDistance
        holder.binding.btnNestedChild.cameraDistance = cameraDistance

        holder.binding.btnPivot.pivotX = 0f
        holder.binding.btnPivot.pivotY = 56f * density
        holder.binding.btnOverlapBack.z = 1f
        holder.binding.btnOverlapFront.z = 8f
    }

    override fun getItemCount(): Int = _itemCount
}
