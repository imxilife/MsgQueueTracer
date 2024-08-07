package me.kelly.mqtracerlib.mq

import android.content.Context
import android.os.Debug
import android.os.Looper
import android.os.Message
import android.os.MessageQueue
import android.os.SystemClock
import android.util.Log
import android.util.Printer
import com.google.gson.Gson
import me.kelly.mqtracerlib.stack.Sliver
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.reflect.Field
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger

private const val MESSAGE_START = ">>>>> Dispatching to "
private const val MESSAGE_END = "<<<<< Finished to "

const val TAGG = "TAGG"

/**
 * MessageQueue 回溯流程
 * 1. 历史和pending消息回溯
 * 2. 超时消息执行堆栈捕获
 */
class XPrinter(private val sliver: Sliver) : Printer {

    private val _300MS = 300
    private var _100MS = 100 //超过阈值的1/3
    private val MAX_MSG_ARRAY_SIZE = 100
    private val MSG_MONITOR_INTERVAL = 25 //堆栈捕获超时监控周期
    private val MSG_EXECUTE_TIMEOUT_MS = 100
    private val MSG_MONITOR_INTERVAL_TEST = 1000 //todo for test

    private var wallTimeStart = 0L
    private var cpuTimeStart = 0L
    private var recordArray = arrayOfNulls<Packet>(MAX_MSG_ARRAY_SIZE)
    private var msgCount = 0
    private var msgCountTime = 0
    private var index = 0
    private var stringBuilder = StringBuilder(100)
    private var messageField: Field? = null
    private var hookMQObj: MessageQueue? = null
    private var isEnablePrint = false
    private var isStart = false
    private var timeStamp = 0L
    private var sequence = AtomicInteger(0)
    private var targetTimestamp = 0L

    @Volatile
    private var isStopRecord = false

    override fun println(log: String?) {

        if (log?.startsWith(MESSAGE_START) == true) {
            //1. begin start
            //timeStamp = SystemClock.elapsedRealtime()
            wallTimeStart = SystemClock.uptimeMillis()
            cpuTimeStart =
                SystemClock.currentThreadTimeMillis() //当前线程running，不包括休眠时间 参考:https://juejin.cn/post/6844904147628589070#4
            targetTimestamp = wallTimeStart + MSG_EXECUTE_TIMEOUT_MS
            sliver.updateTargetTime(targetTimestamp) //target_time = wall_start + 100
            //2.start monitor
            if (!isStart) {
                isStart = true
                sliver.start(
                    Looper.getMainLooper().thread,
                    sliver.getNativePeer(Looper.getMainLooper().thread),
                    MSG_MONITOR_INTERVAL
                )
            }
        }

        if (log?.startsWith(MESSAGE_END) == true) {
            sliver.end()
            val wallTime = SystemClock.uptimeMillis() - wallTimeStart //WallTime包括消息执行中被等待+消息执行时间
            val cpuTime = SystemClock.currentThreadTimeMillis() - cpuTimeStart //cpuTime 线程running时间
            val waitTime = wallTime - cpuTime //等待时间
            record(wallTime.toInt(), waitTime.toInt())
        }
    }

    //历史消息回溯 + pending消息记录
    //300ms * 100组
    private fun record(wallTime: Int, cpuTime: Int) {

        /**
         * 1. 多消息聚合 每条消息<=100ms 累计超过300ms
         * 2. 消息拆分 单条消息>100ms && <300ms, 累计超过300
         * 3. 单条消息>=300ms
         */
        if(isStopRecord) return
        val curMsg = getMessageObj()
        //>300
        if (wallTime >= _300MS) {
            val msgRecord = Packet()
            if (msgCount == 0) {
                msgRecord.count = 1
                msgRecord.wallTime = wallTime
                msgRecord.waitTime = cpuTime
                msgRecord.info = "$curMsg"
                msgRecord.sequence = sequence.incrementAndGet()
                msgRecord.targetTS = targetTimestamp
                recordArray[index % MAX_MSG_ARRAY_SIZE] = msgRecord
                index++
            } else {  //compare two record
                msgRecord.count = msgCount
                msgRecord.totalTime = msgCountTime
                msgRecord.sequence = sequence.incrementAndGet()
                recordArray[index % MAX_MSG_ARRAY_SIZE] = msgRecord
                recordArray[++index % MAX_MSG_ARRAY_SIZE] = Packet(
                    count = 1,
                    wallTime = wallTime,
                    waitTime = cpuTime,
                    info = "$curMsg"
                )
                resetCount()
                index++
            }
            if (isEnablePrint) {
                debugDumpRecord()
            }
        } else {
            //(100,300]
            //<100
            if (msgCount == 0) {
                stringBuilder.append("[")
            }
            ++msgCount
            if (wallTime >= _100MS) {
                if (!stringBuilder.contains("heavy_msg|")) {
                    stringBuilder.append("heavy_msg|")
                }
                stringBuilder.append("${curMsg?.toString()}")
                    .append(" | wallTime:$wallTime")
                    .append(" | waitTime:$cpuTime")
                    .append(" | targetTimestamp:$targetTimestamp")
                    .append(";\n")
                Log.e(TAGG,"<- 记录超100ms消息")
            }
            msgCountTime += wallTime
            if (msgCountTime >= _300MS) {
                stringBuilder.append("]")
                val msgRecord = Packet(
                    count = msgCount,
                    totalTime = msgCountTime,
                    info = stringBuilder.toString(),
                    sequence = sequence.incrementAndGet(),
                )
                recordArray[index % MAX_MSG_ARRAY_SIZE] = msgRecord
                resetCount()
                index++
                Log.d(TAGG, "index:$index | record.size:${recordArray.size}")
                if (isEnablePrint) {
                    debugDumpRecord()
                }
            }
        }
    }

