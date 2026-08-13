package service;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import model.SongInfo;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.LineUnavailableException;
import java.io.ByteArrayInputStream;
import java.io.File;

/**
 * Service to manage audio playback, seeking, and the DSP analysis thread.
 */
public class AudioPlayer {
    private final float[] monoSamples;
    private final SongInfo songInfo;
    private final AudioPlaybackListener listener;
    private final InstrumentDetector instrumentDetector;

    private static final int BUFFER_SIZE = 2048;
    private static final int OVERLAP = 0;

    private AudioDispatcher dispatcher;
    private VolumeProcessor volumeProcessor;
    private Thread audioThread;
    private final Object dispatcherLock = new Object();

    private volatile boolean isPlaying = false;
    private volatile boolean isPaused = false;
    private volatile boolean manuallyStopped = false;
    private volatile boolean isSeeking = false;
    
    private volatile double currentTimeSeconds = 0.0;
    private volatile float volume = 0.8f; // Default volume: 80%

    public AudioPlayer(float[] monoSamples, SongInfo songInfo, AudioPlaybackListener listener) {
        this.monoSamples = monoSamples;
        this.songInfo = songInfo;
        this.listener = listener;
        this.instrumentDetector = new InstrumentDetector(BUFFER_SIZE, 44100.0f);
        this.volumeProcessor = new VolumeProcessor();
        this.volumeProcessor.setVolume(volume);
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public double getCurrentTimeSeconds() {
        return currentTimeSeconds;
    }

    public SongInfo getSongInfo() {
        return songInfo;
    }

    /**
     * Sets the volume (0.0 to 1.0).
     */
    public void setVolume(float volume) {
        this.volume = volume;
        if (volumeProcessor != null) {
            volumeProcessor.setVolume(volume);
        }
    }

    /**
     * Starts playback from the current time.
     */
    public void play() {
        boolean shouldStart = false;
        synchronized (this) {
            if (isPlaying) {
                return;
            }
            manuallyStopped = false;
            isPlaying = true;
            isPaused = false;
            shouldStart = true;
        }

        if (shouldStart) {
            synchronized (dispatcherLock) {
                startDispatcherThread(currentTimeSeconds);
            }
        }
    }

    /**
     * Pauses audio playback.
     */
    public void pause() {
        boolean shouldStop = false;
        synchronized (this) {
            if (!isPlaying || isPaused) {
                return;
            }
            isPaused = true;
            isPlaying = false;
            shouldStop = true;
        }

        if (shouldStop) {
            synchronized (dispatcherLock) {
                stopDispatcher();
            }
        }
    }

    /**
     * Stops audio playback and resets position to start.
     */
    public void stop() {
        synchronized (this) {
            manuallyStopped = true;
            isPlaying = false;
            isPaused = false;
            currentTimeSeconds = 0.0;
        }

        synchronized (dispatcherLock) {
            stopDispatcher();
        }
        instrumentDetector.reset();
        listener.onProgress(0.0);
        listener.onPlaybackStopped();
    }

    /**
     * Seeks to a specific ratio (0.0 to 1.0) of the song.
     */
    public void seek(double ratio) {
        double targetTime = ratio * songInfo.getDurationSeconds();
        if (targetTime < 0) targetTime = 0;
        if (targetTime > songInfo.getDurationSeconds()) targetTime = songInfo.getDurationSeconds();

        System.out.println("DEBUG [seek]: ratio=" + ratio + ", targetTime=" + targetTime + "s, duration=" + songInfo.getDurationSeconds() + "s");

        boolean shouldRestart = false;
        synchronized (this) {
            currentTimeSeconds = targetTime;
            listener.onProgress(currentTimeSeconds);
            if (isPlaying) {
                isSeeking = true;
                shouldRestart = true;
            }
        }

        if (shouldRestart) {
            System.out.println("DEBUG [seek]: player is playing, restarting dispatcher thread...");
            synchronized (dispatcherLock) {
                stopDispatcher();
            }
            synchronized (this) {
                isSeeking = false;
            }
            synchronized (dispatcherLock) {
                startDispatcherThread(currentTimeSeconds);
            }
            System.out.println("DEBUG [seek]: dispatcher thread restarted successfully.");
        } else {
            System.out.println("DEBUG [seek]: player is NOT playing, updated position only.");
        }
    }

    /**
     * Stops the active dispatcher.
     */
    private void stopDispatcher() {
        System.out.println("DEBUG [stopDispatcher]: Stopping dispatcher...");
        if (dispatcher != null) {
            dispatcher.stop();
            dispatcher = null;
            System.out.println("DEBUG [stopDispatcher]: dispatcher.stop() called.");
        }
        if (audioThread != null) {
            System.out.println("DEBUG [stopDispatcher]: Joining audioThread...");
            long startJoin = System.currentTimeMillis();
            try {
                audioThread.join(800); // Wait up to 800ms for the audio thread to terminate
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            long elapsedJoin = System.currentTimeMillis() - startJoin;
            System.out.println("DEBUG [stopDispatcher]: audioThread joined in " + elapsedJoin + " ms.");
            audioThread = null;
        }
    }

    /**
     * Creates and starts the background audio processing and playback thread.
     */
    private void startDispatcherThread(final double startPositionSeconds) {
        System.out.println("DEBUG [startDispatcherThread]: starting from " + startPositionSeconds + "s");
        try {
            int startFrame = (int) (startPositionSeconds * 44100.0);
            if (startFrame >= monoSamples.length) {
                System.out.println("DEBUG [startDispatcherThread]: startFrame exceeds sample length, stopping.");
                stop();
                return;
            }

            int remainingSamples = monoSamples.length - startFrame;
            
            // Convert mono float samples to standard 16-bit Mono PCM bytes
            byte[] pcmBytes = new byte[remainingSamples * 2];
            for (int i = 0; i < remainingSamples; i++) {
                float fVal = monoSamples[startFrame + i];
                fVal = Math.max(-1.0f, Math.min(1.0f, fVal)); // Clamp sample to avoid distortion
                short shortVal = (short) (fVal * 32767.0f);
                pcmBytes[i * 2] = (byte) (shortVal & 0xff);
                pcmBytes[i * 2 + 1] = (byte) ((shortVal >> 8) & 0xff);
            }

            // Create AudioInputStream from bytes
            ByteArrayInputStream bais = new ByteArrayInputStream(pcmBytes);
            AudioFormat audioFormat = new AudioFormat(44100.0f, 16, 1, true, false);
            AudioInputStream ais = new AudioInputStream(bais, audioFormat, remainingSamples);

            // Create TarsosDSP AudioInputStream and Dispatcher
            JVMAudioInputStream jvmAis = new JVMAudioInputStream(ais);
            dispatcher = new AudioDispatcher(jvmAis, BUFFER_SIZE, OVERLAP);

            PitchDetectionHandler pitchHandler = new PitchDetectionHandler() {
                @Override
                public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
                    float pitch = pitchDetectionResult.getPitch();
                    float rms = (float) audioEvent.getRMS();

                    // Notify pitch
                    listener.onPitchDetected(pitch, rms);

                    // Classify and notify instrument
                    float[] buffer = audioEvent.getFloatBuffer();
                    String instrument = instrumentDetector.detectInstrument(buffer, pitch, rms);
                    listener.onInstrumentDetected(instrument);
                }
            };
            PitchProcessor pitchProcessor = new PitchProcessor(
                    PitchProcessor.PitchEstimationAlgorithm.YIN,
                    44100.0f,
                    BUFFER_SIZE,
                    pitchHandler
            );
            dispatcher.addAudioProcessor(pitchProcessor);

            // 2. Volume Gain Processor (multiplies sample amplitudes by volume ratio)
            volumeProcessor.setVolume(volume);
            dispatcher.addAudioProcessor(volumeProcessor);

            // 3. Audio Player (outputs samples to speakers)
            be.tarsos.dsp.io.jvm.AudioPlayer audioPlayer = new be.tarsos.dsp.io.jvm.AudioPlayer(audioFormat);
            dispatcher.addAudioProcessor(audioPlayer);

            // 4. Progress and Lifecycle Tracker
            dispatcher.addAudioProcessor(new AudioProcessor() {
                @Override
                public boolean process(AudioEvent audioEvent) {
                    if (isSeeking) {
                        return true;
                    }
                    double elapsed = audioEvent.getTimeStamp();
                    currentTimeSeconds = startPositionSeconds + elapsed;
                    listener.onProgress(currentTimeSeconds);
                    return true;
                }

                @Override
                public void processingFinished() {
                    synchronized (AudioPlayer.this) {
                        if (isPlaying && !manuallyStopped && !isSeeking) {
                            // Audio played to completion
                            isPlaying = false;
                            currentTimeSeconds = 0.0;
                            listener.onProgress(0.0);
                            listener.onPlaybackStopped();
                        }
                    }
                }
            });

            // Start processing in a background thread
            audioThread = new Thread(dispatcher, "Audio Dispatcher Thread");
            audioThread.setDaemon(true);
            audioThread.start();

        } catch (LineUnavailableException e) {
            System.err.println("Audio device line unavailable: " + e.getMessage());
            stop();
        }
    }

    /**
     * Audio Processor to modify amplitude values (volume).
     */
    private static class VolumeProcessor implements AudioProcessor {
        private float volume = 1.0f;

        public void setVolume(float volume) {
            this.volume = volume;
        }

        @Override
        public boolean process(AudioEvent audioEvent) {
            float[] buffer = audioEvent.getFloatBuffer();
            for (int i = 0; i < buffer.length; i++) {
                buffer[i] *= volume;
            }
            return true;
        }

        @Override
        public void processingFinished() {
            // No cleanup needed
        }
    }
}
