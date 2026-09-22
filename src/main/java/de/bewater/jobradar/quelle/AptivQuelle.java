package de.bewater.jobradar.quelle;

import com.fasterxml.jackson.databind.JsonNode;
import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aptiv ueber die Workday-CXS-API (Technical Center Wuppertal). POST mit
 * JSON-Body, hoechstens 20 Treffer je Seite. Der Standortfilter steckt in den
 * Facets und wird bei jedem Lauf frisch gelesen - Workday vergibt die IDs neu,
 * sobald sich etwas an der Standortliste aendert.
 */
@Component
@Order(82)
public class AptivQuelle extends JsonQuelleBasis {

    /** Workday nimmt keine groessere Seite an, egal was man schickt. */
    private static final int SEITE = 20;
    private static final String STANDORT = "Wuppertal";
    private static final Pattern ZAHL = Pattern.compile("\\d+");

    public AptivQuelle(QuellenProperties props) {
        super(props, "aptiv");
    }

    @Override
    public String name() {
        return "Aptiv";
    }

    @Override
    public List<Stellenanzeige> lade() {
        List<Stellenanzeige> alle = new ArrayList<>();
        try {
            JsonNode ungefiltert = json.readTree(sende(konfig.getUrl(), anfrage(null, List.of(), 0)));
            String facette = standortFacette(ungefiltert);
            List<String> ids = standortIds(ungefiltert);
            if (facette == null || ids.isEmpty()) {
                log.warn("{}: kein Standort-Facet fuer '{}' gefunden - Quelle liefert nichts", name(), STANDORT);
                return List.of();
            }
            log.info("{}: Standort-Facet '{}' mit {} ID(s): {}", name(), facette, ids.size(), ids);

            int offset = 0;
            int gesamt;
            do {
                JsonNode wurzel = json.readTree(sende(konfig.getUrl(), anfrage(facette, ids, offset)));
                gesamt = wurzel.path("total").asInt(0);
                int seite = wurzel.path("jobPostings").size();
                alle.addAll(uebersetze(wurzel));
                offset += SEITE;
                if (seite == 0) {
                    break; // Schutz gegen Endlosschleife bei leerer Seite
                }
            } while (offset < gesamt);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        log.info("{}: {} Treffer", name(), alle.size());
        return alle;
    }

    @Override
    public List<Stellenanzeige> parse(String antwort) throws Exception {
        return uebersetze(json.readTree(antwort));
    }

    // ---- Anfrage ----------------------------------------------------------

    private String anfrage(String facette, List<String> ids, int offset) {
        StringBuilder b = new StringBuilder("{\"appliedFacets\":{");
        if (facette != null && !ids.isEmpty()) {
            b.append('"').append(facette).append("\":[");
            for (int i = 0; i < ids.size(); i++) {
                b.append(i > 0 ? "," : "").append('"').append(ids.get(i)).append('"');
            }
            b.append(']');
        }
        b.append("},\"limit\":").append(SEITE)
                .append(",\"offset\":").append(offset)
                .append(",\"searchText\":\"\"}");
        return b.toString();
    }

    // ---- Facets -----------------------------------------------------------

    /**
     * Name des Facets, in dem Wuppertal steht. Workday nennt es mal "Location",
     * mal "locations" und haengt es teils unter eine Gruppe - deshalb wird der
     * ganze Baum durchsucht statt ein Name festgeschrieben.
     */
    private String standortFacette(JsonNode wurzel) {
        for (JsonNode f : wurzel.path("facets")) {
            String treffer = suchFacette(f);
            if (treffer != null) {
                return treffer;
            }
        }
        return null;
    }

    private String suchFacette(JsonNode facet) {
        String parameter = text(facet, "facetParameter");
        for (JsonNode wert : facet.path("values")) {
            if (passt(text(wert, "descriptor")) && parameter != null) {
                return parameter;
            }
            String tiefer = suchFacette(wert);
            if (tiefer != null) {
                return tiefer;
            }
        }
        return null;
    }

    private List<String> standortIds(JsonNode wurzel) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode f : wurzel.path("facets")) {
            sammle(f, ids);
        }
        return new ArrayList<>(ids);
    }

    private void sammle(JsonNode facet, Set<String> ids) {
        for (JsonNode wert : facet.path("values")) {
            String id = text(wert, "id");
            if (id != null && passt(text(wert, "descriptor"))) {
                ids.add(id);
            }
            sammle(wert, ids);
        }
    }

    private boolean passt(String beschreibung) {
        return beschreibung != null && beschreibung.toLowerCase().contains(STANDORT.toLowerCase());
    }

    // ---- Umwandlung -------------------------------------------------------

    private List<Stellenanzeige> uebersetze(JsonNode wurzel) {
        List<Stellenanzeige> ergebnis = new ArrayList<>();
        for (JsonNode j : wurzel.path("jobPostings")) {
            String pfad = text(j, "externalPath");
            ergebnis.add(anzeige(
                    oder(konfig.getFirma(), name()),
                    text(j, "title"),
                    // locationsText ist oft nur "2 Standorte" - gefiltert wird ohnehin auf Wuppertal.
                    STANDORT,
                    pfad != null ? oder(konfig.getLinkBasis(), "") + pfad : null,
                    // bulletFields[0] ist die Req-ID (J000704207).
                    text(j, "bulletFields", 0),
                    relativesDatum(text(j, "postedOn")),
                    null));
        }
        return ergebnis;
    }

    /**
     * Workday liefert kein Datum, sondern Text: "Heute ausgeschrieben",
     * "Vor 3 Tagen ausgeschrieben", "Vor mehr als 30 Tagen ausgeschrieben"
     * bzw. englisch "Posted Today", "Posted 3 Days Ago", "Posted 30+ Days Ago".
     * Daraus wird heute minus N Tage; unbekannter Text ergibt null.
     */
    LocalDate relativesDatum(String roh) {
        if (roh == null || roh.isBlank()) {
            return null;
        }
        String s = roh.toLowerCase();
        if (s.contains("heute") || s.contains("today")) {
            return LocalDate.now();
        }
        if (s.contains("gestern") || s.contains("yesterday")) {
            return LocalDate.now().minusDays(1);
        }
        Matcher m = ZAHL.matcher(s);
        if (m.find() && (s.contains("tag") || s.contains("day"))) {
            return LocalDate.now().minusDays(Long.parseLong(m.group()));
        }
        return null;
    }
}
