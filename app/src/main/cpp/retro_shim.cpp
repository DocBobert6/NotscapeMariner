// Notscape Mariner - libretro frontend shim (Phase J).
//
// One JNI library that hosts a libretro core (loaded by
// path at runtime - cores are separate .so files, fetched
// and hash-pinned by CI, never linked in): environment /
// video / audio / input callbacks, an EGL/GLES2 presenter
// with letterboxing, and an oboe output stream fed from
// a lock-free ring. Written for the mGBA core first
// (Game Boy / Color / Advance); nothing here is
// core-specific beyond the environment answers.

#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <dlfcn.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <map>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <oboe/Oboe.h>
#include "libretro.h"

#define LOG_TAG "NotscapeRetro"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// ---------------------------------------------------------------- core

struct Core {
    void* handle = nullptr;
    decltype(&retro_set_environment) set_environment = nullptr;
    decltype(&retro_set_video_refresh) set_video_refresh = nullptr;
    decltype(&retro_set_audio_sample) set_audio_sample = nullptr;
    decltype(&retro_set_audio_sample_batch) set_audio_sample_batch = nullptr;
    decltype(&retro_set_input_poll) set_input_poll = nullptr;
    decltype(&retro_set_input_state) set_input_state = nullptr;
    decltype(&retro_init) init = nullptr;
    decltype(&retro_deinit) deinit = nullptr;
    decltype(&retro_api_version) api_version = nullptr;
    decltype(&retro_get_system_info) get_system_info = nullptr;
    decltype(&retro_get_system_av_info) get_system_av_info = nullptr;
    decltype(&retro_reset) reset = nullptr;
    decltype(&retro_run) run = nullptr;
    decltype(&retro_load_game) load_game = nullptr;
    decltype(&retro_unload_game) unload_game = nullptr;
    decltype(&retro_get_memory_data) get_memory_data = nullptr;
    decltype(&retro_get_memory_size) get_memory_size = nullptr;
    decltype(&retro_serialize_size) serialize_size = nullptr;
    decltype(&retro_serialize) serialize = nullptr;
    decltype(&retro_unserialize) unserialize = nullptr;
    bool loaded = false;
};

Core g_core;
// Core-option values for this launch (key=value pairs)
// handed in at create). GET_VARIABLE answers from here;
// an unknown key returns false and the core uses its
// own default, so compile-gated options the shipped .so
// lacks are harmless either way.
std::map<std::string, std::string> g_coreOptions;
std::string g_systemDir;
std::string g_saveDir;
std::string g_sramPath;
retro_pixel_format g_pixelFormat = RETRO_PIXEL_FORMAT_XRGB8888;
double g_fps = 60.0;
int g_sampleRate = 48000;
unsigned g_baseW = 240, g_baseH = 160;

// Guards retro_run() against save/load: a serialize must
// never race a frame. Held for one frame at a time by
// the emu loop, and for the whole call by save/load.
std::mutex g_runMutex;
std::atomic<int32_t> g_inputMask{0};
std::atomic<bool> g_emuRunning{false};
std::atomic<bool> g_shutdownRequested{false};
// Pause menu (J9): the emu loop idles instead of
// running frames, and the audio callback plays
// silence. Fast-forward runs the loop at a
// multiple of the core rate (audio silenced too -
// fast audio would be garbled). Integer scaling
// snaps the presenter's fit scale down to a whole
// number so pixels stay uniform.
std::atomic<bool> g_paused{false};
std::atomic<bool> g_integerScale{false};
std::atomic<int> g_speedMul{1};
std::thread g_emuThread;

// ---------------------------------------------------------------- frame

std::mutex g_frameMutex;
std::vector<uint8_t> g_frameRgba;
unsigned g_frameW = 0, g_frameH = 0;
bool g_frameDirty = false;

// ---------------------------------------------------------------- audio

