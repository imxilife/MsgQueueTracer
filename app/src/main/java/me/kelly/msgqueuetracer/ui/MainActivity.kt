package me.kelly.msgqueuetracer.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
//import me.kelly.xstackhook.mq.XPrinter
//import me.kelly.xstackhook.stack.Sliver


const val TAGG = "TAGG"

/**
 * 整体流程
 *
 */
class MainActivity : AppCompatActivity() {

    //private var sliver: Sliver? = null
    //private lateinit var xPrinter: XPrinter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main_layout)
        //sliver = Sliver()
        //xPrinter = XPrinter(sliver!!)
        //Looper.getMainLooper().setMessageLogging(xPrinter)
    }

   /* open fun getMethodsStackTrace(v: View) {
        val methodIds = sliver?.getMethodFrames(Looper.getMainLooper().thread)
        if (methodIds != null && methodIds.isNotEmpty()) {
            val methodStackList = sliver?.prettyMethods(methodIds)
            methodStackList?.forEach {
                Log.d("TAGG", it)
            }
        }
    }*/

   /* private fun getMethodFrames(thread: Thread) {
        Log.d(TAGG, "<- call getMethodsStackTrace")
        val methodIdList = sliver?.getMethodFrames(thread)
        if (methodIdList != null && methodIdList.isNotEmpty()) {
            sliver?.prettyMethods(methodIdList)
        }
    }*/

    /*fun dumpNextMessages(v:View){

        val handler = Handler(Looper.getMainLooper())
        for (i in 0..9){
             val runnable = DelayRunnable(i)
             Log.i("TAGG","runnable.hashCode:${runnable.hashCode()}")
             handler.post(runnable)
        }
        Log.i("TAGG","post completed...")
        xPrinter.dumpPendingMessages()
    }*/

    fun sleepThread(view: View) {
        Log.d(TAGG, "sleep start...")
        try {
            Thread.sleep(200)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        Log.d(TAGG, "sleep end...")
    }


    /*fun showMetricResult(v:View){
        xPrinter.retrievedData(this,"MQ202407162232")
    }*/


    inner class DelayRunnable(private val index:Int):Runnable{
        override fun run() {
            Thread.sleep(3000)
        }
    }
}