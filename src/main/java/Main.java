/**
 * Entry point launcher for the Music Analyzer & Player.
 * By launching from a class that does not extend Application, 
 * we can bypass the JavaFX module-path restrictions in modern JDKs.
 */
public class Main {
    public static void main(String[] args) {
        ui.MainApp.main(args);
    }
}