// Single-producer / single-consumer ring of stereo s16
// frames between the core's audio batch callback (emu
// thread) and oboe's data callback (audio thread).
class AudioRing {
public:
    static constexpr size_t kCapacity = 16384;
    void push(const int16_t* data, size_t frames) {
        for (size_t i = 0; i < frames; i++) {
            size_t w = write_.load(std::memory_order_relaxed);
            size_t next = (w + 1) % kCapacity;
            if (next == read_.load(std::memory_order_acquire)) {
                return; // full: drop the rest of this batch
            }
            buffer_[w * 2] = data[i * 2];
            buffer_[w * 2 + 1] = data[i * 2 + 1];
            write_.store(next, std::memory_order_release);
        }
    }
    size_t pull(int16_t* out, size_t frames) {
        size_t got = 0;
        while (got < frames) {
            size_t r = read_.load(std::memory_order_relaxed);
            if (r == write_.load(std::memory_order_acquire)) break;
            out[got * 2] = buffer_[r * 2];
            out[got * 2 + 1] = buffer_[r * 2 + 1];
            read_.store((r + 1) % kCapacity, std::memory_order_release);
            got++;
        }
        return got;
    }
    void clear() {
        read_.store(0, std::memory_order_release);
        write_.store(0, std::memory_order_release);
    }
private:
    int16_t buffer_[kCapacity * 2];
    std::atomic<size_t> read_{0};
    std::atomic<size_t> write_{0};
};

AudioRing g_audioRing;

class AudioOut : public oboe::AudioStreamDataCallback {
public:
    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* stream, void* audioData,
            int32_t numFrames) override {
        auto* out = static_cast<int16_t*>(audioData);
        if (g_paused.load() || g_speedMul.load() > 1) {
            // Drain the ring but play silence.
            g_audioRing.pull(out, static_cast<size_t>(numFrames));
            memset(out, 0, numFrames * 2 * sizeof(int16_t));
            return oboe::DataCallbackResult::Continue;
        }
        size_t got = g_audioRing.pull(out, static_cast<size_t>(numFrames));
        if (got < static_cast<size_t>(numFrames)) {
            memset(out + got * 2, 0,
                   (numFrames - got) * 2 * sizeof(int16_t));
        }
        return oboe::DataCallbackResult::Continue;
    }
    std::shared_ptr<oboe::AudioStream> stream;
};

AudioOut g_audioOut;
bool g_audioStarted = false;

bool audioStart() {
    if (g_audioStarted) return true;
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setFormat(oboe::AudioFormat::I16)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(g_sampleRate)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setDataCallback(&g_audioOut);
    oboe::Result result = builder.openStream(g_audioOut.stream);
    if (result != oboe::Result::OK || !g_audioOut.stream) {
        LOGE("oboe open failed: %s", oboe::convertToText(result));
        return false;
    }
    result = g_audioOut.stream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("oboe start failed: %s", oboe::convertToText(result));
        return false;
    }
    g_audioStarted = true;
    return true;
}

void audioStop() {
    if (g_audioOut.stream) {
        g_audioOut.stream->stop();
        g_audioOut.stream->close();
        g_audioOut.stream.reset();
    }
    g_audioStarted = false;
    g_audioRing.clear();
}

// ---------------------------------------------------------------- core callbacks

void coreLog(enum retro_log_level level, const char* fmt, ...) {
    char buf[512];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    int prio = ANDROID_LOG_INFO;
    if (level == RETRO_LOG_ERROR) prio = ANDROID_LOG_ERROR;
    else if (level == RETRO_LOG_WARN) prio = ANDROID_LOG_WARN;
    else if (level == RETRO_LOG_DEBUG) prio = ANDROID_LOG_DEBUG;
    __android_log_print(prio, LOG_TAG, "core: %s", buf);
}

bool environCb(unsigned cmd, void* data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *static_cast<const char**>(data) = g_systemDir.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *static_cast<const char**>(data) = g_saveDir.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY:
            *static_cast<const char**>(data) = g_systemDir.c_str();
            return true;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            g_pixelFormat = *static_cast<const enum retro_pixel_format*>(data);
            return true;
        case RETRO_ENVIRONMENT_SET_MESSAGE: {
            auto* msg = static_cast<const struct retro_message*>(data);
            if (msg && msg->msg) LOGI("message: %s", msg->msg);
            return true;
        }
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            auto* log = static_cast<struct retro_log_callback*>(data);
            log->log = &coreLog;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            auto* var = static_cast<struct retro_variable*>(data);
            var->value = nullptr;
            if (!var->key) return false;
            auto it = g_coreOptions.find(var->key);
            if (it == g_coreOptions.end()) return false;
            var->value = it->second.c_str();
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *static_cast<bool*>(data) = false;
            return true;
        case RETRO_ENVIRONMENT_SET_VARIABLES:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
        case RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO:
            return true;
        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            auto* geo = static_cast<const struct retro_game_geometry*>(data);
            if (geo) { g_baseW = geo->base_width; g_baseH = geo->base_height; }
            return true;
        }
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            auto* info = static_cast<const struct retro_system_av_info*>(data);
            if (info) {
                g_fps = info->timing.fps > 0 ? info->timing.fps : 60.0;
                if (info->timing.sample_rate > 0) {
                    g_sampleRate = static_cast<int>(info->timing.sample_rate);
                }
                g_baseW = info->geometry.base_width;
                g_baseH = info->geometry.base_height;
            }
            return true;
        }
        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            *static_cast<unsigned*>(data) = 0;
            return true;
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *static_cast<bool*>(data) = true;
            return true;
        case RETRO_ENVIRONMENT_SET_ROTATION:
            return true;
        case RETRO_ENVIRONMENT_GET_OVERSCAN:
            *static_cast<bool*>(data) = false;
            return true;
        case RETRO_ENVIRONMENT_SHUTDOWN:
            g_shutdownRequested.store(true);
            return true;
        default:
            return false;
    }
}

