package com.example.firebot.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.SurfaceView
import android.view.View

class JoystickView : SurfaceView, SurfaceHolder.Callback, View.OnTouchListener {

    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var hatRadius = 0f //the top of the joystick
    private var joystickCallback: JoystickListener? = null
    private val ratio = 5 //The smaller, the more shading will occur

    private fun setupDimensions() {
        centerX = (width / 2).toFloat()
        centerY = (height / 2).toFloat()
        baseRadius = (Math.min(width, height) / 3).toFloat()
        hatRadius = (Math.min(width, height) / 5).toFloat()
    }

    constructor(context: Context?) : super(context) {
        holder.addCallback(this)
        //adding this line will cause the SurfaceView to use the onTouch method from this class to handle user screen touches
        setOnTouchListener(this)
        if (context is JoystickListener) joystickCallback = context
    }

    constructor(context: Context?, attributes: AttributeSet?, style: Int) : super(
        context,
        attributes,
        style
    ) {
        holder.addCallback(this)
        setOnTouchListener(this)
        if (context is JoystickListener) joystickCallback = context
    }

    constructor(context: Context?, attributes: AttributeSet?) : super(context, attributes) {
        holder.addCallback(this)
        setOnTouchListener(this)
        if (context is JoystickListener) joystickCallback = context
    }

    private fun drawJoystick(newX: Float, newY: Float) {
        /*if statement prevents the drawing method from executing when the SurfaceView
        has not been created on-screen, preventing exceptions at runtime*/
        if (holder.surface.isValid) {
            val myCanvas: Canvas = this.holder.lockCanvas() //Stuff to draw
            val colors = Paint()
            myCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR) //Clear the BG

            //First determine the sin and cos of the angle that the touched point is at relative to the center of the joystick
            val hypotenuse =
                Math.sqrt(
                    Math.pow(
                        (newX - centerX).toDouble(),
                        2.0
                    ) + Math.pow((newY - centerY).toDouble(), 2.0)
                )
                    .toFloat()
            val sin = (newY - centerY) / hypotenuse //sin = o/h
            val cos = (newX - centerX) / hypotenuse //cos = a/h

            //Draw the base first before shading
            colors.setARGB(100, 79, 79, 79)
            myCanvas.drawCircle(centerX, centerY, baseRadius, colors)
            /*for (i in 1..(baseRadius / ratio).toInt()) {
                colors.setARGB(
                    150 / i,
                    38,
                    70,
                    83
                ) //Gradually decrease the shade of black drawn to create a nice shading effect
                myCanvas.drawCircle(
                    newX - cos * hypotenuse * (ratio / baseRadius) * i,
                    newY - sin * hypotenuse * (ratio / baseRadius) * i,
                    i * (hatRadius * ratio / baseRadius),
                    colors
                ) //Gradually increase the size of the shading effect
            }*/

            //Drawing the joystick hat
            for (i in 0..(hatRadius / ratio).toInt()) {
                colors.setARGB(
                    20,
                    (i * (33 * ratio / hatRadius)).toInt(),
                    (i * (37 * ratio / hatRadius)).toInt(), 41
                ) //Change the joystick color for shading purposes
                myCanvas.drawCircle(
                    newX,
                    newY,
                    hatRadius - i.toFloat() * ratio / 2,
                    colors
                ) //Draw the shading for the hat
            }
            holder.unlockCanvasAndPost(myCanvas) //Write the new drawing to the SurfaceView
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        setupDimensions()
        drawJoystick(centerX, centerY)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    override fun onTouch(v: View, e: MotionEvent): Boolean {
        //this makes sure that the touch listener only accepts touches coming from this SurfaceView
        if (v.equals(this)) {
            /*we need to do this to make sure that the joystick only moves as long as the user is touching the screen,
            and resets to its original position when the user lets go*/
            if (e.action != MotionEvent.ACTION_UP) {
                val displacement =
                    Math.sqrt(
                        Math.pow(
                            (e.x - centerX).toDouble(),
                            2.0
                        ) + Math.pow((e.y - centerY).toDouble(), 2.0)
                    )
                        .toFloat()
                //the click is valid and we don't have to constrain the joystick hat
                if (displacement < baseRadius) {
                    //e.x and e.y give the X and Y coordinates, respectively, in pixels where the user touched the screen
                        //sending them to the drawJoystick method causes the hat of the joystick to be drawn at those positions
                    drawJoystick(e.x, e.y)
                    joystickCallback!!.onJoystickMoved(
                        (e.x - centerX) / baseRadius, (e.y - centerY) / baseRadius,
                        id
                    )
                } else {
                    val ratio = baseRadius / displacement
                    val constrainedX = centerX + (e.x - centerX) * ratio
                    val constrainedY = centerY + (e.y - centerY) * ratio
                    drawJoystick(constrainedX, constrainedY)
                    joystickCallback!!.onJoystickMoved(
                        (constrainedX - centerX) / baseRadius,
                        (constrainedY - centerY) / baseRadius,
                        id
                    )
                }
            } else drawJoystick(centerX, centerY)
            joystickCallback!!.onJoystickMoved(0f, 0f, id)
            //this else statement executes when the joystick is released
            //this resets the joystick to its center position when the user lets go
        }
        return true
    }

    interface JoystickListener {
        fun onJoystickMoved(xPercent: Float, yPercent: Float, id: Int)
    }
}