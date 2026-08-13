package model;

/**
 * Represents metadata of the loaded audio file.
 */
public class SongInfo {
    private final String fileName;
    private final double durationSeconds;
    private final String durationStr;
    private final int sampleRate;
    private final int channels;
    private final int bitrate; // in kbps
    private final String fileSizeStr;

    public SongInfo(String fileName, double durationSeconds, String durationStr, 
                    int sampleRate, int channels, int bitrate, String fileSizeStr) {
        this.fileName = fileName;
        this.durationSeconds = durationSeconds;
        this.durationStr = durationStr;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitrate = bitrate;
        this.fileSizeStr = fileSizeStr;
    }

    public String getFileName() {
        return fileName;
    }

    public double getDurationSeconds() {
        return durationSeconds;
    }

    public String getDurationStr() {
        return durationStr;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public int getBitrate() {
        return bitrate;
    }

    public String getFileSizeStr() {
        return fileSizeStr;
    }
}
