package util;

import javazoom.jl.converter.Converter;
import javazoom.jl.decoder.JavaLayerException;
import model.SongInfo;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Utility class for audio conversion, decoding, resampling, and metadata parsing.
 */
public class AudioUtils {

    /**
     * Converts an MP3 file to a temporary WAV file using JLayer.
     * @param mp3File The input MP3 file.
     * @return The temporary WAV file.
     * @throws JavaLayerException if decoding fails.
     * @throws IOException if file creation fails.
     */
    public static File convertMp3ToWav(File mp3File) throws JavaLayerException, IOException {
        File tempWav = File.createTempFile("music_analyzer_", ".wav");
        tempWav.deleteOnExit();
        
        Converter converter = new Converter();
        converter.convert(mp3File.getAbsolutePath(), tempWav.getAbsolutePath());
        return tempWav;
    }

    /**
     * Extracts mono float PCM samples (44.1 kHz) from a WAV file.
     * @param wavFile The input WAV file.
     * @return An array of float samples in range [-1.0, 1.0].
     * @throws UnsupportedAudioFileException if the audio format is not supported.
     * @throws IOException if reading the file fails.
     */
    public static float[] decodeWavToMonoSamples(File wavFile) throws UnsupportedAudioFileException, IOException {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile)) {
            AudioFormat format = ais.getFormat();
            
            // Read all bytes from the stream
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = ais.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            byte[] audioBytes = baos.toByteArray();

            int sampleSizeInBits = format.getSampleSizeInBits();
            int channels = format.getChannels();
            boolean bigEndian = format.isBigEndian();
            float sampleRate = format.getSampleRate();

            // Default to 16-bit if not specified (e.g. some compressed WAVs)
            if (sampleSizeInBits != 8 && sampleSizeInBits != 16) {
                sampleSizeInBits = 16;
            }

            int bytesPerSample = sampleSizeInBits / 8;
            int bytesPerFrame = bytesPerSample * channels;
            int numFrames = audioBytes.length / bytesPerFrame;

            float[] rawSamples = new float[numFrames];

            for (int i = 0; i < numFrames; i++) {
                int frameOffset = i * bytesPerFrame;
                float sum = 0;

                // Process each channel and average them to get a mono signal
                for (int c = 0; c < channels; c++) {
                    int sampleOffset = frameOffset + c * bytesPerSample;
                    float val = 0;

                    if (sampleSizeInBits == 16) {
                        short shortVal;
                        if (bigEndian) {
                            shortVal = (short) ((audioBytes[sampleOffset] << 8) | (audioBytes[sampleOffset + 1] & 0xFF));
                        } else {
                            shortVal = (short) ((audioBytes[sampleOffset + 1] << 8) | (audioBytes[sampleOffset] & 0xFF));
                        }
                        val = shortVal / 32768.0f;
                    } else if (sampleSizeInBits == 8) {
                        // Unsigned 8-bit PCM is standard for WAV
                        int unsignedVal = audioBytes[sampleOffset] & 0xFF;
                        val = (unsignedVal - 128) / 128.0f;
                    }
                    sum += val;
                }
                rawSamples[i] = sum / channels;
            }

            // Resample to 44100 Hz if necessary
            return resample(rawSamples, sampleRate, 44100.0f);
        }
    }

    /**
     * Resamples the audio buffer from input rate to target rate using linear interpolation.
     */
    private static float[] resample(float[] input, float fromRate, float toRate) {
        if (Math.abs(fromRate - toRate) < 0.1f) {
            return input;
        }

        double ratio = (double) fromRate / toRate;
        int targetLength = (int) Math.round(input.length / ratio);
        float[] output = new float[targetLength];

        for (int i = 0; i < targetLength; i++) {
            double srcIdx = i * ratio;
            int idx = (int) Math.floor(srcIdx);
            double frac = srcIdx - idx;

            if (idx < input.length - 1) {
                output[i] = (float) ((1.0 - frac) * input[idx] + frac * input[idx + 1]);
            } else if (idx < input.length) {
                output[i] = input[idx];
            } else {
                output[i] = 0.0f;
            }
        }
        return output;
    }

    /**
     * Parses the audio file properties and returns a SongInfo object.
     * @param audioFile The original uploaded file (MP3 or WAV).
     * @param decodedWav The decoded WAV file (for WAV property analysis).
     * @return SongInfo containing metadata.
     */
    public static SongInfo getSongInfo(File audioFile, File decodedWav) {
        String fileName = audioFile.getName();
        long fileSize = audioFile.length();
        String fileSizeStr = formatFileSize(fileSize);

        int sampleRate = 44100;
        int channels = 1;
        int bitsPerSample = 16;
        double durationSeconds = 0;

        try (AudioInputStream ais = AudioSystem.getAudioInputStream(decodedWav)) {
            AudioFormat format = ais.getFormat();
            sampleRate = (int) format.getSampleRate();
            channels = format.getChannels();
            bitsPerSample = format.getSampleSizeInBits() > 0 ? format.getSampleSizeInBits() : 16;
            
            long frameLength = ais.getFrameLength();
            durationSeconds = frameLength / format.getSampleRate();
        } catch (Exception e) {
            System.err.println("Error reading wav headers: " + e.getMessage());
        }

        // If WAV was generated from MP3, the original bitrate is estimated based on original size and duration
        int bitrate;
        if (audioFile.getName().toLowerCase().endsWith(".mp3")) {
            if (durationSeconds > 0) {
                bitrate = (int) Math.round((fileSize * 8.0) / (durationSeconds * 1000.0));
            } else {
                bitrate = 128; // Default fallback
            }
        } else {
            bitrate = (int) Math.round((sampleRate * channels * bitsPerSample) / 1000.0);
        }

        String durationStr = formatDuration(durationSeconds);

        return new SongInfo(fileName, durationSeconds, durationStr, sampleRate, channels, bitrate, fileSizeStr);
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }

    public static String formatDuration(double seconds) {
        int m = (int) (seconds / 60);
        int s = (int) (seconds % 60);
        return String.format("%02d:%02d", m, s);
    }
}
