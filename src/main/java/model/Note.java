package model;

/**
 * Represents a musical note detected in real-time.
 */
public class Note {
    private final double timestamp; // Time in seconds
    private final String noteName;   // e.g., C, D#, G
    private final int octave;       // e.g., 4, 5
    private final double frequency;  // Frequency in Hz
    private final int midiNote;     // MIDI note number (e.g., 60 for Middle C)

    public Note(double timestamp, String noteName, int octave, double frequency, int midiNote) {
        this.timestamp = timestamp;
        this.noteName = noteName;
        this.octave = octave;
        this.frequency = frequency;
        this.midiNote = midiNote;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public String getNoteName() {
        return noteName;
    }

    public int getOctave() {
        return octave;
    }

    public double getFrequency() {
        return frequency;
    }

    public int getMidiNote() {
        return midiNote;
    }

    // Helper to format frequency for display
    public String getFrequencyStr() {
        return String.format("%.2f Hz", frequency);
    }

    // Helper to format timestamp for display
    public String getTimestampStr() {
        return String.format("%.2fs", timestamp);
    }

    // Full name like "C4"
    public String getFullName() {
        return noteName + octave;
    }
}