void videoCb(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (!data || data == RETRO_HW_FRAME_BUFFER_VALID) return;
    std::lock_guard<std::mutex> lock(g_frameMutex);
    g_frameRgba.assign(static_cast<size_t>(width) * height * 4, 0);
    const auto* src = static_cast<const uint8_t*>(data);
    for (unsigned y = 0; y < height; y++) {
        const uint8_t* row = src + y * pitch;
        uint8_t* dst = g_frameRgba.data() + static_cast<size_t>(y) * width * 4;
        for (unsigned x = 0; x < width; x++) {
            uint8_t r = 0, g = 0, b = 0;
            switch (g_pixelFormat) {
                case RETRO_PIXEL_FORMAT_XRGB8888: {
                    uint32_t px;
                    memcpy(&px, row + x * 4, 4);
                    r = static_cast<uint8_t>((px >> 16) & 0xFF);
                    g = static_cast<uint8_t>((px >> 8) & 0xFF);
                    b = static_cast<uint8_t>(px & 0xFF);
                    break;
                }
                case RETRO_PIXEL_FORMAT_RGB565: {
                    uint16_t px;
                    memcpy(&px, row + x * 2, 2);
                    r = static_cast<uint8_t>(((px >> 11) & 0x1F) * 255 / 31);
                    g = static_cast<uint8_t>(((px >> 5) & 0x3F) * 255 / 63);
                    b = static_cast<uint8_t>((px & 0x1F) * 255 / 31);
                    break;
                }
                case RETRO_PIXEL_FORMAT_0RGB1555: {
                    uint16_t px;
                    memcpy(&px, row + x * 2, 2);
                    r = static_cast<uint8_t>(((px >> 10) & 0x1F) * 255 / 31);
                    g = static_cast<uint8_t>(((px >> 5) & 0x1F) * 255 / 31);
                    b = static_cast<uint8_t>((px & 0x1F) * 255 / 31);
                    break;
                }
                default:
                    break;
            }
            dst[x * 4 + 0] = r;
            dst[x * 4 + 1] = g;
            dst[x * 4 + 2] = b;
            dst[x * 4 + 3] = 255;
        }
    }
    g_frameW = width;
    g_frameH = height;
    g_frameDirty = true;
}

void audioCb(int16_t left, int16_t right) {
    int16_t pair[2] = {left, right};
    g_audioRing.push(pair, 1);
}

size_t audioBatchCb(const int16_t* data, size_t frames) {
    g_audioRing.push(data, frames);
    return frames;
}

void inputPollCb() {}

int16_t inputStateCb(unsigned port, unsigned device, unsigned /*index*/,
                     unsigned id) {
    if (port == 0 && device == RETRO_DEVICE_JOYPAD) {
        return static_cast<int16_t>((g_inputMask.load() >> id) & 1);
    }
    return 0;
}

// ---------------------------------------------------------------- GL presenter

const char* kVertSrc =
    "attribute vec2 aPos;\n"
    "attribute vec2 aUv;\n"
    "varying vec2 vUv;\n"
    "void main() {\n"
    "  vUv = aUv;\n"
    "  gl_Position = vec4(aPos, 0.0, 1.0);\n"
    "}\n";
const char* kFragSrc =
    "precision mediump float;\n"
    "varying vec2 vUv;\n"
    "uniform sampler2D uTex;\n"
    "void main() {\n"
    "  gl_FragColor = texture2D(uTex, vUv);\n"
    "}\n";
