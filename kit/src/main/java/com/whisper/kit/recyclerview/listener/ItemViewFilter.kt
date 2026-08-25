package com.whisper.kit.recyclerview.listener

import android.view.View


/**
 *
 *
 * Created by whisper on 2024/11/19
 */
fun interface ItemViewFilter {

    fun filter(view: View): Boolean

}