    private fun resetCount() {
        msgCountTime = 0
        msgCount = 0
        stringBuilder.setLength(0)
    }

    private fun formatMessageStr(msgStr: String): String {
        return if (msgStr.contains(MESSAGE_END) || msgStr.contains(MESSAGE_START)) {
            return msgStr.replace(MESSAGE_START, "").replace(MESSAGE_END, "")
        } else {
            msgStr
        }
    }

    private fun debugDumpRecord() {
        recordArray.forEachIndexed { index, packet ->
            packet?.let {
                Log.e(TAGG, "index:$index | record:$packet")
            }
        }
    }

    private data class Packet(
        /**CPU耗时*/
        var waitTime: Int = 0,

        /**总耗时*/
        var wallTime: Int = 0,

        /**本条记录有多少条message*/
        var count: Int = 0,

        /**该条消息内容*/
        var info: String = "",

        /**时间点*/
        //var timeStamp: Long = 0,

        /**消息序号*/
        var sequence: Int = 0,

        /**消息总耗时*/
        var totalTime: Int = 0,

        var targetTS:Long = 0L,

        var pendingMessageCount:Int = 0

    ):Serializable {
        companion object{
            private const val serialVersionUUID = -1928374650912345678L
        }
    }

    private fun getMessageObj(): Message? {
        if (hookMQObj == null || messageField == null) {
            hookMessageField()
        }
        return try {
            messageField?.get(hookMQObj) as Message?   // curMsg 现在包含了当前的Message对象或者如果没有则为null
        } catch (e: Exception) {
            null
        }
    }

    private fun hookMessageField() {
        // 获取Looper主线程的实例
        val mainLooper = Looper.getMainLooper()
        // 通过反射获取MessageQueue字段
        val mqField = Looper::class.java.getDeclaredField("mQueue")
        mqField.isAccessible = true
        // 从Looper实例获取MessageQueue实例
        hookMQObj = mqField.get(mainLooper) as MessageQueue
        // 通过反射获取Message字段
        messageField = MessageQueue::class.java.getDeclaredField("mMessages")
        messageField?.isAccessible = true
    }

    fun dumpPendingMessages():Int {
        var curMsg: Message? = getMessageObj() ?: return 0
        var next: Message?
        var pendingCount = 0
        while (curMsg != null) {
            next = getNextMessage(curMsg)
            if (isEnablePrint) {
                Log.d(
                    "TAGG",
                    "PendingMessage:${next?.toString()}|PendingMessage.callback.hashCode:${next?.callback.hashCode()}"
                )
            }
            curMsg = next
            ++pendingCount
        }
        return pendingCount
    }

    /**
     * 回收数据
     * todo 数据格式优化为protobuf
     */
    fun retrievedData(context:Context,fileName:String):Boolean{
        isStopRecord = true
        return try {
            val file = File(context.filesDir,fileName)
            if(file.exists()||!file.isFile){
                file.delete()
                Log.d(TAGG,"文件已存在做删除处理")
            }
            if(!file.exists()){
                file.createNewFile()
                Log.i(TAGG,"文件不存在重新创建...")
            }
            val count = dumpPendingMessages()
            val packet = Packet(
                pendingMessageCount = count
            )
            if (recordArray[index] == null) {
                recordArray[index] = packet
            }else{
                recordArray[++index] = packet
            }
            val gson = Gson()
            val json = gson.toJson(recordArray)
            file.writeText(json, Charset.forName("UTF-8"))
            Log.i(TAGG,"写入完成")
            true
        }catch (e:IOException){
            e.printStackTrace()
            Log.e(TAGG,"写入失败，err:${e.message}")
            false
        }
    }

    private fun getNextMessage(msg: Message): Message? {
        val nextField = Message::class.java.getDeclaredField("next")
        nextField.isAccessible = true
        val nextMsg = nextField.get(msg) as? Message
        return nextMsg
    }
}