// Shader pass (J8): the same texture, plus one
// look per display type, all aligned to GAME
// pixels (vUv * texture size, not window pixels,
// so upscaling never turns them into moire).
// Home consoles: scanlines + a gentle horizontal
// blend. Handhelds: a true LCD grid - thin dark
// lines between every game pixel - plus a slight
// blend. No column pattern anywhere: the earlier
// every-third-column mask read as large squares
// at phone scale, and a true grille cannot resolve
// cleanly there (three stripes per game pixel
// would be sub-pixel mush). The per-console values
// arrive as uniforms; the UI names the look and
// nothing more.
const char* kFragSrcShader =
    "precision mediump float;\n"
    "varying vec2 vUv;\n"
    "uniform sampler2D uTex;\n"
    "uniform float uScan;\n"
    "uniform float uGrid;\n"
    "uniform float uBlur;\n"
    "uniform float uTexelX;\n"
    "uniform float uTexW;\n"
    "uniform float uTexH;\n"
    "void main() {\n"
    "  vec3 c = texture2D(uTex, vUv).rgb;\n"
    "  if (uBlur > 0.0) {\n"
    "    vec3 l = texture2D(uTex, vUv - vec2(uTexelX, 0.0)).rgb;\n"
    "    vec3 r = texture2D(uTex, vUv + vec2(uTexelX, 0.0)).rgb;\n"
    "    c = mix(c, (l + c + r) / 3.0, uBlur);\n"
    "  }\n"
    "  float gy = vUv.y * uTexH;\n"
    "  float scan = 1.0 - uScan * (0.5 - 0.5 * sin(gy * 3.14159265));\n"
    "  float fx = fract(vUv.x * uTexW);\n"
    "  float fy = fract(vUv.y * uTexH);\n"
    "  float gx = smoothstep(0.0, 0.12, min(fx, 1.0 - fx));\n"
    "  float gy2 = smoothstep(0.0, 0.12, min(fy, 1.0 - fy));\n"
    "  float grid = 1.0 - uGrid * (1.0 - min(gx, gy2));\n"
    "  gl_FragColor = vec4(c * scan * grid, 1.0);\n"
    "}\n";

GLuint compileShader(GLenum type, const char* src) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);
    GLint ok = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetShaderInfoLog(shader, sizeof(log), nullptr, log);
        LOGE("shader: %s", log);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

std::mutex g_windowMutex;
ANativeWindow* g_window = nullptr;
std::atomic<bool> g_renderRunning{false};
// Portrait top inset in pixels (J7), set from Kotlin
// whenever the cutout value changes.
std::atomic<int> g_topInsetPx{0};
// New game frames consumed by the presenter (the FPS
// counter reads this; it counts fresh core frames,
// not repeated draws of the same texture).
std::atomic<long long> g_newFrames{0};
std::atomic<bool> g_shaderOn{false};
std::atomic<float> g_scan{0.f}, g_grid{0.f}, g_blur{0.f};
std::thread g_renderThread;

