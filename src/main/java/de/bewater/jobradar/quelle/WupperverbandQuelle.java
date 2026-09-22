package de.bewater.jobradar.quelle;

import com.fasterxml.jackson.databind.JsonNode;
import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Wupperverband, Bewerbermanagement d.vinci: ein einfaches JSON-Array aller Ausschreibungen. */
@Component
@Order(20)
public class WupperverbandQuelle extends JsonQuelleBasis {

    public WupperverbandQuelle(QuellenProperties props) {
        super(props, "wupperverband");
    }

    @Override
    public String name() {
        return "Wupperverband";
    }

    @Override
    public List<Stellenanzeige> parse(String antwort) throws Exception {
        List<Stellenanzeige> ergebnis = new ArrayList<>();
        for (JsonNode j : json.readTree(antwort)) {
            JsonNode oeffnung = j.path("jobOpening");
            ergebnis.add(anzeige(
                    oder(text(oeffnung, "company", "name"), oder(konfig.getFirma(), name())),
                    text(j, "position"),
                    nurOrt(text(oeffnung, "locations", 0, "name")),
                    text(j, "jobPublicationURL"),
                    oder(text(oeffnung, "reference"), text(j, "id")),
                    datum(text(j, "startDate")),
                    null));
        }
        return ergebnis;
    }

    /** "Hauptverwaltung, Wuppertal" -> "Wuppertal". */
    static String nurOrt(String standort) {
        if (standort == null) {
            return null;
        }
        int komma = standort.lastIndexOf(',');
        return komma >= 0 ? standort.substring(komma + 1).trim() : standort.trim();
    }
}
