package com.mmgpt.shoplistgpt

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/*
 * Double-tap-to-open-category navigation, shared by the two overview pages:
 * the Plan "Categories" page (PlanCategoriesFragment) and the Shop "Summary" page
 * (ShopCategoriesFragment). Each page decides which rows count and which page they open.
 */

/**
 * Calls [onRowDoubleTapped] with the adapter position of the row the user double-taps.
 * The callback returns true if it handled the tap. Normal touch handling (scrolling,
 * buttons inside rows) is unaffected.
 */
fun RecyclerView.setOnRowDoubleTapListener(onRowDoubleTapped: (position: Int) -> Boolean) {
    val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // The touch listener receives events in this RecyclerView's own coordinates.
            val row = findChildViewUnder(e.x, e.y) ?: return false
            val position = getChildAdapterPosition(row)
            return position != RecyclerView.NO_POSITION && onRowDoubleTapped(position)
        }
    })
    setOnTouchListener { _, event ->
        detector.onTouchEvent(event)
        false
    }
}

/** Shows page [page] of the activity's category ViewPager. */
fun Fragment.openPagerPage(page: Int) {
    requireActivity().findViewById<ViewPager2>(R.id.viewPager).currentItem = page
}
