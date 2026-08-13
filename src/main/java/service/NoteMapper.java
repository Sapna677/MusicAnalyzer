package service;

import model.Note;

/**
 * Maps audio frequencies to musical notes using MIDI standards.
 */
public class NoteMapper {
    private static final String[] NOTE_NAMES = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    /**
     * Converts a frequency (Hz) into a Note model object.
     * @param frequency The frequency to convert.
     * @param timestamp The time offset in seconds when this note occurred.
     * @return A Note object, or null if the frequency is invalid.
     */
    public static Note fromFrequency(double frequency, double timestamp) {
        if (frequency <= 20.0 || frequency > 20000.0) {
            return null; // Ignore non-audible or out of bounds frequencies
        }

        // Calculate MIDI note number: n = 12 * log2(f/440) + 69
        double midi = 12.0 * (Math.log(frequency / 440.0) / Math.log(2.0)) + 69.0;
        int midiNote = (int) Math.round(midi);

        if (midiNote < 0 || midiNote > 127) {
            return null;
        }

        int noteIndex = midiNote % 12;
        String noteName = NOTE_NAMES[noteIndex];
        int octave = (midiNote / 12) - 1;

        return new Note(timestamp, noteName, octave, frequency, midiNote);
    }
}
