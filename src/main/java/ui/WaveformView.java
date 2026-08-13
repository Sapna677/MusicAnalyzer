package ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

/**
 * Custom Canvas to render the audio waveform and playback head.
 */
public class WaveformView extends Canvas {

    public interface SeekListener {
        void onSeek(double ratio);
    }

    private float[] samples;
    private double playProgressRatio = 0.0;
    private SeekListener seekListener;

    public WaveformView() {
        // Redraw when width or height changes to make the component fully responsive
        widthProperty().addListener(evt -> draw());
        heightProperty().addListener(evt -> draw());

        // Handle mouse click to seek
        setOnMouseClicked(event -> {
            if (seekListener != null && getWidth() > 0) {
                double ratio = event.getX() / getWidth();
                seekListener.onSeek(Math.max(0.0, Math.min(1.0, ratio)));
            }
        });
    }

    public void setSeekListener(SeekListener seekListener) {
        this.seekListener = seekListener;
    }

    /**
     * Sets the float samples representing the audio and triggers a redraw.
     */
    public void setAudioSamples(float[] samples) {
        this.samples = samples;
        this.playProgressRatio = 0.0;
        draw();
    }

    /**
     * Updates the playhead progress and triggers a redraw.
     * @param ratio Current time divided by total duration (0.0 to 1.0).
     */
    public void setPlaybackProgress(double ratio) {
        this.playProgressRatio = ratio;
        draw();
    }

    /**
     * Clears and redraws the waveform canvas.
     */
    public void draw() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();

        if (w <= 0 || h <= 0) return;

        // 1. Draw Background
        gc.clearRect(0, 0, w, h);
        gc.setFill(Color.web("#221b35")); // Deep dark lavender
        gc.fillRoundRect(0, 0, w, h, 16, 16);

        if (samples == null || samples.length == 0) {
            // Draw cute placeholder text when no file is loaded
            gc.setFill(Color.web("#bd93f9")); // Lavender
            gc.setFont(javafx.scene.text.Font.font("System", 14));
            gc.fillText("🎵 Upload an audio file to view waveform 🌸", w / 2 - 130, h / 2 + 5);
            return;
        }

        // 2. Draw Waveform
        double centerY = h / 2.0;
        int numSamples = samples.length;
        
        // Define a beautiful pink-purple gradient for the waveform
        LinearGradient gradient = new LinearGradient(
                0, 0, w, 0, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ff79c6")),   // Pink
                new Stop(0.5, Color.web("#bd93f9")), // Lavender
                new Stop(1.0, Color.web("#8be9fd"))  // Cyan
        );
        gc.setStroke(gradient);
        gc.setLineWidth(2.0);

        // Calculate average peaks for each pixel column
        int samplesPerPixel = Math.max(1, numSamples / (int) w);
        for (int x = 0; x < w; x++) {
            int startIdx = x * samplesPerPixel;
            int endIdx = Math.min(numSamples, startIdx + samplesPerPixel);

            if (startIdx >= numSamples) break;

            float min = 1.0f;
            float max = -1.0f;
            for (int i = startIdx; i < endIdx; i++) {
                float s = samples[i];
                if (s < min) min = s;
                if (s > max) max = s;
            }

            // Draw a vertical line from min to max amplitude
            double y1 = centerY + (min * centerY * 0.85);
            double y2 = centerY + (max * centerY * 0.85);
            
            // Highlight played versus unplayed part of waveform
            double currentXRatio = x / w;
            if (currentXRatio < playProgressRatio) {
                gc.setStroke(gradient);
            } else {
                gc.setStroke(Color.web("#44475a")); // Dark grey for unplayed section
            }
            gc.strokeLine(x, y1, x, y2);
        }

        // 3. Draw Playback Progress Indicator (Playhead)
        double playheadX = playProgressRatio * w;
        
        // Draw the vertical line
        gc.setStroke(Color.web("#ff79c6")); // Glowing pink playhead
        gc.setLineWidth(1.5);
        gc.strokeLine(playheadX, 0, playheadX, h);

        // Draw a cute handle circle at the top
        gc.setFill(Color.web("#ff79c6"));
        gc.fillOval(playheadX - 4, 0, 8, 8);
        
        // Draw a subtle border around the canvas
        gc.setStroke(Color.web("#3d345c"));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(0, 0, w, h, 16, 16);
    }
}
