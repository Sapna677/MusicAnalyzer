package ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import model.Note;
import model.SongInfo;
import service.AudioPlaybackListener;
import service.AudioPlayer;
import service.NoteMapper;
import util.AudioUtils;

import java.io.File;

/**
 * Controller for the MainUI.fxml view.
 */
public class MainController implements AudioPlaybackListener {

    @FXML private Button btnUpload;
    @FXML private Button btnPlay;
    @FXML private Button btnPause;
    @FXML private Button btnStop;
    @FXML private Button btnSkipBack;
    @FXML private Button btnSkipForward;

    @FXML private Label lblFileName;
    @FXML private Label lblDuration;
    @FXML private Label lblSampleRate;
    @FXML private Label lblChannels;
    @FXML private Label lblBitrate;
    @FXML private Label lblFileSize;

    @FXML private Label lblCurrentTime;
    @FXML private Label lblTotalDuration;
    @FXML private Slider sliderProgress;
    @FXML private Slider sliderVolume;

    @FXML private Label lblInstrument;

    // Table elements
    @FXML private TableView<Note> tableNotes;
    @FXML private TableColumn<Note, String> colTime;
    @FXML private TableColumn<Note, String> colNote;
    @FXML private TableColumn<Note, Integer> colOctave;
    @FXML private TableColumn<Note, String> colFrequency;



    // Layout Containers for Custom Canvases
    @FXML private Pane waveformContainer;
    @FXML private Pane staffContainer;

    // Views
    private WaveformView waveformView;
    private StaffView staffView;

    // Services
    private AudioPlayer audioPlayer;
    private final ObservableList<Note> noteList = FXCollections.observableArrayList();
    private boolean isDraggingProgressSlider = false;

