package com.example.aiassistantcoder.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.example.aiassistantcoder.R
import com.google.android.material.snackbar.Snackbar

object SnackBarApp {

    enum class Type {
        ERROR,
        WARNING,
        INFO,
        SUCCESS
    }

    fun show(root: View, message: String, type: Type) {
        val snackbar = Snackbar.make(root, "", Snackbar.LENGTH_SHORT)

        // Use custom layout
        val layout = snackbar.view as ViewGroup
        val inflater = LayoutInflater.from(root.context)
        val customView = inflater.inflate(R.layout.snackbar_colored_message, null)

        val rootView = customView.findViewById<View>(R.id.snackbar_root)
        val iconView = customView.findViewById<ImageView>(R.id.snackbar_icon)
        val textView = customView.findViewById<TextView>(R.id.snackbar_text)

        textView.text = message

        val ctx = root.context
        val res = ctx.resources

        // Choose color + icon per type
        val (colorRes, iconRes) = when (type) {
            Type.ERROR -> R.color.snackbar_error to R.drawable.ic_error_24
            Type.WARNING -> R.color.snackbar_warning to R.drawable.ic_warning_24
            Type.INFO -> R.color.snackbar_info to R.drawable.ic_info_24
            Type.SUCCESS -> R.color.snackbar_success to R.drawable.ic_success_24
        }

        // Tint background
        val colorInt = res.getColor(colorRes, ctx.theme)
        ViewCompat.setBackgroundTintList(
            rootView,
            ColorStateList.valueOf(colorInt)
        )

        // Set icon & force white tint to match screenshot
        iconView.setImageResource(iconRes)
        iconView.imageTintList =
            ColorStateList.valueOf(res.getColor(android.R.color.white, ctx.theme))
        textView.setTextColor(res.getColor(android.R.color.white, ctx.theme))

        // Remove default snackbar background & padding
        layout.setPadding(0, 0, 0, 0)
        layout.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        layout.removeAllViews()
        layout.addView(customView)

        snackbar.show()
    }
}
