package me.kelly.mqtracerlib.stack

import android.util.Log

class Sliver {

    companion object{
        init {
            try {
                System.loadLibrary("stackHook")
                Log.i("TAGG","load stackhook library succeed...")
            }catch (e:Exception){
                e.printStackTrace()
            }
        }
    }

    private external fun nativeGetMethodStackTrace(thread:Thread,nativePeer:Long):LongArray

    external fun start(thread: Thread,nativePeer: Long,sample_interval:Int)

    external fun end()

    external fun nativeGetTime():Long

    external fun dump(path:String)

    external fun updateTargetTime(targetTime:Long)

    external fun prettyMethods(methodIds: LongArray):Array<String>

    external fun stop()

    fun getMethodFrames(thread: Thread): LongArray {
        val threadPeer = getNativePeer(thread)
        return nativeGetMethodStackTrace(thread, threadPeer)
    }

    fun getStackTrace(thread:Thread):String{
        val nativePeer = getNativePeer(thread)
        val frames = nativeGetMethodStackTrace(thread,nativePeer)
        val methods = prettyMethodsAsString(frames)
        val stringBuilder = StringBuilder(100)
        methods.forEach {
            stringBuilder.append(it).append("\n")
        }
        if(stringBuilder.isNotEmpty()){
            stringBuilder.deleteCharAt(stringBuilder.length-1)
        }
        return stringBuilder.toString()
    }

    fun prettyMethodsAsString(frames:LongArray):List<String>{
        return listOf()
    }

    fun getNativePeer(thread: Thread): Long {
        return try {
            val nativePeerField = Thread::class.java.getDeclaredField("nativePeer")
            nativePeerField.isAccessible = true
            nativePeerField.get(thread) as Long
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            -1L
        }
    }

}