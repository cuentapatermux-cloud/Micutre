// Copyright 2026 PollNull

#include <jni.h>

#include <oboe/Oboe.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <vector>

namespace {

class MicutreAudio final : public oboe::AudioStreamDataCallback,
                           public oboe::AudioStreamErrorCallback {
public:
    bool start(int32_t preferredOutputId, float gain, float pitchSemitones,
               float echoMix, float echoDelayMs, float robotMix) {
        mGain.store(std::clamp(gain, 0.0f, 1.0f), std::memory_order_relaxed);
        setEffect(pitchSemitones, echoMix, echoDelayMs, robotMix);

        if (!openOutput(preferredOutputId) || !openInput()) {
            stop();
            return false;
        }

        mFifo.reset();
        mPitchBuffer.fill(0.0f);
        mEchoBuffer.fill(0.0f);
        mPitchWrite = 0;
        mEchoWrite = 0;
        mPitchPhase = 0.5;
        mRobotPhase = 0.0;
        mRunning.store(true, std::memory_order_release);
        if (mOutput->requestStart() != oboe::Result::OK ||
            mInput->requestStart() != oboe::Result::OK) {
            stop();
            return false;
        }
        return true;
    }

    void stop() {
        mRunning.store(false, std::memory_order_release);
        if (mInput) {
            mInput->requestStop();
            mInput->close();
            mInput.reset();
        }
        if (mOutput) {
            mOutput->requestStop();
            mOutput->close();
            mOutput.reset();
        }
        mFifo.reset();
    }

    void setGain(float gain) {
        mGain.store(std::clamp(gain, 0.0f, 1.0f), std::memory_order_relaxed);
    }

    void setEffect(float pitchSemitones, float echoMix, float echoDelayMs, float robotMix) {
        const float boundedPitch = std::clamp(pitchSemitones, -12.0f, 12.0f);
        mPitchRatio.store(std::pow(2.0f, boundedPitch / 12.0f), std::memory_order_relaxed);
        mEchoMix.store(std::clamp(echoMix, 0.0f, 0.8f), std::memory_order_relaxed);
        mEchoDelayMs.store(std::clamp(echoDelayMs, 60.0f, 500.0f), std::memory_order_relaxed);
        mRobotMix.store(std::clamp(robotMix, 0.0f, 1.0f), std::memory_order_relaxed);
    }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream,
                                           void *audioData,
                                           int32_t numFrames) override {
        if (!mRunning.load(std::memory_order_acquire)) {
            return oboe::DataCallbackResult::Stop;
        }

        if (stream->getDirection() == oboe::Direction::Input) {
            mFifo.push(static_cast<const float *>(audioData), numFrames);
            return oboe::DataCallbackResult::Continue;
        }

        auto *output = static_cast<float *>(audioData);
        const int32_t copied = mFifo.pop(output, numFrames);
        if (copied < numFrames) {
            std::fill(output + copied, output + numFrames, 0.0f);
        }

        const float gain = mGain.load(std::memory_order_relaxed);
        const float pitchRatio = mPitchRatio.load(std::memory_order_relaxed);
        const float echoMix = mEchoMix.load(std::memory_order_relaxed);
        const float echoDelayMs = mEchoDelayMs.load(std::memory_order_relaxed);
        const float robotMix = mRobotMix.load(std::memory_order_relaxed);
        const double sampleRate = static_cast<double>(stream->getSampleRate());
        const double pitchWindow = std::min(1024.0, std::max(128.0, sampleRate * 0.024));
        const uint32_t echoDelay = static_cast<uint32_t>(std::clamp(
                sampleRate * static_cast<double>(echoDelayMs) / 1000.0,
                1.0, static_cast<double>(kEchoCapacity - 1)));
        const double robotStep = sampleRate > 0.0 ? 2.0 * kPi * 35.0 / sampleRate : 0.0;

        for (int32_t i = 0; i < numFrames; ++i) {
            float sample = output[i] * gain;
            mPitchBuffer[mPitchWrite] = sample;

            if (std::abs(pitchRatio - 1.0f) > 0.001f) {
                const double phaseStep = (1.0 - static_cast<double>(pitchRatio)) / pitchWindow;
                mPitchPhase = wrapPhase(mPitchPhase + phaseStep);
                const double phaseB = wrapPhase(mPitchPhase + 0.5);
                const double weightA = 0.5 - 0.5 * std::cos(2.0 * kPi * mPitchPhase);
                const double weightB = 1.0 - weightA;
                const float shiftedA = readPitchDelay(mPitchPhase * pitchWindow);
                const float shiftedB = readPitchDelay(phaseB * pitchWindow);
                sample = static_cast<float>(weightA * shiftedA + weightB * shiftedB);
            }
            mPitchWrite = (mPitchWrite + 1) % kPitchCapacity;

            if (robotMix > 0.0f) {
                const float carrier = static_cast<float>(std::sin(mRobotPhase));
                sample *= (1.0f - robotMix) + robotMix * carrier;
                mRobotPhase += robotStep;
                if (mRobotPhase >= 2.0 * kPi) mRobotPhase -= 2.0 * kPi;
            }

            if (echoMix > 0.0f) {
                const uint32_t read = (mEchoWrite + kEchoCapacity - echoDelay) % kEchoCapacity;
                const float delayed = mEchoBuffer[read];
                mEchoBuffer[mEchoWrite] = std::clamp(sample + delayed * 0.28f, -1.0f, 1.0f);
                sample = sample * (1.0f - echoMix) + delayed * echoMix;
            } else {
                mEchoBuffer[mEchoWrite] = sample;
            }
            mEchoWrite = (mEchoWrite + 1) % kEchoCapacity;
            output[i] = std::clamp(sample, -1.0f, 1.0f);
        }
        return oboe::DataCallbackResult::Continue;
    }

