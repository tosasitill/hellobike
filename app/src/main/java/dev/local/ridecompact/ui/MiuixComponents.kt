package dev.local.ridecompact.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * View-based Miuix design tokens used while the app migrates to Compose.
 * Values mirror top.yukonga.miuix.kmp:miuix-ui:0.9.0 lightColorScheme()/darkColorScheme()
 * (see Colors.kt) and res/values(-night)/colors.xml. Update all three together.
 */
object MiuixComponents {
    const val SURFACE = 0xffF7F7F7.toInt()
    const val PAGE = 0xffffffff.toInt()
    const val PRIMARY = 0xff3482FF.toInt()
    const val ON_SURFACE = 0xff000000.toInt()
    const val SECONDARY = 0xffE6E6E6.toInt()
    const val OUTLINE = 0xffD9D9D9.toInt()
    const val SECONDARY_VARIANT = 0xffF0F0F0.toInt()
    const val ON_SECONDARY_VARIANT = 0xff303030.toInt()
    const val ON_SURFACE_VARIANT_SUMMARY = 0x99000000.toInt()
    const val DISABLED_ON_SURFACE = 0xffB2B2B2.toInt()

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
