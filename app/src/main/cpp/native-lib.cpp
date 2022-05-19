#include <jni.h>
#include <string>
#include <android/log.h>

#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,"rtmp-jni",__VA_ARGS__)
extern "C" {
#include "librtmp/rtmp.h"
}
typedef struct {
    RTMP *rtmp;
    int16_t sps_len;
    int8_t *sps;
    int16_t pps_len;
    int8_t *pps;
} Live;
Live *live = NULL;

int sendVideo(jbyte *data, jint len, jlong tms);

void prepareVideo(jbyte *data, jint len, Live *live);

RTMPPacket *createVideoPackage0(Live *live);

RTMPPacket *createVideoPackage1(jbyte *data, jint len, jlong tms, Live *live);

int sendPacket(RTMPPacket *pPacket);

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_rtmppush_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

int sendVideo(jbyte *data, jint len, jlong tms) {
    int ret;
    LOGW("sendVideo %02X", data[4]);

    if (data[4] == 0x67) {// sps pps
        if (live && (!live->pps || !live->sps)){
            prepareVideo(data, len, live);
        }
    } else {
        if (data[4] == 0x65) {
            LOGW("sendVideo %s", "0");
            RTMPPacket *packet = createVideoPackage0(live);
            if (!(ret = sendPacket(packet))) {

            }
        }

        LOGW("sendVideo %s", "1");
        RTMPPacket *packet = createVideoPackage1(data, len, tms, live);
        ret = sendPacket(packet);
    }

    return ret;
}

int sendPacket(RTMPPacket *packet) {
    int r = RTMP_SendPacket(live->rtmp, packet, 1);
    RTMPPacket_Free(packet);
    free(packet);
    return r;
}

RTMPPacket *createVideoPackage1(jbyte *data, jint len, jlong tms, Live *live) {
    //去掉分隔符
    data += 4;
    len -= 4;

    int body_size = len + 9;
    RTMPPacket *packet = static_cast<RTMPPacket *>(malloc(sizeof(RTMPPacket)));
    RTMPPacket_Alloc(packet, len + 9);
    packet->m_body[0] = 0x27;
    if (data[0] == 0x65) {
        packet->m_body[0] = 0x17;
        LOGW("Keyframe");
    }
    packet->m_body[1] = 0x01;
    packet->m_body[2] = 0x00;
    packet->m_body[3] = 0x00;
    packet->m_body[4] = 0x00;
    //长度
    packet->m_body[5] = (len >> 24) & 0xff;
    packet->m_body[6] = (len >> 16) & 0xff;
    packet->m_body[7] = (len >> 8) & 0xff;
    packet->m_body[8] = len & 0xff;

    memcpy(&packet->m_body[9], data, len);

    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = body_size;
    packet->m_nChannel = 0x04;
    packet->m_nTimeStamp = tms;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = live->rtmp->m_stream_id;
    return packet;
}

RTMPPacket *createVideoPackage0(Live *live) {
    LOGW("createVideoPackage0 %p", live);
    int body_size = 13 + live->sps_len + 3 + live->pps_len;
    RTMPPacket *packet = static_cast<RTMPPacket *>(malloc(sizeof(RTMPPacket)));
    RTMPPacket_Alloc(packet, body_size);
    int i = 0;
    //AVC sequence header 与IDR一样
    packet->m_body[i++] = 0x17;
    //AVC sequence header 设置为0x00
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    //AVC sequence header
    packet->m_body[i++] = 0x01;//版本号1
    packet->m_body[i++] = live->sps[1]; // profile
    packet->m_body[i++] = live->sps[2];
    packet->m_body[i++] = live->sps[3];// profile level
    packet->m_body[i++] = 0xFF;

    //sps
    packet->m_body[i++] = 0xE1;
    packet->m_body[i++] = (live->sps_len >> 8) & 0xff;
    packet->m_body[i++] = live->sps_len & 0xff;

    memcpy(&packet->m_body[i], live->sps, live->sps_len);
    i += live->sps_len;

    //pps
    packet->m_body[i++] = 0x01;
    packet->m_body[i++] = (live->pps_len >> 8) & 0xff;
    packet->m_body[i++] = live->pps_len & 0xff;
    memcpy(&packet->m_body[i], live->pps, live->pps_len);

    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = body_size;
    packet->m_nChannel = 0x04;
    packet->m_nTimeStamp = 0;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = live->rtmp->m_stream_id;
    return packet;
}

void prepareVideo(jbyte *data, jint len, Live *live) {
    LOGW("prepareVideo %p %d %p", data, len, live);
    for (int i = 0; i < len; ++i) {
        if (i + 4 < len) {
            //分隔符
            if (data[i] == 0x00
                && data[i + 1] == 0x00
                && data[i + 2] == 0x00 &&
                data[i + 3] == 0x01) {
                //sps pps
                if (data[i + 4] == 0x68) {
                    //去掉界定符
                    live->sps_len = i - 4;
                    live->sps = static_cast<int8_t *>(malloc(live->sps_len));
                    memcpy(live->sps, data + 4, live->sps_len);

                    live->pps_len = len - (4 + live->sps_len) - 4;
                    live->pps = static_cast<int8_t *>(malloc(live->pps_len));
                    memcpy(live->pps, data + 4 + live->sps_len + 4, live->pps_len);
                    LOGW("sps:%d pps:%d", live->sps_len, live->pps_len);
                    break;
                }
            }
        }
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_rtmppush_rtmp_PushRtmp_sendData(JNIEnv *env, jobject thiz, jbyteArray data_, jint len,
                                            jlong tms) {
    int ret;

    jbyte *data = env->GetByteArrayElements(data_, NULL);
    ret = sendVideo(data, len, tms);
    env->ReleaseByteArrayElements(data_, data, 0);
    return ret;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_rtmppush_rtmp_PushRtmp_connect(JNIEnv *env, jobject thiz, jstring url_) {
    const char *url = env->GetStringUTFChars(url_, 0);
    int ret;
    do {
        live = static_cast<Live *>(malloc(sizeof(Live)));
        memset(live, 0, sizeof(Live));
        live->rtmp = RTMP_Alloc();
        RTMP_Init(live->rtmp);
        live->rtmp->Link.timeout = 100000;
        LOGW("connect %s", url);
        if (!(ret = RTMP_SetupURL(live->rtmp, (char *) url))) break;
        RTMP_EnableWrite(live->rtmp);
        LOGW("RTMP_Connect");
        if (!(ret = RTMP_Connect(live->rtmp, 0))) break;
        LOGW("RTMP_ConnectStream");
        if (!(ret = RTMP_ConnectStream(live->rtmp, 0))) break;
        LOGW("connect success");
    } while (0);
    if (!ret && live) {
        free(live);
        live = nullptr;
    }
    env->ReleaseStringUTFChars(url_, url);
    return ret;
}