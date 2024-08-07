//
// Created by Kelly on 2024/5/19.
//

#include "fetch_stack_visitor.h"
#include "xdl.h"
#include <jni.h>
#include "art.h"
#include "sliver/fetch_stack_visitor.h"
#include "vector"
#include <chrono>
#include "logger.h"
#include "art_thread.h"
#include "pthread.h"
#include "ctime"
#include "thread"
#include "chrono"
#include "mutex"
#include "atomic"
#include "time.h"


#define SLIVER_JNI_CLASS_NAME  "me/kelly/xstackhook/stack/Sliver"

#define TAG "TAGG"

using namespace xlart;

/**
 * 获取栈信息
 * @param env
 * @param cls
 * @param thread
 * @param native_peer
 * @return
 */

#define TIME_OUT_MS 20 //20ms

static jlong targetTime;
static jlong lastTime;

static std::mutex targetTimeMutex;
static std::atomic<bool> isRunning(false);

using namespace std::chrono;

static void
start(JNIEnv *env, jobject cls, jobject thread, jlong thread_peer, jint sample_interval);

void *monitor(void *args);

static jlongArray
nativeGetMethodStack(JNIEnv *env, jobject cls, jobject thread_peer, jlong native_peer);

static jobjectArray prettyMethods(JNIEnv *env, jobject cls, jlongArray methods);

static void printJObjectArray(JNIEnv* env,jobjectArray array);

static jlong getTime(JNIEnv* env, jobject cls);

static void testPowerState();

static void end(JNIEnv *env,jobject cls);

void stop();

JavaVM* gJvm = nullptr;

typedef struct {
    jobject cls;
    jobject thread;
    jlong thread_peer;
    jint sample_interval;
} threadArgs;

static void start(JNIEnv *env, jobject cls, jobject thread, jlong thread_peer,jint sample_interval) {
    if (isRunning) {
        LOGI(TAG, "is running return...");
        return;
    }
    LOGD(TAG, "start stack monitor...");
    isRunning = true;
    threadArgs* args = new threadArgs {cls, thread, thread_peer,sample_interval};
    pthread_t pthread;
    int result = pthread_create(&pthread, nullptr, monitor, (void *) args);
    if (result) {
        LOGE(TAG, "error create thread fail... %d\n", result);
    }
    pthread_detach(result);
}

void* monitor(void *args) {

    JNIEnv * env;
    threadArgs *paramPtr = static_cast<threadArgs *>(args);
    if (paramPtr == nullptr) {
        LOGE(TAG, "paramPtr is NULL...");
        return nullptr;
    }

    if (gJvm != nullptr) {
        if (gJvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
            if (gJvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
                LOGE(TAG,"sample thread attach failed...");
                return nullptr;
            }
        }
    }

    while (isRunning) {
        auto now = getTime(nullptr, nullptr);
        //休眠后唤醒判断当前时间是否>超时时间如果是 则dump堆栈，否则继续休眠(目标时间减去-当前时间)，等待下次唤醒
        //int timeDuration = duration_cast<milliseconds>(targetTime - steady_clock::now()).count();
        //std::this_thread::sleep_for(milliseconds(25L)); //sleep xx ms
        std::unique_lock<std::mutex> lock(targetTimeMutex);
        if (now > targetTime && lastTime != targetTime) {
            //抓栈
            LOGE(TAG, "start dump stack... 实际时间:%lld | 预期完成时间:%lld | 差值:%lld |上一次时间值:%lld", now, targetTime,
                 (now - targetTime),lastTime);
            jlongArray methodArray = nativeGetMethodStack(env, paramPtr->cls, paramPtr->thread,paramPtr->thread_peer);
            jobjectArray methods = prettyMethods(env,paramPtr->cls,methodArray);
            printJObjectArray(env,methods);
            lastTime = targetTime;
        }
        lock.unlock();
        auto end = getTime(nullptr, nullptr);
        auto diff = end - now;
        auto sleepTime = paramPtr->sample_interval - diff;
        sleepTime = sleepTime > 0 ? sleepTime : paramPtr->sample_interval;
        std::this_thread::sleep_for(milliseconds(sleepTime));
    }
    if (gJvm != nullptr) {
        gJvm->DetachCurrentThread();
    }
    return nullptr;
}

static void testPowerState(){
    // 执行 dumpsys power 命令并读取完整输出
    FILE* fp = popen("dumpsys power", "r");
    if (fp != nullptr) {
        char buffer[256];
        std::string powerState;
        while (fgets(buffer, sizeof(buffer), fp) != nullptr) {
            powerState += buffer;
        }
        pclose(fp);

        // 输出完整的电源状态信息以供调试
        LOGD(TAG, "Complete power state: %s", powerState.c_str());

        // 查找特定字符串，如 "mWakefulness"
        size_t pos = powerState.find("mWakefulness");
        if (pos != std::string::npos) {
            // 提取并输出电源状态行
            size_t endLine = powerState.find('\n', pos);
            std::string wakefulnessLine = powerState.substr(pos, endLine - pos);
            LOGD(TAG, "Power state: %s", wakefulnessLine.c_str());
        } else {
            LOGD(TAG, "mWakefulness not found in power state");
        }
    } else {
        LOGE(TAG, "Failed to execute dumpsys power command");
    }
}

static void printJObjectArray(JNIEnv* env,jobjectArray array){
    jsize arrayLength = env->GetArrayLength(array);

    for (jsize i = 0; i < arrayLength; ++i) {
        jstring jstr = (jstring) env->GetObjectArrayElement(array, i);
        if (jstr != nullptr) {
            const char* str = env->GetStringUTFChars(jstr, nullptr);
            if (str != nullptr) {
                LOGD(TAG,"str:%s",str);
                env->ReleaseStringUTFChars(jstr, str);
            }
            env->DeleteLocalRef(jstr);
        }
    }
}

