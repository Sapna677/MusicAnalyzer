package service;

import be.tarsos.dsp.util.fft.FFT;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/**
 * Service to analyze spectral features and classify the active instrument.
 */
public class InstrumentDetector {
    private final FFT fft;
    private final float[] amplitudes;
    private final int bufferSize;
    private final float sampleRate;

    // Rolling window to smooth instrument classification (prevent wild flickering)
    private static final int ROLLING_WINDOW_SIZE = 15;
    private final Queue<String> history = new ArrayDeque<>();

    public InstrumentDetector(int bufferSize, float sampleRate) {
        this.bufferSize = bufferSize;
        this.sampleRate = sampleRate;
        this.fft = new FFT(bufferSize);
        this.amplitudes = new float[bufferSize / 2];
    }

    /**
     * Resets the smoothing window history.
     */
    public void reset() {
        history.clear();
    }

    /**
     * Detects and classifies the active musical instrument from raw float audio samples.
     * @param audioBuffer The buffer containing mono float samples.
     * @param pitch The pitch detected in Hz, or -1 if no pitch.
     * @param rms The Root Mean Square energy (volume) of the buffer.
     * @return The smoothed name of the instrument.
     */
    public String detectInstrument(float[] audioBuffer, float pitch, float rms) {
        if (rms < 0.006f) {
            return "Silence";
        }

        // 1. Calculate Zero Crossing Rate (ZCR)
        float zcr = 0;
        for (int i = 1; i < audioBuffer.length; i++) {
            if (audioBuffer[i - 1] * audioBuffer[i] < 0) {
                zcr++;
            }
        }
        zcr = zcr / audioBuffer.length;

        // 2. Perform FFT to analyze spectral components
        float[] fftBuffer = audioBuffer.clone();
        fft.forwardTransform(fftBuffer);
        fft.modulus(fftBuffer, amplitudes);

        // 3. Calculate Spectral Centroid (centroid frequency)
        float centroidSum = 0;
        float amplitudeSum = 0;
        for (int i = 0; i < amplitudes.length; i++) {
            float freq = i * (sampleRate / bufferSize);
            centroidSum += freq * amplitudes[i];
            amplitudeSum += amplitudes[i];
        }
        float spectralCentroid = amplitudeSum > 0 ? (centroidSum / amplitudeSum) : 0;

        // 4. Raw heuristic classification for this specific frame
        String rawClassification;

        if (pitch == -1) {
            // No pitch -> likely Percussive (Drums) or Vocal breath/noise
            if (zcr > 0.15f || spectralCentroid > 2500.0f) {
                rawClassification = "🥁 Drums";
            } else {
                rawClassification = "🎤 Vocals";
            }
        } else {
            // Pitched signal
            if (zcr > 0.18f) {
                rawClassification = "🥁 Drums"; // Cymbals/Snare bleed
            } else if (zcr < 0.03f) {
                // Low noise / clean signal
                if (pitch > 600.0f) {
                    rawClassification = "🎻 Strings";
                } else {
                    rawClassification = "🎹 Piano";
                }
            } else if (zcr >= 0.03f && zcr < 0.09f) {
                // Mid-ZCR pitched signals
                if (pitch >= 82.0f && pitch <= 1000.0f) {
                    // Check harmonic brilliance (guitar vs piano/voice)
                    if (spectralCentroid > 1400.0f) {
                        rawClassification = "🎸 Guitar";
                    } else if (pitch > 350.0f) {
                        rawClassification = "🎤 Vocals";
                    } else {
                        rawClassification = "🎹 Piano";
                    }
                } else {
                    rawClassification = "🎹 Piano";
                }
            } else { // 0.09f <= zcr <= 0.18f
                // Pitch in vocals range with breath/noise
                if (pitch >= 80.0f && pitch <= 1200.0f) {
                    rawClassification = "🎤 Vocals";
                } else {
                    rawClassification = "🎸 Guitar";
                }
            }
        }

        // Add to history and perform rolling window smoothing
        history.offer(rawClassification);
        if (history.size() > ROLLING_WINDOW_SIZE) {
            history.poll();
        }

        // Count votes
        Map<String, Integer> votes = new HashMap<>();
        for (String c : history) {
            votes.put(c, votes.getOrDefault(c, 0) + 1);
        }

        // Find majority
        String smoothedClassification = rawClassification;
        int maxVotes = -1;
        for (Map.Entry<String, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                smoothedClassification = entry.getKey();
            }
        }

        return smoothedClassification;
    }
}