void renderLoop() {
    ANativeWindow* window;
    {
        std::lock_guard<std::mutex> lock(g_windowMutex);
        window = g_window;
    }
    if (!window) return;
    ANativeWindow_acquire(window);
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(display, nullptr, nullptr);
    const EGLint configAttribs[] = {
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8, EGL_NONE
    };
    EGLConfig config;
    EGLint numConfigs = 0;
    eglChooseConfig(display, configAttribs, &config, 1, &numConfigs);
    EGLint format;
    eglGetConfigAttrib(display, config, EGL_NATIVE_VISUAL_ID, &format);
    ANativeWindow_setBuffersGeometry(window, 0, 0, format);
    const EGLint ctxAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, ctxAttribs);
    EGLSurface surface = eglCreateWindowSurface(display, config, window, nullptr);
    eglMakeCurrent(display, surface, surface, context);

    GLuint vs = compileShader(GL_VERTEX_SHADER, kVertSrc);
    GLuint fs = compileShader(GL_FRAGMENT_SHADER, kFragSrc);
    GLuint program = glCreateProgram();
    glAttachShader(program, vs);
    glAttachShader(program, fs);
    glLinkProgram(program);
    GLint aPos = glGetAttribLocation(program, "aPos");
    GLint aUv = glGetAttribLocation(program, "aUv");
    GLuint fs2 = compileShader(GL_FRAGMENT_SHADER, kFragSrcShader);
    GLuint program2 = glCreateProgram();
    glAttachShader(program2, vs);
    glAttachShader(program2, fs2);
    glLinkProgram(program2);
    GLint aPos2 = glGetAttribLocation(program2, "aPos");
    GLint aUv2 = glGetAttribLocation(program2, "aUv");
    GLint uScan = glGetUniformLocation(program2, "uScan");
    GLint uGrid = glGetUniformLocation(program2, "uGrid");
    GLint uBlur = glGetUniformLocation(program2, "uBlur");
    GLint uTexelX = glGetUniformLocation(program2, "uTexelX");
    GLint uTexW = glGetUniformLocation(program2, "uTexW");
    GLint uTexH = glGetUniformLocation(program2, "uTexH");
    glUseProgram(program);
    glUniform1i(glGetUniformLocation(program, "uTex"), 0);
    glUseProgram(program2);
    glUniform1i(glGetUniformLocation(program2, "uTex"), 0);
    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glClearColor(0.f, 0.f, 0.f, 1.f);

    unsigned texW = 0, texH = 0;
    std::vector<uint8_t> upload;
    unsigned uploadW = 0, uploadH = 0;
    bool haveFrame = false;

    const float uvs[8] = {0.f, 1.f, 1.f, 1.f, 0.f, 0.f, 1.f, 0.f};
    while (g_renderRunning.load()) {
        EGLint winW = 0, winH = 0;
        eglQuerySurface(display, surface, EGL_WIDTH, &winW);
        eglQuerySurface(display, surface, EGL_HEIGHT, &winH);
        {
            std::lock_guard<std::mutex> lock(g_frameMutex);
            if (g_frameDirty && g_frameW > 0 && g_frameH > 0) {
                upload = g_frameRgba;
                uploadW = g_frameW;
                uploadH = g_frameH;
                g_frameDirty = false;
                g_newFrames.fetch_add(1);
                haveFrame = true;
            }
        }
        if (haveFrame && uploadW > 0) {
            glBindTexture(GL_TEXTURE_2D, tex);
            if (uploadW != texW || uploadH != texH) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, uploadW, uploadH, 0,
                             GL_RGBA, GL_UNSIGNED_BYTE, upload.data());
                texW = uploadW;
                texH = uploadH;
            } else {
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, uploadW, uploadH,
                                GL_RGBA, GL_UNSIGNED_BYTE, upload.data());
            }
        }
        glViewport(0, 0, winW, winH);
        glClear(GL_COLOR_BUFFER_BIT);
        bool sh = g_shaderOn.load();
        glUseProgram(sh ? program2 : program);
        if (sh) {
            glUniform1f(uScan, g_scan.load());
            glUniform1f(uGrid, g_grid.load());
            glUniform1f(uBlur, g_blur.load());
            glUniform1f(uTexelX, texW > 0 ? 1.0f / texW : 0.0f);
            glUniform1f(uTexW, static_cast<float>(texW));
            glUniform1f(uTexH, static_cast<float>(texH));
        }
        if (haveFrame && texW > 0 && winW > 0 && winH > 0) {
            // Portrait: the picture is top-anchored
            // just below the camera cutout (J7), so
            // it can never hide under the notch.
            // Landscape stays centred as before.
            float availH = static_cast<float>(winH);
            float top = 0.f;
            if (winH > winW) {
                int inset = g_topInsetPx.load();
                if (inset > 0 && inset < winH) {
                    availH = static_cast<float>(winH - inset);
                    top = 1.f - 2.f *
                        static_cast<float>(inset) /
                        static_cast<float>(winH);
                }
            }
            float fit = std::min(
                static_cast<float>(winW) / static_cast<float>(texW),
                availH / static_cast<float>(texH));
            float scale = g_integerScale.load()
                ? std::max(1.f, floorf(fit))
                : fit;
            float sx = (static_cast<float>(texW) * scale) / winW;
            float sy = (static_cast<float>(texH) * scale) / winH;
            if (top == 0.f) top = sy;
            const float pos[8] = {
                -sx, top - 2.f * sy, sx, top - 2.f * sy,
                -sx, top, sx, top
            };
            GLint ap = sh ? aPos2 : aPos;
            GLint au = sh ? aUv2 : aUv;
            glVertexAttribPointer(ap, 2, GL_FLOAT, GL_FALSE, 0, pos);
            glEnableVertexAttribArray(ap);
            glVertexAttribPointer(au, 2, GL_FLOAT, GL_FALSE, 0, uvs);
            glEnableVertexAttribArray(au);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        }
        eglSwapBuffers(display, surface);
        std::this_thread::sleep_for(std::chrono::milliseconds(3));
    }
    glDeleteTextures(1, &tex);
    glDeleteProgram(program);
    glDeleteProgram(program2);
    glDeleteShader(vs);
    glDeleteShader(fs);
    glDeleteShader(fs2);
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(display, surface);
    eglDestroyContext(display, context);
    eglTerminate(display);
    ANativeWindow_release(window);
}

