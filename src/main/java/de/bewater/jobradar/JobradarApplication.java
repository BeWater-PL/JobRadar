package de.bewater.jobradar;

import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.config.SuchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.awt.Desktop;
import java.net.ServerSocket;
import java.net.URI;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({SuchProperties.class, QuellenProperties.class})
public class JobradarApplication {

    /** Muss zu server.port in application.yml passen. */
    private static final int PORT = 8088;

    public static void main(String[] args) {
        // Zweiter Doppelklick auf die .exe: Die Anwendung laeuft schon im Hintergrund.
        // Dann nur die Trefferliste oeffnen statt mit "Port belegt" abzubrechen -
        // der jpackage-Launcher wuerde daraus sonst "Failed to launch JVM" machen.
        if (laeuftSchon()) {
            browserOeffnen();
            return;
        }
        SpringApplication.run(JobradarApplication.class, args);
    }

    private static boolean laeuftSchon() {
        try (ServerSocket probe = new ServerSocket(PORT)) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static void browserOeffnen() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("http://localhost:" + PORT));
            }
        } catch (Exception e) {
            // Nichts zu tun - die laufende Instanz ist ohnehin unter localhost:8088 erreichbar.
        }
    }
}
