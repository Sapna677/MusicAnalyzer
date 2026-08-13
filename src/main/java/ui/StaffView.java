package ui;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Custom Canvas to render detected notes on a scrolling musical staff.
 */
public class StaffView extends Canvas {

    private static class StaffNote {
        final int midiNote;
        final String name;
        final int step;
        final boolean isSharp;
        double x;
        final Color color;

        StaffNote(int midiNote, String name, int step, boolean isSharp, double x, Color color) {
            this.midiNote = midiNote;
            this.name = name;
            this.step = step;
            this.isSharp = isSharp;
            this.x = x;
            this.color = color;
        }
    }

    private final List<StaffNote> activeNotes = new ArrayList<>();
    private final AnimationTimer animationTimer;

    // Pitch mapping offsets relative to Middle C (C4)
    private static final int[] DIATONIC_OFFSETS = {0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6}; // C, C#, D, D#, E, F, F#, G, G#, A, A#, B

    public StaffView() {
        // Redraw when resized
        widthProperty().addListener(evt -> draw());
        heightProperty().addListener(evt -> draw());

        // Setup smooth scrolling animation at 60 FPS
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateAndDraw();
            }
        };
        animationTimer.start();
    }

    /**
     * Adds a note to the scrolling staff visualization.
     */
    public synchronized void addNote(int midiNote, String noteName, int octave) {
        // Calculate diatonic step relative to C4 (step 0)
        int noteInOctave = midiNote % 12;
        int step = (octave - 4) * 7 + DIATONIC_OFFSETS[noteInOctave];
        boolean isSharp = (noteInOctave == 1 || noteInOctave == 3 || noteInOctave == 6 || noteInOctave == 8 || noteInOctave == 10);

        // Assign a pretty pastel color based on the note name for visual appeal
        Color noteColor = getNoteColor(noteInOctave);

        // Add note starting at the right edge
        double startX = getWidth() > 0 ? getWidth() - 30 : 400;
        
        // Prevent duplicate spam of the exact same pitch in close succession
        if (!activeNotes.isEmpty()) {
            StaffNote last = activeNotes.get(activeNotes.size() - 1);
            if (last.midiNote == midiNote && startX - last.x < 35) {
                return; // Too close, skip duplicating
            }
        }

        activeNotes.add(new StaffNote(midiNote, noteName + octave, step, isSharp, startX, noteColor));
    }

    /**
     * Clear all notes from the staff.
     */
    public synchronized void clear() {
        activeNotes.clear();
        draw();
    }

    private Color getNoteColor(int noteInOctave) {
        switch (noteInOctave) {
            case 0: case 1: return Color.web("#ff79c6"); // Pink (C, C#)
            case 2: case 3: return Color.web("#ffb86c"); // Orange (D, D#)
            case 4:         return Color.web("#f1fa8c"); // Yellow (E)
            case 5: case 6: return Color.web("#50fa7b"); // Green (F, F#)
            case 7: case 8: return Color.web("#8be9fd"); // Cyan (G, G#)
            case 9: case 10:return Color.web("#bd93f9"); // Lavender (A, A#)
            default:        return Color.web("#ff79c6"); // Pink (B)
        }
    }

    /**
     * Frame update method to scroll notes and redraw.
     */
    private synchronized void updateAndDraw() {
        double speed = 1.0; // Scroll speed: pixels per frame
        double clefBarrierX = 75.0; // Don't scroll past the treble clef area

        Iterator<StaffNote> it = activeNotes.iterator();
        while (it.hasNext()) {
            StaffNote note = it.next();
            note.x -= speed;
            if (note.x < clefBarrierX) {
                it.remove(); // Remove note once it reaches the clef area
            }
        }
        draw();
    }

    /**
     * Renders the staff background and scrolling notes.
     */
    public synchronized void draw() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();

        if (w <= 0 || h <= 0) return;

        // 1. Draw Background
        gc.clearRect(0, 0, w, h);
        gc.setFill(Color.web("#221b35")); // Match cute dark theme
        gc.fillRoundRect(0, 0, w, h, 16, 16);

        // Define staff sizing constants
        double lineSpacing = 10.0;
        double yCenter = h / 2.0;
        
        // 2. Draw 5 Staff Lines (E4, G4, B4, D5, F5)
        gc.setStroke(Color.web("#44475a"));
        gc.setLineWidth(1.2);
        for (int i = -2; i <= 2; i++) {
            double y = yCenter + i * lineSpacing;
            gc.strokeLine(10, y, w - 10, y);
        }

        // 3. Draw Treble Clef Symbol (large unicode symbol)
        gc.setFill(Color.web("#bd93f9")); // Lavender Clef
        gc.setFont(Font.font("Segoe UI Symbol", 44));
        gc.fillText("\uD834\uDD1E", 20, yCenter + 2.2 * lineSpacing);

        // 4. Draw Scrolling Notes
        gc.setFont(Font.font("System", 10));
        
        for (StaffNote note : activeNotes) {
            // Compute Y-coordinate relative to middle line B4 (step 6)
            double y = yCenter - (note.step - 6) * (lineSpacing / 2.0);

            // Draw ledger lines if outside the staff lines
            gc.setStroke(Color.web("#f8f8f2"));
            gc.setLineWidth(1.0);
            drawLedgerLines(gc, note.step, note.x, lineSpacing, yCenter);

            // Draw note head (tilted oval)
            gc.save();
            gc.translate(note.x, y);
            gc.rotate(-20);
            gc.setFill(note.color);
            gc.fillOval(-6, -4.5, 12, 9);
            gc.restore();

            // Draw stem (Standard musical directions)
            gc.setStroke(Color.web("#f8f8f2"));
            gc.setLineWidth(1.5);
            if (note.step < 6) {
                // Stem goes UP on the right side
                gc.strokeLine(note.x + 5, y, note.x + 5, y - 26);
            } else {
                // Stem goes DOWN on the left side
                gc.strokeLine(note.x - 5, y, note.x - 5, y + 26);
            }

            // Draw sharp symbol if accidental
            if (note.isSharp) {
                gc.setFill(Color.web("#ffb86c"));
                gc.setFont(Font.font("System", 12));
                gc.fillText("#", note.x - 14, y + 4.5);
            }

            // Draw note name text (label)
            gc.setFill(Color.web("#f8f8f2"));
            gc.setFont(Font.font("System", 9));
            if (note.step < 6) {
                gc.fillText(note.name, note.x - 7, y + 17); // Label below
            } else {
                gc.fillText(note.name, note.x - 7, y - 17); // Label above
            }
        }

        // Draw staff border
        gc.setStroke(Color.web("#3d345c"));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(0, 0, w, h, 16, 16);
    }

    /**
     * Helper to draw ledger lines for notes outside the standard staff lines.
     */
    private void drawLedgerLines(GraphicsContext gc, int step, double x, double lineSpacing, double yCenter) {
        if (step <= 0) {
            // Draw ledger lines below bottom line E4 (step 2)
            // C4 (step 0), A3 (step -2), etc., are on lines
            for (int s = 0; s >= step; s--) {
                if (s % 2 == 0) {
                    double y = yCenter - (s - 6) * (lineSpacing / 2.0);
                    gc.strokeLine(x - 9, y, x + 9, y);
                }
            }
        } else if (step >= 12) {
            // Draw ledger lines above top line F5 (step 10)
            // A5 (step 12), C6 (step 14), etc., are on lines
            for (int s = 12; s <= step; s++) {
                if (s % 2 == 0) {
                    double y = yCenter - (s - 6) * (lineSpacing / 2.0);
                    gc.strokeLine(x - 9, y, x + 9, y);
                }
            }
        }
    }
}