// ---------------------------------------------------------------- emu loop + SRAM

void saveSram() {
    if (!g_core.loaded || g_sramPath.empty()) return;
    void* data = g_core.get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t size = g_core.get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!data || size == 0) return;
    FILE* f = fopen(g_sramPath.c_str(), "wb");
    if (!f) return;
    fwrite(data, 1, size, f);
    fclose(f);
}

void loadSram() {
    if (!g_core.loaded || g_sramPath.empty()) return;
    void* data = g_core.get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t size = g_core.get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!data || size == 0) return;
    FILE* f = fopen(g_sramPath.c_str(), "rb");
    if (!f) return;
    fread(data, 1, size, f);
    fclose(f);
}

void emuLoop() {
    using namespace std::chrono;
    while (g_emuRunning.load()) {
        if (g_shutdownRequested.load()) {
            g_emuRunning.store(false);
            break;
        }
        if (g_paused.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(16));
            continue;
        }
        auto start = steady_clock::now();
        {
            std::lock_guard<std::mutex> lock(g_runMutex);
            g_core.run();
        }
        double targetMs = 1000.0 / (g_fps > 0 ? g_fps : 60.0);
        targetMs /= g_speedMul.load();
        auto elapsed = duration_cast<microseconds>(steady_clock::now() - start).count() / 1000.0;
        if (elapsed < targetMs) {
            std::this_thread::sleep_for(duration<double, std::milli>(targetMs - elapsed));
        }
    }
}

std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return "";
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string out = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

