package de.bewater.jobradar.quelle;

import com.fasterxml.jackson.databind.JsonNode;
import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Erfurt & Sohn ueber die Talention-API. Nur POST mit JSON-Body (GET gibt 405),
 * hoechstens 10 Treffer je Antwort - bei mehr wird mit offset nachgeladen.
 */
@Component
@Order(70)
public class ErfurtQuelle extends JsonQuelleBasis {

    public ErfurtQuelle(QuellenProperties props) {
        super(props, "erfurt");
    }

    @Override
    public String name() {
        return "Erfurt & Sohn";
    }

    @Override
    public List<Stellenanzeige> lade() {
        List<Stellenanzeige> alle = new ArrayList<>();
        int offset = 0;
        try {
            while (true) {
                JsonNode wurzel = json.readTree(sende(konfig.getUrl(), "{\"offset\": " + offset + "}"));
                int seite = wurzel.path("results").size();
                alle.addAll(uebersetze(wurzel));
                offset += seite;
                if (seite == 0 || offset >= wurzel.path("resultsTotal").asInt(0)) {
                    break;
                }
            }
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

    private List<Stellenanzeige> uebersetze(JsonNode wurzel) {
        List<Stellenanzeige> ergebnis = new ArrayList<>();
        for (JsonNode r : wurzel.path("results")) {
            ergebnis.add(anzeige(
                    oder(konfig.getFirma(), name()),
                    text(r, "title"),
                    text(r, "location"),
                    text(r, "url"),
                    text(r, "id"),
                    datum(oder(text(r, "enabledDate"), text(r, "createdDate"))),
                    null));
        }
        return ergebnis;
    }
}
