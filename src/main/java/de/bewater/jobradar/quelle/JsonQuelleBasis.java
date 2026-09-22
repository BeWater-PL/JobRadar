package de.bewater.jobradar.quelle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Gemeinsamer Unterbau der Arbeitgeber-Feeds: HTTP mit Timeout, JSON-Zugriff
 * ueber JsonNode (die Feeds aendern sich, starre Klassen brechen dann) und
 * ein Datumsparser, der alle vorkommenden Schreibweisen schluckt.
 */
public abstract class JsonQuelleBasis implements Jobquelle {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final DateTimeFormatter DEUTSCH = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    protected final ObjectMapper json = new ObjectMapper();
    protected final QuellenProperties.Quelle konfig;
    private final RestClient rest;

    protected JsonQuelleBasis(QuellenProperties props, String schluessel) {
        this.konfig = props.quelle(schluessel);
        SimpleClientHttpRequestFactory fabrik = new SimpleClientHttpRequestFactory();
        fabrik.setConnectTimeout(TIMEOUT);
        fabrik.setReadTimeout(TIMEOUT);
        this.rest = RestClient.builder()
                .requestFactory(fabrik)
                .defaultHeader("User-Agent", "Mozilla/5.0 (JobRadar)")
                .build();
    }

    @Override
    public boolean aktiv() {
        return konfig.isAktiv() && konfig.getUrl() != null && !konfig.getUrl().isBlank();
    }

    @Override
    public boolean altersfilterAnwenden() {
        return konfig.isAltersfilter();
    }

    /** Wandelt den Rohtext einer Antwort in Anzeigen um - ohne Netz testbar. */
    public abstract List<Stellenanzeige> parse(String antwort) throws Exception;

    /** Standardfall: ein GET auf die konfigurierte URL. Paginierte Quellen ueberschreiben das. */
    @Override
    public List<Stellenanzeige> lade() {
        try {
            List<Stellenanzeige> treffer = parse(hole(konfig.getUrl()));
            log.info("{}: {} Treffer", name(), treffer.size());
            return treffer;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    // ---- HTTP -------------------------------------------------------------

    protected String hole(String url) {
        RestClient.RequestHeadersSpec<?> anfrage = rest.get().uri(url);
        for (Map.Entry<String, String> h : konfig.getHeader().entrySet()) {
            anfrage = anfrage.header(h.getKey(), h.getValue());
        }
        return anfrage.retrieve().body(String.class);
    }

    protected String sende(String url, String body) {
        RestClient.RequestBodySpec anfrage = rest.post().uri(url)
                .header("Content-Type", "application/json");
        for (Map.Entry<String, String> h : konfig.getHeader().entrySet()) {
            anfrage = anfrage.header(h.getKey(), h.getValue());
        }
        return anfrage.body(body).retrieve().body(String.class);
    }

    // ---- JSON-Helfer ------------------------------------------------------

    /** Pfad aus Feldnamen und Indizes; fehlt etwas, kommt null statt Exception. */
    protected static String text(JsonNode knoten, Object... pfad) {
        JsonNode aktuell = knoten;
        for (Object schritt : pfad) {
            if (aktuell == null || aktuell.isMissingNode() || aktuell.isNull()) {
                return null;
            }
            aktuell = schritt instanceof Integer i ? aktuell.path(i) : aktuell.path(schritt.toString());
        }
        if (aktuell == null || aktuell.isMissingNode() || aktuell.isNull()) {
            return null;
        }
        String wert = aktuell.asText();
        return wert.isBlank() ? null : wert;
    }

    /** Erster Eintrag, egal ob der Feed ein Array oder ein einzelnes Objekt liefert. */
    protected static JsonNode erster(JsonNode knoten) {
        if (knoten == null || knoten.isMissingNode() || knoten.isNull()) {
            return knoten;
        }
        return knoten.isArray() ? knoten.path(0) : knoten;
    }

    protected static String oder(String wert, String ersatz) {
        return wert != null ? wert : ersatz;
    }

    // ---- Datum ------------------------------------------------------------

    /**
     * Versteht "04.09.2026", "2026-09-15", ISO-Zeitstempel mit Zone/Offset
     * sowie Epoch-Millis (als Zahl oder Ziffernfolge). Sonst null, nie Exception.
     */
    protected LocalDate datum(JsonNode knoten) {
        if (knoten == null || knoten.isMissingNode() || knoten.isNull()) {
            return null;
        }
        if (knoten.isNumber()) {
            return ausMillis(knoten.asLong());
        }
        return datum(knoten.asText());
    }

    protected LocalDate datum(String roh) {
        if (roh == null || roh.isBlank()) {
            return null;
        }
        String s = roh.trim();
        try {
            if (s.matches("\\d{13}")) {
                return ausMillis(Long.parseLong(s));
            }
            if (s.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
                return LocalDate.parse(s, DEUTSCH);
            }
            if (s.length() >= 10 && s.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                return LocalDate.parse(s.substring(0, 10));
            }
        } catch (Exception e) {
            log.debug("Datum nicht lesbar: {}", roh);
        }
        return null;
    }

    private LocalDate ausMillis(long millis) {
        if (millis <= 0) {
            return null;
        }
        return Instant.ofEpochMilli(millis).atZone(ZoneId.of("Europe/Berlin")).toLocalDate();
    }

    // ---- Anzeige bauen ----------------------------------------------------

    protected Stellenanzeige anzeige(String firma, String titel, String ort,
                                     String link, String refnr, LocalDate veroeffentlicht, String plz) {
        Stellenanzeige a = new Stellenanzeige(oder(firma, name()), oder(titel, "(ohne Titel)"), ort);
        a.setQuelle(name());
        a.setLink(link);
        a.setRefnr(refnr);
        a.setVeroeffentlicht(veroeffentlicht);
        a.setPlz(plz);
        // Entfernung bleibt bewusst leer: kein Geocoding fuer Arbeitgeber-Feeds.
        return a;
    }
}
