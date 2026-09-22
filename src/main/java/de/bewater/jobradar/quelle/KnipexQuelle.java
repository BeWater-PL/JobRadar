package de.bewater.jobradar.quelle;

import com.fasterxml.jackson.databind.JsonNode;
import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Knipex, softgarden-Karriereseite mit schema.org-DataFeed. jobLocation kann
 * ein einzelnes Objekt oder ein Array sein - deshalb JsonNode statt Klasse.
 */
@Component
@Order(50)
public class KnipexQuelle extends JsonQuelleBasis {

    public KnipexQuelle(QuellenProperties props) {
        super(props, "knipex");
    }

    @Override
    public String name() {
        return "Knipex";
    }

    @Override
    public List<Stellenanzeige> parse(String antwort) throws Exception {
        List<Stellenanzeige> ergebnis = new ArrayList<>();
        for (JsonNode element : json.readTree(antwort).path("dataFeedElement")) {
            JsonNode item = element.path("item");
            JsonNode adresse = erster(item.path("jobLocation")).path("address");
            ergebnis.add(anzeige(
                    oder(text(item, "identifier", "name"), oder(konfig.getFirma(), name())),
                    text(item, "title"),
                    text(adresse, "addressLocality"),
                    text(item, "url"),
                    text(item, "identifier", "value"),
                    datum(text(item, "datePosted")),
                    text(adresse, "postalCode")));
        }
        return ergebnis;
    }
}
