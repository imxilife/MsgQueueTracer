package me.kelly.msgqueuetracer.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import me.kelly.msgqueuetracer.R
import me.kelly.mqtracerlib.stack.Sliver
import me.kelly.mqtracerlib.mq.XPrinter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Random


const val TAGG = "TAGG"

/**
 * 整体流程
 *
 */
class MainActivity : AppCompatActivity() {

    private var sliver: Sliver? = null
    private lateinit var xPrinter: XPrinter
    private var sdf = SimpleDateFormat("yyMMddHHmmsssss")
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_layout)
        sliver = Sliver()
        xPrinter = XPrinter(sliver!!)
        Looper.getMainLooper().setMessageLogging(xPrinter)
    }

    open fun getMethodsStackTrace(v: View) {
        val methodIds = sliver?.getMethodFrames(Looper.getMainLooper().thread)
        if (methodIds != null && methodIds.isNotEmpty()) {
            val methodStackList = sliver?.prettyMethods(methodIds)
            methodStackList?.forEach {
                Log.d("TAGG", it)
            }
        }
    }

    private fun getMethodFrames(thread: Thread) {
        Log.d(TAGG, "<- call getMethodsStackTrace")
        val methodIdList = sliver?.getMethodFrames(thread)
        if (methodIdList != null && methodIdList.isNotEmpty()) {
            sliver?.prettyMethods(methodIdList)
        }
    }

    fun dumpNextMessages(v:View){

        val handler = Handler(Looper.getMainLooper())
        for (i in 0..9){
            val runnable = DelayRunnable(i)
            Log.i("TAGG","runnable.hashCode:${runnable.hashCode()}")
            handler.post(runnable)
        }
        Log.i("TAGG","post completed...")
        xPrinter.dumpPendingMessages()
    }

    /**
     * 模拟在UI线程执行单次耗时操作
     */
    fun sleepThread(view: View) {
        Log.d(TAGG, "sleep start...")
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        Log.d(TAGG, "sleep end...")
    }

    fun postMultiHeavyMethod(view:View){
        for (i in 0..10){
            handler.post(MyRunnable())
        }
    }

    inner class MyRunnable:Runnable{
        override fun run() {
            var rNumber = Random().nextInt(10)
            if(rNumber==0){
                rNumber = 1
            }
            Log.d(TAGG,"rNumber:$rNumber")
            try {
                Thread.sleep(rNumber*1000L)
            }catch (e:Exception){
                e.printStackTrace()
            }
        }
    }

    fun showMetricResult(v: View) {
        val timeStamp = generateTimeStamp()
        xPrinter.retrievedData(this, "MQ${timeStamp}.json")
        sliver?.dump(File(filesDir.absoluteFile,"stack${timeStamp}.txt").absolutePath)
    }


    private fun generateTimeStamp():String{
        return sdf.format(Date(System.currentTimeMillis()))
    }

    inner class DelayRunnable(private val index: Int) : Runnable {
        override fun run() {
            Thread.sleep(3000)
        }
    }
}