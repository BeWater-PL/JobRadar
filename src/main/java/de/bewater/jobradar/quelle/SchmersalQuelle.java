package de.bewater.jobradar.quelle;

import com.fasterxml.jackson.databind.JsonNode;
import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Schmersal ueber die onlyfy-Jobliste (format=json). */
@Component
@Order(60)
public class SchmersalQuelle extends JsonQuelleBasis {

    public SchmersalQuelle(QuellenProperties props) {
        super(props, "schmersal");
    }

    @Override
    public String name() {
        return "Schmersal";
    }

    @Override
    public List<Stellenanzeige> parse(String antwort) throws Exception {
        JsonNode wurzel = json.readTree(antwort);
        String firma = oder(text(wurzel, "company"), oder(konfig.getFirma(), name()));
        List<Stellenanzeige> ergebnis = new ArrayList<>();
        for (JsonNode j : wurzel.path("jobs")) {
            Stellenanzeige a = anzeige(
                    firma,
                    text(j, "title"),
                    text(j, "city", "title"),
                    text(j, "showUrl"),
                    text(j, "shortHandle"),
                    datum(text(j, "published_at")),
                    null);
            a.setRemote(text(j, "remote") != null);
            ergebnis.add(a);
        }
        return ergebnis;
    }
}
