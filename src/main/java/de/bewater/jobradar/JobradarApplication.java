package de.bewater.jobradar;

import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.config.SuchProperties;
import de.bewater.jobradar.web.BrowserOeffner;
import de.bewater.jobradar.web.Instanzpruefung;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({SuchProperties.class, QuellenProperties.class})
public class JobradarApplication {

    /** Muss zu server.port in application.yml passen. */
    private static final int PORT = 8088;
    private static final String ADRESSE = "http://localhost:" + PORT;

    public static void main(String[] args) {
        // Zweiter Doppelklick auf die .exe: Die Anwendung laeuft schon im Hintergrund.
        // Dann nur die Trefferliste oeffnen statt mit "Port belegt" abzubrechen -
        // der jpackage-Launcher wuerde daraus sonst "Failed to launch JVM" machen.
        switch (Instanzpruefung.pruefe(PORT)) {
            case LAEUFT_SCHON -> {
                notiz("INFO  JobRadar laeuft bereits - zeige nur die Oberflaeche: " + ADRESSE);
                BrowserOeffner.oeffne(ADRESSE);
                System.exit(0);
            }
            case FREMD -> {
                notiz("ERROR Port " + PORT + " ist von einem anderen Programm belegt."
                        + " JobRadar wurde nicht gestartet - Port freimachen oder server.port aendern.");
                System.exit(1);
            }
            case FREI -> new SpringApplicationBuilder(JobradarApplication.class)
                    // headless(false) laesst java.awt.Desktop als Rueckfallweg zu.
                    .headless(false)
                    .run(args);
        }
    }

    /**
     * Schreibt eine Zeile direkt ins Protokoll. Vor SpringApplication.run steht
     * die Logback-Konfiguration aus application.yml noch nicht, und ohne
     * Konsolenfenster waere die Meldung sonst verloren.
     */
    private static void notiz(String text) {
        Path datei = Path.of(System.getProperty("user.home"), ".jobradar", "jobradar.log");
        String zeile = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "  " + text + System.lineSeparator();
        try {
            Files.createDirectories(datei.getParent());
            Files.writeString(datei, zeile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Ohne Protokoll laeuft es trotzdem weiter - hier ist nichts zu retten.
        }
    }
}
