package me.kelly.msgqueuetracer.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.round

class BallView(context: Context, attr: AttributeSet) : View(context, attr) {

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val radius = 30f
    private var viewX = 0f
    private var viewY = 0f

    init {
        paint.color = Color.RED
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 2f
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                viewX = event.x
                viewY = event.y
                if (boundaryCheck()) {
                    postInvalidate()
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (viewX == 0f && viewY == 0f) {
            canvas.drawCircle(width / 2f, height / 2f, radius, paint)
        } else {
            canvas.drawCircle(viewX, viewY, radius, paint)
        }
    }

    private fun boundaryCheck(): Boolean {
        val curX = round(viewX).toInt()
        val curY = round(viewY).toInt()
        if (curX <= radius * 2 || curX >= width - radius * 2) return false
        if (curY <= radius * 2 || curY >= height - radius * 2) return false
        return true
    }

}