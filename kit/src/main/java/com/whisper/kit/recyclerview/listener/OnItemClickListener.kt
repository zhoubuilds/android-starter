package com.whisper.kit.recyclerview.listener

import android.view.View
import androidx.recyclerview.widget.RecyclerView


/**
 *
 *
 * Created by whisper on 2024/11/19
 */
fun interface OnItemClickListener {

    fun onItemClick(recyclerView: RecyclerView, view: View, position: Int)

}