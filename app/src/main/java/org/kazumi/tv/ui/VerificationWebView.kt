package org.kazumi.tv.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView

/** D-pad pointer is opt-in so normal dialog buttons retain their standard focus navigation. */
class VerificationWebView(context:Context):WebView(context) {
    var pointerEnabled=false
        set(value) { if(!value&&dragging)touch(MotionEvent.ACTION_UP);field=value;dragging=false;invalidate() }
    var onExitPointer:()->Unit={}
    private var xCursor=0f
    private var yCursor=0f
    private var dragging=false
    private var longPress=false
    private var gestureStart=0L
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xff95d5a0.toInt();style=Paint.Style.STROKE;strokeWidth=3f }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) { super.onSizeChanged(w,h,oldw,oldh);xCursor=w/2f;yCursor=h/2f }
    private fun touch(action:Int) {
        val now=android.os.SystemClock.uptimeMillis()
        if(action==MotionEvent.ACTION_DOWN)gestureStart=now
        val event=MotionEvent.obtain(gestureStart,now,action,xCursor,yCursor,0)
        super.onTouchEvent(event);event.recycle()
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        if(pointerEnabled) { canvas.drawCircle(xCursor+scrollX,yCursor+scrollY,if(dragging)14f else 9f,paint) }
    }
    override fun dispatchKeyEvent(event:KeyEvent):Boolean {
        if(!pointerEnabled)return super.dispatchKeyEvent(event)
        if(event.keyCode==KeyEvent.KEYCODE_BACK) { if(event.action==KeyEvent.ACTION_UP) { pointerEnabled=false;onExitPointer() };return true }
        if(event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER)) {
            if(event.action==KeyEvent.ACTION_DOWN&&event.repeatCount==0)longPress=false
            if(event.action==KeyEvent.ACTION_DOWN&&event.isLongPress&&!dragging) { longPress=true;dragging=true;touch(MotionEvent.ACTION_DOWN) }
            if(event.action==KeyEvent.ACTION_UP&&!longPress) {
                if(dragging) { touch(MotionEvent.ACTION_UP);dragging=false }
                else { touch(MotionEvent.ACTION_DOWN);touch(MotionEvent.ACTION_UP) }
            }
            invalidate();return true
        }
        val step=24f*resources.displayMetrics.density
        val dx=when(event.keyCode) { KeyEvent.KEYCODE_DPAD_LEFT -> -step;KeyEvent.KEYCODE_DPAD_RIGHT -> step;else->0f }
        val dy=when(event.keyCode) { KeyEvent.KEYCODE_DPAD_UP -> -step;KeyEvent.KEYCODE_DPAD_DOWN -> step;else->0f }
        if(dx!=0f||dy!=0f) {
            if(event.action==KeyEvent.ACTION_DOWN) {
                if(!dragging&&(yCursor+dy<0||yCursor+dy>height))scrollBy(0,dy.toInt())
                xCursor=(xCursor+dx).coerceIn(0f,width.toFloat());yCursor=(yCursor+dy).coerceIn(0f,height.toFloat())
                if(dragging)touch(MotionEvent.ACTION_MOVE)
                invalidate()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
