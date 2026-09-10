package dev.local.ridecompact.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/** View-based Miuix design tokens used while the app migrates to Compose. */
object MiuixComponents {
    const val SURFACE = 0xffffffff.toInt()
    const val PAGE = 0xffF4FAF7.toInt()
    const val PRIMARY = 0xff006B5F.toInt()
    const val ON_SURFACE = 0xff19352E.toInt()
    const val SECONDARY = 0xff64736E.toInt()
    const val OUTLINE = 0xffDCE9E4.toInt()

    @JvmStatic fun heading(view: TextView) {
        view.setTextColor(ON_SURFACE)
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }

    @JvmStatic fun primaryAction(button: MaterialButton) {
        button.setTextColor(0xffffffff.toInt())
        button.backgroundTintList = ColorStateList.valueOf(PRIMARY)
        button.cornerRadius = 54
        button.stateListAnimator = null
    }

    @JvmStatic fun secondaryAction(button: MaterialButton) {
        button.setTextColor(PRIMARY)
        button.strokeColor = ColorStateList.valueOf(0xffB8D5CC.toInt())
        button.strokeWidth = 1
        button.cornerRadius = 54
        button.stateListAnimator = null
    }

    @JvmStatic fun pressFeedback(view: View) {
        view.setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> touched.alpha = .72f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touched.alpha = 1f
            }
            false
        }
    }
}