static jlongArray
nativeGetMethodStack(JNIEnv *env, jobject cls, jobject thread_peer, jlong native_peer) {

    bool timeOut;

    auto *thread = reinterpret_cast<Thread *>(native_peer); //java mapping native thread obj
    Thread *cur_thread = Thread::current();

    bool isSameThread = false;
    if (cur_thread == thread) {
        isSameThread = true;
    }

    //step1 挂起要抓栈的线程
    if (!isSameThread) {
        LOGI(TAG,"suspend thread:%p",thread);
        ArtHelper::SuspendThreadByThreadId(thread->GetThreadId(), SuspendReason::kForUserCode,&timeOut);
    }

    /******test begin*******/
/*    if (!isSameThread) {
        LOGI(TAG,"resume thread:%p",thread);
        ArtHelper::Resume(thread, SuspendReason::kForUserCode);
    }*/
    /*******test end*******/

    std::vector<std::uintptr_t> stack_methods;
    auto fn = [](ArtMethod *method, void *visitorData) -> bool {
        auto *methods = reinterpret_cast<std::vector<std::uintptr_t> *>(visitorData);
        methods->push_back(reinterpret_cast<std::uintptr_t>(&*method));
        return true;
    };

    //step2 调用walkStack
    auto visitor = new FetchStackVisitor(thread,
                                         &stack_methods,
                                         fn);
    ArtHelper::StackVisitorWalkStack(visitor, false);

    //step3 恢复线程
    if (!isSameThread) {
        LOGI(TAG,"resume thread:%p",thread);
        ArtHelper::Resume(thread, SuspendReason::kForUserCode);
    }

    //for test???
    //std::vector<double> results(4);
    //jdoubleArray output = env->NewDoubleArray(4);
    //env->SetDoubleArrayRegion(output,0,4,&results[4]);

    jlongArray methodArray = env->NewLongArray((jsize) stack_methods.size());
    jlong *destElements = env->GetLongArrayElements(methodArray, nullptr);
    size_t count = stack_methods.size();
    LOGD(TAG,"stack methods count:%d",count);
    for (int i = 0; i < count; i++) {
        destElements[i] = (jlong) stack_methods[i];
    }
    env->ReleaseLongArrayElements(methodArray, destElements, 0);
    return methodArray;
}

/**
 * @param env
 * @param cls
 * @param methods
 * @return
 */
static jobjectArray prettyMethods(JNIEnv *env, jobject cls, jlongArray methods) {

    jlong *method_ptr = env->GetLongArrayElements(methods, nullptr); //在Native中无法直接访问Java中的数据，这里要桥接下
    jsize size = env->GetArrayLength(methods);
    jobjectArray ret = env->NewObjectArray(size, env->FindClass("java/lang/String"), nullptr);
    for (int i = 0; i < size; ++i) {
        const std::string &pretty_method = ArtHelper::PrettyMethod(
                reinterpret_cast<void *>(method_ptr[i]), true);
        /* if(pretty_method.empty()){
             LOGE("TAGG","pretty_methods is empty...");
         }*/

        pretty_method.c_str();
        jstring frame = env->NewStringUTF(pretty_method.c_str());
        env->SetObjectArrayElement(ret, i, frame);
        env->DeleteLocalRef(frame);
    }
    env->ReleaseLongArrayElements(methods, method_ptr, 0);
    return ret;
}


void stop() {
    isRunning = false;
}


static void updateTargetTime(JNIEnv *env, jobject cls, jlong msgTargetTime) {
    std::unique_lock<std::mutex> lock(targetTimeMutex);
    targetTime = msgTargetTime;
    lastTime = 0L;
    jlong now = getTime(env,cls);
    LOGI(TAG,"updateTarget MTT:%lld | NOW:%lld | MTT-NOW:%lld",msgTargetTime,now,(msgTargetTime-now));
}

/**
 * 返回当前时间 毫秒
 * @return
 */
static jlong getTime(JNIEnv* env, jobject cls) {
    timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    jlong currentTime = static_cast<jlong>(ts.tv_sec) * 1000L + static_cast<jlong>(ts.tv_nsec) / 1000000L;
    return currentTime;
}


static void dump(JNIEnv *env, jobject cls) {
    LOGD(TAG,"dump");

}

static void end(JNIEnv *env,jobject cls){
    std::unique_lock<std::mutex> lock(targetTimeMutex);
    lastTime = targetTime;
}


static JNINativeMethod jniMethods[] = {{"nativeGetMethodStackTrace", "(Ljava/lang/Thread;J)[J", (void *) nativeGetMethodStack},
                                       {"prettyMethods",             "([J)[Ljava/lang/String;", (void *) prettyMethods},
                                       {"start",                     "(Ljava/lang/Thread;JI)V",                     (void *) start},
                                       {"dump",                      "()V",                     (void *) dump},
                                       {"updateTargetTime","(J)V",(void*) updateTargetTime},
                                       {"nativeGetTime", "()J",(void*) getTime},
                                       {"end","()V",(void*)end}};


JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {

    LOGD(TAG, "JNI_OnLoad...");

    JNIEnv *env;
    jclass cls;
    if (vm == nullptr) return JNI_ERR;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    if (nullptr == env) return JNI_ERR;
    if ((cls = env->FindClass(SLIVER_JNI_CLASS_NAME)) == nullptr) return JNI_ERR;
    env->RegisterNatives(cls, jniMethods, sizeof(jniMethods) / sizeof(jniMethods[0]));
    env->GetJavaVM(&gJvm);
    ArtHelper::init(env);
    return JNI_VERSION_1_6;
}



