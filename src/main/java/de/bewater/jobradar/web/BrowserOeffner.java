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
        String adresse = "http://localhost:" + port;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(adresse));
                return;
            }
            log.info("Browser bitte selbst oeffnen: {}", adresse);
        } catch (Exception e) {
            // Kein Grund, die Anwendung deswegen zu beenden.
            log.info("Browser liess sich nicht oeffnen ({}). Adresse: {}", e.getMessage(), adresse);
        }
    }
}
