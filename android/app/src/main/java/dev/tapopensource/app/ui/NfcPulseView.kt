package dev.tapopensource.app.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import dev.tapopensource.app.R

/**
 * Animação NFC — 3 anéis pulsando igual à web (tap-pulse).
 * Cada anel: scale 0.85→1.1 + alpha 0.9→0, com delay escalonado.
 * Anéis começam MAIORES que o ícone e expandem para fora.
 */
class NfcPulseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val rings = mutableListOf<View>()
    private val animators = mutableListOf<AnimatorSet>()

    // Tamanhos dos anéis em dp - começam maiores que o ícone (48dp)
    // e expandem para fora: 70, 110, 150
    private val ringSizesDp = listOf(70, 110, 150)
    private val delaysMs    = listOf(0L, 500L, 1000L)

    init {
        val size = dpToPx(160)
        layoutParams = LayoutParams(size, size)

        // Cria os 3 anéis (ficam atrás do ícone)
        ringSizesDp.forEachIndexed { i, dp ->
            val ring = View(context).apply {
                background = context.getDrawable(R.drawable.nfc_ring)
                alpha = 0f
                val s = dpToPx(dp)
                val lp = LayoutParams(s, s).apply {
                    gravity = android.view.Gravity.CENTER
                }
                layoutParams = lp
            }
            addView(ring)
            rings.add(ring)
        }

        // Ícone do cartão NFC no centro (fica na frente)
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_nfc_card)
            val s = dpToPx(48)
            layoutParams = LayoutParams(s, s).apply {
                gravity = android.view.Gravity.CENTER
            }
            elevation = dpToPx(4).toFloat() // Garante que fica na frente
        }
        addView(icon)
    }

    fun startAnimation() {
        stopAnimation()
        rings.forEachIndexed { i, ring ->
            val scaleX = ObjectAnimator.ofFloat(ring, "scaleX", 0.85f, 1.1f).apply {
                duration = 2000
                startDelay = delaysMs[i]
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
            }
            val scaleY = ObjectAnimator.ofFloat(ring, "scaleY", 0.85f, 1.1f).apply {
                duration = 2000
                startDelay = delaysMs[i]
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
            }
            val alpha = ObjectAnimator.ofFloat(ring, "alpha", 0.9f, 0f).apply {
                duration = 2000
                startDelay = delaysMs[i]
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
            }
            val set = AnimatorSet().apply { playTogether(scaleX, scaleY, alpha) }
            animators.add(set)
            set.start()
        }
    }

    fun stopAnimation() {
        animators.forEach { it.cancel() }
        animators.clear()
        rings.forEach { it.alpha = 0f; it.scaleX = 1f; it.scaleY = 1f }
    }

    private fun dpToPx(dp: Int) =
        (dp * resources.displayMetrics.density).toInt()
}
