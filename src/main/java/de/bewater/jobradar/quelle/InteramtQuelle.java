package de.bewater.jobradar.quelle;

import com.fasterxml.jackson.databind.JsonNode;
import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Stellenportal des oeffentlichen Dienstes. Der Webservice liefert nur je
 * Partner-ID (= Behoerde); ohne Parameter antwortet er 404 mit {"error":...}.
 * Stadt Wuppertal = 1714, Gebaeudemanagement Wuppertal = 2732, Jobcenter = 1409.
 */
@Component
@Order(10)
public class InteramtQuelle extends JsonQuelleBasis {

    public InteramtQuelle(QuellenProperties props) {
        super(props, "interamt");
    }

    @Override
    public String name() {
        return "Interamt";
    }

    @Override
    public List<Stellenanzeige> lade() {
        List<Stellenanzeige> alle = new ArrayList<>();
        int fehler = 0;
        for (Integer partner : konfig.getPartnerIds()) {
            String url = konfig.getUrl().replace("{partner}", String.valueOf(partner));
            try {
                List<Stellenanzeige> treffer = parse(hole(url));
                log.info("Interamt Partner {}: {} Treffer", partner, treffer.size());
                alle.addAll(treffer);
            } catch (Exception e) {
                // Eine Behoerde darf die anderen nicht mitreissen.
                fehler++;
                log.warn("Interamt Partner {} fehlgeschlagen: {}", partner, e.getMessage());
            }
        }
        if (fehler > 0 && fehler == konfig.getPartnerIds().size()) {
            throw new IllegalStateException("alle " + fehler + " Partner-Abfragen fehlgeschlagen");
        }
        return alle;
    }

    @Override
    public List<Stellenanzeige> parse(String antwort) throws Exception {
        JsonNode wurzel = json.readTree(antwort);
        if (wurzel.has("error")) {
            throw new IllegalStateException("Interamt meldet: " + wurzel.get("error").asText());
        }
        List<Stellenanzeige> ergebnis = new ArrayList<>();
        for (JsonNode s : wurzel.path("Stellenangebote")) {
            String id = text(s, "Id");
            JsonNode ort = erster(s.path("StellenangebotOrt"));
            ergebnis.add(anzeige(
                    text(s, "Behoerde"),
                    text(s, "StellenBezeichnung"),
                    text(ort, "Ort"),
                    id != null ? "https://interamt.de/koop/app/stelle?id=" + id : null,
                    id,
                    datum(text(s, "Daten", "Eingestellt")),
                    text(ort, "PLZ")));
        }
        return ergebnis;
    }
}
