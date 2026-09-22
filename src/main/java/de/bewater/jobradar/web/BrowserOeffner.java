package de.bewater.jobradar.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;

/**
 * Nach dem Doppelklick auf die .exe soll die Trefferliste von selbst aufgehen.
 * Laeuft absichtlich als letztes (Order), damit der erste Suchlauf schon durch ist.
 */
@Component
@Order(100)
public class BrowserOeffner {

    private static final Logger log = LoggerFactory.getLogger(BrowserOeffner.class);

    private final int port;
    private final boolean aktiv;

    public BrowserOeffner(@Value("${server.port}") int port,
                          @Value("${jobradar.browser-oeffnen:true}") boolean aktiv) {
        this.port = port;
        this.aktiv = aktiv;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void oeffnen() {
        if (!aktiv) {
            return;
        }
        oeffne("http://localhost:" + port);
    }

    /**
     * Oeffnet den Standardbrowser. Statisch, damit die main-Methode das beim
     * zweiten Doppelklick ohne Spring-Kontext benutzen kann.
     *
     * <p>Unter Windows fuehrt der Weg ueber rundll32: java.awt.Desktop faellt
     * aus, sobald der Prozess headless laeuft - und genau das war die Ursache
     * dafuer, dass beim Start nie ein Browser aufging.
     *
     * @return true, wenn ein Browser gestartet werden konnte
     */
    public static boolean oeffne(String adresse) {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            try {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", adresse).start();
                log.info("Browser geoeffnet: {}", adresse);
                return true;
            } catch (Exception e) {
                log.warn("rundll32 hat den Browser nicht gestartet ({}) - versuche java.awt.Desktop", e.getMessage());
            }
        }
        try {
            // Wirkt nur, solange AWT noch nicht hochgefahren ist - schadet sonst nicht.
            System.setProperty("java.awt.headless", "false");
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(adresse));
                log.info("Browser geoeffnet: {}", adresse);
                return true;
            }
        } catch (Exception e) {
            // Kein Grund, die Anwendung deswegen zu beenden.
            log.warn("Browser liess sich nicht oeffnen ({}).", e.getMessage());
        }
        log.warn("Browser bitte selbst oeffnen: {}", adresse);
        return false;
    }
}
