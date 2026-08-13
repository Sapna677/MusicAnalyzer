package service;

/**
 * Interface to receive real-time audio playback and analysis updates.
 */
public interface AudioPlaybackListener {
    /**
     * Called periodically during playback to report current time.
     * @param currentTimeSeconds Position of playback in seconds.
     */
    void onProgress(double currentTimeSeconds);

    /**
     * Called whenever a pitch is processed.
     * @param pitch Detected frequency in Hz, or -1 if unpitched.
     * @param rms RMS volume level of the processed buffer.
     */
    void onPitchDetected(float pitch, float rms);

    /**
     * Called whenever the active instrument classification updates.
     * @param instrument Name and icon of the detected instrument (e.g. "🎹 Piano").
     */
    void onInstrumentDetected(String instrument);

    /**
     * Called when the song reaches the end or is manually stopped.
     */
    void onPlaybackStopped();
}