    void onErrorAfterClose(oboe::AudioStream *, oboe::Result) override {
        mRunning.store(false, std::memory_order_release);
    }

private:
    static constexpr uint32_t kPitchCapacity = 2048;
    static constexpr uint32_t kEchoCapacity = 96000;
    static constexpr double kPi = 3.14159265358979323846;

    static double wrapPhase(double phase) {
        phase -= std::floor(phase);
        return phase;
    }

    float readPitchDelay(double delay) const {
        double position = static_cast<double>(mPitchWrite) - delay;
        while (position < 0.0) position += kPitchCapacity;
        while (position >= kPitchCapacity) position -= kPitchCapacity;
        const uint32_t first = static_cast<uint32_t>(position);
        const uint32_t second = (first + 1) % kPitchCapacity;
        const float fraction = static_cast<float>(position - first);
        return mPitchBuffer[first] + (mPitchBuffer[second] - mPitchBuffer[first]) * fraction;
    }

    class SampleFifo {
    public:
        static constexpr uint32_t kCapacity = 512;
        static constexpr uint32_t kMask = kCapacity - 1;

        SampleFifo() : mSamples(kCapacity, 0.0f) {}

        void reset() {
            mRead.store(0, std::memory_order_relaxed);
            mWrite.store(0, std::memory_order_relaxed);
        }

        void push(const float *samples, int32_t count) {
            uint32_t write = mWrite.load(std::memory_order_relaxed);
            for (int32_t i = 0; i < count; ++i) {
                const uint32_t read = mRead.load(std::memory_order_acquire);
                if (write - read < kCapacity) {
                    mSamples[write & kMask] = samples[i];
                    ++write;
                }
            }
            mWrite.store(write, std::memory_order_release);
        }

        int32_t pop(float *destination, int32_t requested) {
            uint32_t read = mRead.load(std::memory_order_relaxed);
            const uint32_t write = mWrite.load(std::memory_order_acquire);
            const uint32_t available = std::min<uint32_t>(write - read,
                                                          static_cast<uint32_t>(requested));
            for (uint32_t i = 0; i < available; ++i) {
                destination[i] = mSamples[(read + i) & kMask];
            }
            read += available;
            mRead.store(read, std::memory_order_release);
            return static_cast<int32_t>(available);
        }

    private:
    std::vector<float> mSamples;
        std::atomic<uint32_t> mRead{0};
        std::atomic<uint32_t> mWrite{0};
    };

