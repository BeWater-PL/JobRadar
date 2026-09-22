package de.bewater.jobradar.quelle;

import com.fasterxml.jackson.databind.JsonNode;
import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Barmenia Gothaer ueber die oeffentliche SmartRecruiters-API. Konzernweit
 * mehrere hundert Stellen, deshalb paginiert; nur Deutschland wird uebernommen.
 */
@Component
@Order(30)
public class GothaerQuelle extends JsonQuelleBasis {

    private static final String ANZEIGEN_BASIS = "https://jobs.smartrecruiters.com/BarmeniaGothaerAG/";

    public GothaerQuelle(QuellenProperties props) {
        super(props, "gothaer");
    }

    @Override
    public String name() {
        return "Barmenia Gothaer";
    }

    @Override
    public List<Stellenanzeige> lade() {
        List<Stellenanzeige> alle = new ArrayList<>();
        int offset = 0;
        int gesamt;
        try {
            do {
                String url = konfig.getUrl() + (konfig.getUrl().contains("?") ? "&" : "?")
                        + "limit=" + konfig.getLimit() + "&offset=" + offset;
                JsonNode wurzel = json.readTree(hole(url));
                gesamt = wurzel.path("totalFound").asInt(0);
                int seite = wurzel.path("content").size();
                alle.addAll(uebersetze(wurzel));
                offset += Math.max(seite, 1);
                if (seite == 0) {
                    break; // Schutz gegen Endlosschleife bei leerer Seite
                }
            } while (offset < gesamt);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        log.info("{}: {} Treffer (von {} konzernweit)", name(), alle.size(), gesamt);
        return alle;
    }

    @Override
    public List<Stellenanzeige> parse(String antwort) throws Exception {
        return uebersetze(json.readTree(antwort));
    }

    private List<Stellenanzeige> uebersetze(JsonNode wurzel) {
        List<Stellenanzeige> ergebnis = new ArrayList<>();
        for (JsonNode p : wurzel.path("content")) {
            if (!"de".equalsIgnoreCase(text(p, "location", "country"))) {
                continue;
            }
            String id = text(p, "id");
            ergebnis.add(anzeige(
                    text(p, "company", "name"),
                    text(p, "name"),
                    text(p, "location", "city"),
                    id != null ? ANZEIGEN_BASIS + id : null,
                    id,
                    datum(text(p, "releasedDate")),
                    text(p, "location", "postalCode")));
        }
        return ergebnis;
    }
}