template <typename T>
bool loadSym(void* handle, const char* name, T& out) {
    out = reinterpret_cast<T>(dlsym(handle, name));
    return out != nullptr;
}

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeCreate(
        JNIEnv* env, jclass, jstring corePath, jstring romPath,
        jstring systemDir, jstring saveDir, jobjectArray options) {
    std::string core = jstr(env, corePath);
    std::string rom = jstr(env, romPath);
    g_systemDir = jstr(env, systemDir);
    g_saveDir = jstr(env, saveDir);
    g_coreOptions.clear();
    g_newFrames.store(0);
    g_paused.store(false);
    g_speedMul.store(1);
    if (options) {
        jsize count = env->GetArrayLength(options);
        for (jsize i = 0; i < count; i++) {
            auto* item = static_cast<jstring>(
                env->GetObjectArrayElement(options, i));
            if (!item) continue;
            std::string pair = jstr(env, item);
            size_t eq = pair.find('=');
            if (eq != std::string::npos) {
                g_coreOptions[pair.substr(0, eq)] =
                    pair.substr(eq + 1);
            }
            env->DeleteLocalRef(item);
        }
    }

    void* handle = dlopen(core.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        std::string err = std::string("dlopen failed: ") + dlerror();
        return env->NewStringUTF(err.c_str());
    }
    g_core.handle = handle;
    bool ok = true;
    ok = ok && loadSym(handle, "retro_set_environment", g_core.set_environment);
    ok = ok && loadSym(handle, "retro_set_video_refresh", g_core.set_video_refresh);
    ok = ok && loadSym(handle, "retro_set_audio_sample", g_core.set_audio_sample);
    ok = ok && loadSym(handle, "retro_set_audio_sample_batch", g_core.set_audio_sample_batch);
    ok = ok && loadSym(handle, "retro_set_input_poll", g_core.set_input_poll);
    ok = ok && loadSym(handle, "retro_set_input_state", g_core.set_input_state);
    ok = ok && loadSym(handle, "retro_init", g_core.init);
    ok = ok && loadSym(handle, "retro_deinit", g_core.deinit);
    ok = ok && loadSym(handle, "retro_api_version", g_core.api_version);
    ok = ok && loadSym(handle, "retro_get_system_info", g_core.get_system_info);
    ok = ok && loadSym(handle, "retro_get_system_av_info", g_core.get_system_av_info);
    ok = ok && loadSym(handle, "retro_reset", g_core.reset);
    ok = ok && loadSym(handle, "retro_run", g_core.run);
    ok = ok && loadSym(handle, "retro_load_game", g_core.load_game);
    ok = ok && loadSym(handle, "retro_unload_game", g_core.unload_game);
    ok = ok && loadSym(handle, "retro_get_memory_data", g_core.get_memory_data);
    ok = ok && loadSym(handle, "retro_get_memory_size", g_core.get_memory_size);
    ok = ok && loadSym(handle, "retro_serialize_size", g_core.serialize_size);
    ok = ok && loadSym(handle, "retro_serialize", g_core.serialize);
    ok = ok && loadSym(handle, "retro_unserialize", g_core.unserialize);
    if (!ok) {
        return env->NewStringUTF("core is missing libretro symbols");
    }
    if (g_core.api_version() != RETRO_API_VERSION) {
        return env->NewStringUTF("core API version mismatch");
    }
    g_core.set_environment(&environCb);
    g_core.set_video_refresh(&videoCb);
    g_core.set_audio_sample(&audioCb);
    g_core.set_audio_sample_batch(&audioBatchCb);
    g_core.set_input_poll(&inputPollCb);
    g_core.set_input_state(&inputStateCb);
    g_core.init();

    // Read the ROM and hand the core its bytes.
    FILE* f = fopen(rom.c_str(), "rb");
    if (!f) return env->NewStringUTF("ROM file could not be opened");
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (size <= 0) {
        fclose(f);
        return env->NewStringUTF("ROM file is empty");
    }
    std::vector<uint8_t> romData(static_cast<size_t>(size));
    size_t read = fread(romData.data(), 1, romData.size(), f);
    fclose(f);
    if (read != romData.size()) {
        return env->NewStringUTF("ROM file could not be read");
    }
    struct retro_game_info info {};
    info.path = rom.c_str();
    info.data = romData.data();
    info.size = romData.size();
    info.meta = nullptr;
    if (!g_core.load_game(&info)) {
        return env->NewStringUTF("the core refused this ROM");
    }
    g_core.loaded = true;

    struct retro_system_av_info avInfo {};
    g_core.get_system_av_info(&avInfo);
    if (avInfo.timing.fps > 0) g_fps = avInfo.timing.fps;
    if (avInfo.timing.sample_rate > 0) {
        g_sampleRate = static_cast<int>(avInfo.timing.sample_rate);
    }
    g_baseW = avInfo.geometry.base_width;
    g_baseH = avInfo.geometry.base_height;

    // Battery save next to the ROM's name, in the save dir.
    std::string base = rom;
    size_t slash = base.find_last_of('/');
    if (slash != std::string::npos) base = base.substr(slash + 1);
    g_sramPath = g_saveDir + "/" + base + ".srm";
    loadSram();
    return nullptr;
}

JNIEXPORT void JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeSetSurface(
        JNIEnv* env, jclass, jobject surface) {
    std::lock_guard<std::mutex> lock(g_windowMutex);
    if (g_window) {
        ANativeWindow_release(g_window);
        g_window = nullptr;
    }
    if (surface) {
        g_window = ANativeWindow_fromSurface(env, surface);
    }
}

JNIEXPORT void JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeSetTopInset(
        JNIEnv*, jclass, jint px) {
    g_topInsetPx.store(static_cast<int>(px));
}

JNIEXPORT void JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeSetShader(
        JNIEnv*, jclass, jboolean on, jfloat scan, jfloat grid,
        jfloat blur) {
    g_shaderOn.store(on != 0);
    g_scan.store(scan);
    g_grid.store(grid);
    g_blur.store(blur);
}

JNIEXPORT void JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeSetPaused(
        JNIEnv*, jclass, jboolean paused) {
    g_paused.store(paused != 0);
}

JNIEXPORT void JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeSetSpeed(
        JNIEnv*, jclass, jint mul) {
    g_speedMul.store(mul < 1 ? 1 : mul);
}

JNIEXPORT void JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeSetIntegerScale(
        JNIEnv*, jclass, jboolean on) {
    g_integerScale.store(on != 0);
}

JNIEXPORT void JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeReset(
        JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_runMutex);
    if (g_core.loaded && g_core.reset) g_core.reset();
}

