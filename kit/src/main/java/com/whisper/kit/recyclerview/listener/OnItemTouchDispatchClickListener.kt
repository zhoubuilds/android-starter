package com.whisper.kit.recyclerview.listener

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView


/**
 *
 *
 * Created by whisper on 2024/11/19
 */
class OnItemTouchDispatchClickListener(
    recyclerView: RecyclerView,
    listener: OnItemClickListener?
) :
    RecyclerView.OnItemTouchListener {

    private val _gestureDetector: GestureDetector = GestureDetector(
        recyclerView.context,
        OnDispatchClickGestureListener(
            recyclerView,
            { view -> view.isClickable && view.isEnabled },
            listener
        )
    )

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        return _gestureDetector.onTouchEvent(e)
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
    }

}