    @FXML
    public void initialize() {
        // 1. Instantiate and bind responsive canvases to their FXML containers
        waveformView = new WaveformView();
        waveformContainer.getChildren().add(waveformView);
        waveformView.widthProperty().bind(waveformContainer.widthProperty());
        waveformView.heightProperty().bind(waveformContainer.heightProperty());

        staffView = new StaffView();
        staffContainer.getChildren().add(staffView);
        staffView.widthProperty().bind(staffContainer.widthProperty());
        staffView.heightProperty().bind(staffContainer.heightProperty());

        // 2. Initialize Table columns
        colTime.setCellValueFactory(new PropertyValueFactory<>("timestampStr"));
        colNote.setCellValueFactory(new PropertyValueFactory<>("noteName"));
        colOctave.setCellValueFactory(new PropertyValueFactory<>("octave"));
        colFrequency.setCellValueFactory(new PropertyValueFactory<>("frequencyStr"));
        tableNotes.setItems(noteList);

        // 3. Connect controls
        updateButtonStates();

        // Setup volume slider listener
        sliderVolume.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (audioPlayer != null) {
                audioPlayer.setVolume(newVal.floatValue() / 100.0f);
            }
        });

        // Setup progress slider drag behaviors
        sliderProgress.setOnMousePressed(event -> isDraggingProgressSlider = true);
        sliderProgress.setOnMouseReleased(event -> {
            isDraggingProgressSlider = false;
            if (audioPlayer != null) {
                double ratio = sliderProgress.getValue() / 100.0;
                audioPlayer.seek(ratio);
            }
        });

        // Waveform click-to-seek
        waveformView.setSeekListener(ratio -> {
            if (audioPlayer != null) {
                audioPlayer.seek(ratio);
            }
        });
    }

    /**
     * File Upload Action Handler.
     */
    @FXML
    private void handleUpload() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Audio File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Audio Files (*.mp3, *.wav)", "*.mp3", "*.wav"),
                new FileChooser.ExtensionFilter("MP3 Files (*.mp3)", "*.mp3"),
                new FileChooser.ExtensionFilter("WAV Files (*.wav)", "*.wav")
        );

        Stage stage = (Stage) btnUpload.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            btnUpload.setDisable(true);
            btnUpload.setText("🎀 Loading and Decoding... 🌸");

            // Process audio decoding in a background thread to prevent UI freezing
            Task<Void> loadAudioTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    File fileToDecode = selectedFile;
                    
                    // Decode MP3 to WAV first if necessary
                    if (selectedFile.getName().toLowerCase().endsWith(".mp3")) {
                        fileToDecode = AudioUtils.convertMp3ToWav(selectedFile);
                    }

                    float[] samples = AudioUtils.decodeWavToMonoSamples(fileToDecode);
                    SongInfo info = AudioUtils.getSongInfo(selectedFile, fileToDecode);

                    final File finalWav = fileToDecode;
                    final float[] finalSamples = samples;

                    Platform.runLater(() -> {
                        if (audioPlayer != null) {
                            audioPlayer.stop();
                        }

                        // Create Player instance
                        audioPlayer = new AudioPlayer(finalSamples, info, MainController.this);
                        audioPlayer.setVolume((float) (sliderVolume.getValue() / 100.0f));

                        // Update metadata labels
                        lblFileName.setText(info.getFileName());
                        lblDuration.setText(info.getDurationStr());
                        lblSampleRate.setText(info.getSampleRate() + " Hz");
                        lblChannels.setText(info.getChannels() == 1 ? "Mono" : "Stereo");
                        lblBitrate.setText(info.getBitrate() + " kbps");
                        lblFileSize.setText(info.getFileSizeStr());

                        lblCurrentTime.setText("00:00");
                        lblTotalDuration.setText(info.getDurationStr());
                        sliderProgress.setValue(0.0);

                        // Clear UI notes & graphs
                        noteList.clear();
                        staffView.clear();
                        lblInstrument.setText("Silence");

                        // Update Waveform View
                        waveformView.setAudioSamples(finalSamples);

                        // Enable control buttons
                        updateButtonStates();

                        btnUpload.setDisable(false);
                        btnUpload.setText("📂 Upload Audio (MP3 / WAV) 🎵");
                    });
                    return null;
                }

                @Override
                protected void failed() {
                    Platform.runLater(() -> {
                        btnUpload.setDisable(false);
                        btnUpload.setText("📂 Upload Audio (MP3 / WAV) 🎵");
                        showCuteAlert("Failed Loading Audio 😿", 
                                "An error occurred while decoding: " + getException().getMessage());
                    });
                }
            };

            Thread loadThread = new Thread(loadAudioTask, "Audio Decoder Thread");
            loadThread.setDaemon(true);
            loadThread.start();
        }
    }

    @FXML
    private void handlePlay() {
        if (audioPlayer != null) {
            audioPlayer.play();
            updateButtonStates();
        }
    }

    @FXML
    private void handlePause() {
        if (audioPlayer != null) {
            audioPlayer.pause();
            updateButtonStates();
        }
    }

    @FXML
    private void handleStop() {
        if (audioPlayer != null) {
            audioPlayer.stop();
            updateButtonStates();
        }
    }

    // --- AudioPlaybackListener Implementation Callbacks ---

    @Override
    public void onProgress(double currentTimeSeconds) {
        Platform.runLater(() -> {
            if (!isDraggingProgressSlider) {
                lblCurrentTime.setText(AudioUtils.formatDuration(currentTimeSeconds));
                if (audioPlayer != null && audioPlayer.getSongInfo().getDurationSeconds() > 0) {
                    double ratio = currentTimeSeconds / audioPlayer.getSongInfo().getDurationSeconds();
                    sliderProgress.setValue(ratio * 100.0);
                    waveformView.setPlaybackProgress(ratio);
                }
            }
        });
    }

    @Override
    public void onPitchDetected(float pitch, float rms) {
        if (pitch <= 0.0f) {
            return; // Ignore invalid / silent frequencies
        }

        Platform.runLater(() -> {
            if (audioPlayer == null) return;
            
            Note note = NoteMapper.fromFrequency(pitch, audioPlayer.getCurrentTimeSeconds());
            if (note != null) {
                boolean shouldLog = false;
                
                if (noteList.isEmpty()) {
                    shouldLog = true;
                } else {
                    Note lastLoggedNote = noteList.get(noteList.size() - 1);
                    double elapsed = note.getTimestamp() - lastLoggedNote.getTimestamp();
                    
                    // Log note if name/octave changed, or if 200ms has elapsed to keep lists neat
                    boolean isDifferent = !note.getNoteName().equals(lastLoggedNote.getNoteName()) 
                            || note.getOctave() != lastLoggedNote.getOctave();
                    if (isDifferent || elapsed > 0.20) {
                        shouldLog = true;
                    }
                }

                if (shouldLog) {
                    noteList.add(note);
                    
                    // Restrict note table rows to avoid DOM bloating
                    if (noteList.size() > 150) {
                        noteList.remove(0);
                    }
                    
                    tableNotes.scrollTo(noteList.size() - 1);

                    // Add note to staff view
                    staffView.addNote(note.getMidiNote(), note.getNoteName(), note.getOctave());
                }
            }
        });
    }

    @Override
    public void onInstrumentDetected(String instrument) {
        Platform.runLater(() -> lblInstrument.setText(instrument));
    }

    @Override
    public void onPlaybackStopped() {
        Platform.runLater(() -> {
            lblInstrument.setText("Silence");
            waveformView.setPlaybackProgress(0.0);
            sliderProgress.setValue(0.0);
            lblCurrentTime.setText("00:00");
            updateButtonStates();
        });
    }

    private void updateButtonStates() {
        if (audioPlayer == null) {
            btnPlay.setDisable(true);
            btnPause.setDisable(true);
            btnStop.setDisable(true);
            btnSkipBack.setDisable(true);
            btnSkipForward.setDisable(true);
            return;
        }

        boolean playing = audioPlayer.isPlaying();
        boolean paused = audioPlayer.isPaused();

        btnPlay.setDisable(playing);
        btnPause.setDisable(!playing);
        btnStop.setDisable(!playing && !paused);
        btnSkipBack.setDisable(false);
        btnSkipForward.setDisable(false);
    }

    private void showCuteAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        
        // Add cute theme overrides to alert dialog
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/css/cute-theme.css").toExternalForm());
        dialogPane.getStyleClass().add("card-panel");
        
        alert.showAndWait();
    }



    @FXML
    private void handleSkipBack() {
        if (audioPlayer != null) {
            double duration = audioPlayer.getSongInfo().getDurationSeconds();
            if (duration > 0) {
                double targetTime = audioPlayer.getCurrentTimeSeconds() - 10.0;
                double ratio = Math.max(0.0, targetTime) / duration;
                audioPlayer.seek(ratio);
            }
        }
    }

    @FXML
    private void handleSkipForward() {
        if (audioPlayer != null) {
            double duration = audioPlayer.getSongInfo().getDurationSeconds();
            if (duration > 0) {
                double targetTime = audioPlayer.getCurrentTimeSeconds() + 10.0;
                double ratio = Math.min(duration, targetTime) / duration;
                audioPlayer.seek(ratio);
            }
        }
    }

    /**
     * Stop background threads on window exit.
     */
    public void shutdown() {
        if (audioPlayer != null) {
            audioPlayer.stop();
        }
    }
}
