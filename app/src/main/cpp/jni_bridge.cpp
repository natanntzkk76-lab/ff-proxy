// jni_bridge.cpp — exposes entity scan to the Java overlay + aimbot
#include <jni.h>
#include <string>
#include <vector>
#include "memory_engine.cpp"

static EntityReader* g_reader = nullptr;

// Called once with the game module base (GameAssembly.dll / libil2cpp.so)
extern "C" JNIEXPORT void JNICALL
Java_com_breadcrumb_ffproxy_MemoryEngine_init(JNIEnv* env, jobject thiz,
                                              jlong base, jint w, jint h) {
    if (g_reader) delete g_reader;
    g_reader = new EntityReader(static_cast<uintptr_t>(base), w, h);
}

// Returns a flat float array: [headX, headY, hipX, hipY, team, isFiring, ...]
// and a parallel string array for names.
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_breadcrumb_ffproxy_MemoryEngine_scan(JNIEnv* env, jobject thiz) {
    std::vector<Entity> ents;
    if (!g_reader || !g_reader->getEntities(ents)) {
        jfloatArray empty = env->NewFloatArray(0);
        return empty;
    }
    // 6 floats per entity: headX, headY, hipX, hipY, team, isFiring
    jfloatArray arr = env->NewFloatArray(ents.size() * 6);
    float* buf = env.GetFloatArrayElements(arr, nullptr);
    for (size_t i = 0; i < ents.size(); i++) {
        buf[i*6+0] = ents[i].screenHeadX;
        buf[i*6+1] = ents[i].screenHeadY;
        buf[i*6+2] = ents[i].screenHipX;
        buf[i*6+3] = ents[i].screenHipY;
        buf[i*6+4] = static_cast<float>(ents[i].team);
        buf[i*6+5] = ents[i].isFiring ? 1.0f : 0.0f;
    }
    env.SetFloatArrayRegion(arr, 0, ents.size() * 6, buf);
    env.ReleaseFloatArrayElements(arr, buf, 0);
    return arr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_breadcrumb_ffproxy_MemoryEngine_getName(JNIEnv* env, jobject thiz, jint index) {
    std::vector<Entity> ents;
    if (g_reader && g_reader->getEntities(ents) && index < (int)ents.size())
        return env->NewStringUTF(ents[index].name.c_str());
    return env->NewStringUTF("");
}