JNIEXPORT void JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeStart(JNIEnv*, jclass) {
    if (!g_core.loaded) return;
    audioStart();
    if (!g_emuRunning.load()) {
        g_shutdownRequested.store(false);
        g_emuRunning.store(true);
        g_emuThread = std::thread(&emuLoop);
    }
    if (!g_renderRunning.load()) {
        g_renderRunning.store(true);
        g_renderThread = std::thread(&renderLoop);
    }
}

JNIEXPORT void JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeStop(JNIEnv*, jclass) {
    g_emuRunning.store(false);
    if (g_emuThread.joinable()) g_emuThread.join();
    g_renderRunning.store(false);
    if (g_renderThread.joinable()) g_renderThread.join();
    audioStop();
    saveSram();
}

JNIEXPORT jboolean JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeSaveState(
        JNIEnv* env, jclass, jstring path) {
    std::string p = jstr(env, path);
    std::lock_guard<std::mutex> lock(g_runMutex);
    if (!g_core.loaded) return JNI_FALSE;
    size_t size = g_core.serialize_size();
    if (size == 0) return JNI_FALSE;
    std::vector<uint8_t> buf(size);
    if (!g_core.serialize(buf.data(), size)) return JNI_FALSE;
    FILE* f = fopen(p.c_str(), "wb");
    if (!f) return JNI_FALSE;
    size_t written = fwrite(buf.data(), 1, buf.size(), f);
    fclose(f);
    return written == buf.size() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeLoadState(
        JNIEnv* env, jclass, jstring path) {
    std::string p = jstr(env, path);
    FILE* f = fopen(p.c_str(), "rb");
    if (!f) return JNI_FALSE;
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (size <= 0) { fclose(f); return JNI_FALSE; }
    std::vector<uint8_t> buf(static_cast<size_t>(size));
    size_t read = fread(buf.data(), 1, buf.size(), f);
    fclose(f);
    if (read != buf.size()) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(g_runMutex);
    if (!g_core.loaded) return JNI_FALSE;
    return g_core.unserialize(buf.data(), buf.size())
        ? JNI_TRUE : JNI_FALSE;
}

// Frame grab for state thumbnails: an 8-byte little-endian
// width/height header followed by the latest RGBA frame.
JNIEXPORT jbyteArray JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeFrameGrab(
        JNIEnv* env, jclass) {
    std::vector<uint8_t> frame;
    unsigned w = 0, h = 0;
    {
        std::lock_guard<std::mutex> lock(g_frameMutex);
        if (g_frameRgba.empty()) return nullptr;
        frame = g_frameRgba;
        w = g_frameW;
        h = g_frameH;
    }
    jbyteArray out = env->NewByteArray(
        static_cast<jsize>(8 + frame.size()));
    if (!out) return nullptr;
    uint8_t header[8];
    header[0] = static_cast<uint8_t>(w & 0xFF);
    header[1] = static_cast<uint8_t>((w >> 8) & 0xFF);
    header[2] = static_cast<uint8_t>((w >> 16) & 0xFF);
    header[3] = static_cast<uint8_t>((w >> 24) & 0xFF);
    header[4] = static_cast<uint8_t>(h & 0xFF);
    header[5] = static_cast<uint8_t>((h >> 8) & 0xFF);
    header[6] = static_cast<uint8_t>((h >> 16) & 0xFF);
    header[7] = static_cast<uint8_t>((h >> 24) & 0xFF);
    env->SetByteArrayRegion(out, 0, 8,
        reinterpret_cast<const jbyte*>(header));
    env->SetByteArrayRegion(out, 8,
        static_cast<jsize>(frame.size()),
        reinterpret_cast<const jbyte*>(frame.data()));
    return out;
}

JNIEXPORT void JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeSetInput(
        JNIEnv*, jclass, jint mask) {
    g_inputMask.store(mask);
}

JNIEXPORT void JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeDestroy(JNIEnv*, jclass) {
    if (g_core.loaded) {
        g_core.unload_game();
        g_core.loaded = false;
    }
    if (g_core.handle) {
        g_core.deinit();
        dlclose(g_core.handle);
        g_core.handle = nullptr;
    }
    std::lock_guard<std::mutex> lock(g_windowMutex);
    if (g_window) {
        ANativeWindow_release(g_window);
        g_window = nullptr;
    }
}

JNIEXPORT jlong JNICALL
Java_net_notscape_browser_emu_RetroNative_nativeGetPresentedFrames(
        JNIEnv*, jclass) {
    return static_cast<jlong>(g_newFrames.load());
}

} // extern "C"