    bool openOutput(int32_t preferredOutputId) {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
                ->setChannelCount(1)
                ->setFormat(oboe::AudioFormat::Float)
                ->setUsage(oboe::Usage::Game)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(oboe::SharingMode::Exclusive)
                ->setDataCallback(this)
                ->setErrorCallback(this);

        if (preferredOutputId > 0) {
            builder.setDeviceId(preferredOutputId);
        }

        auto result = builder.openStream(mOutput);
        if (result != oboe::Result::OK && preferredOutputId > 0) {
            // If an explicit Bluetooth route can't open, let Android choose its active route.
            builder.setDeviceId(oboe::kUnspecified);
            result = builder.openStream(mOutput);
        }
        if (result != oboe::Result::OK) {
            builder.setSharingMode(oboe::SharingMode::Shared);
            result = builder.openStream(mOutput);
        }
        if (result != oboe::Result::OK && mOutput) {
            mOutput->close();
            mOutput.reset();
            builder.setSharingMode(oboe::SharingMode::Shared);
            result = builder.openStream(mOutput);
        }
        if (result != oboe::Result::OK || !mOutput) {
            return false;
        }

        const int32_t burst = mOutput->getFramesPerBurst();
        if (burst > 0) {
            mOutput->setBufferSizeInFrames(burst * 2);
        }
        return true;
    }

    bool openInput() {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Input)
                ->setChannelCount(1)
                ->setFormat(oboe::AudioFormat::Float)
                ->setInputPreset(oboe::InputPreset::VoiceRecognition)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(oboe::SharingMode::Exclusive)
                ->setDataCallback(this)
                ->setErrorCallback(this);

        auto result = builder.openStream(mInput);
        if (result != oboe::Result::OK) {
            builder.setSharingMode(oboe::SharingMode::Shared);
            result = builder.openStream(mInput);
        }
        if (result != oboe::Result::OK || !mInput) {
            return false;
        }

        const int32_t burst = mInput->getFramesPerBurst();
        if (burst > 0) {
            mInput->setBufferSizeInFrames(burst * 2);
        }
        return true;
    }

    std::shared_ptr<oboe::AudioStream> mInput;
    std::shared_ptr<oboe::AudioStream> mOutput;
    SampleFifo mFifo;
    std::atomic<float> mGain{0.8f};
    std::atomic<float> mPitchRatio{1.0f};
    std::atomic<float> mEchoMix{0.0f};
    std::atomic<float> mEchoDelayMs{180.0f};
    std::atomic<float> mRobotMix{0.0f};
    std::atomic<bool> mRunning{false};
    std::array<float, kPitchCapacity> mPitchBuffer{};
    std::array<float, kEchoCapacity> mEchoBuffer{};
    uint32_t mPitchWrite{0};
    uint32_t mEchoWrite{0};
    double mPitchPhase{0.5};
    double mRobotPhase{0.0};
};

std::mutex gEngineMutex;
std::unique_ptr<MicutreAudio> gEngine;

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_miclite_voz_AudioEngine_nativeStart(JNIEnv *, jobject, jint outputDeviceId, jfloat gain,
                                             jfloat pitchSemitones, jfloat echoMix,
                                             jfloat echoDelayMs, jfloat robotMix) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gEngine) {
        gEngine->stop();
    }
    auto engine = std::make_unique<MicutreAudio>();
    if (!engine->start(outputDeviceId, gain, pitchSemitones, echoMix, echoDelayMs, robotMix)) {
        return JNI_FALSE;
    }
    gEngine = std::move(engine);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_miclite_voz_AudioEngine_nativeSetGain(JNIEnv *, jobject, jfloat gain) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gEngine) {
        gEngine->setGain(gain);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_miclite_voz_AudioEngine_nativeSetEffect(JNIEnv *, jobject, jfloat pitchSemitones,
                                                 jfloat echoMix, jfloat echoDelayMs,
                                                 jfloat robotMix) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gEngine) {
        gEngine->setEffect(pitchSemitones, echoMix, echoDelayMs, robotMix);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_miclite_voz_AudioEngine_nativeStop(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gEngine) {
        gEngine->stop();
        gEngine.reset();
    }
}
