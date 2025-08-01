package com.example.kanjireader

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView

class ClickablePartsTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    interface OnPartClickListener {
        fun onPartClicked(part: String)
    }

    private var partClickListener: OnPartClickListener? = null

    init {
        // Don't set movement method here, we'll set it after text is set
        highlightColor = Color.TRANSPARENT
    }

    fun setOnPartClickListener(listener: OnPartClickListener?) {
        this.partClickListener = listener
    }

    fun setParts(parts: List<String>, prefix: String = "Parts: ") {
        if (parts.isEmpty()) {
            text = "${prefix}Unknown"
            return
        }

        val fullText = prefix + parts.joinToString(", ")
        val spannableString = SpannableString(fullText)

        var currentIndex = prefix.length
        for ((index, part) in parts.withIndex()) {
            val partStart = currentIndex
            val partEnd = partStart + part.length

            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    partClickListener?.onPartClicked(part)
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = Color.parseColor("#2196F3") // Material Blue
                    ds.isUnderlineText = false
                }
            }

            spannableString.setSpan(
                clickableSpan,
                partStart,
                partEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // Move to next part position (add 2 for ", ")
            currentIndex = partEnd + if (index < parts.size - 1) 2 else 0
        }

        text = spannableString
        // Set movement method after setting the text to avoid layout issues
        movementMethod = LinkMovementMethod.getInstance()
    }
}