package de.bewater.jobradar.quelle;

import com.fasterxml.jackson.databind.JsonNode;
import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * bilstein group, Guidecom-Portal hinter einem TYPO3-Proxy. Der Proxy liefert
 * nur mit "Accept: application/json" JSON - ohne den Header kommt eine
 * HTML-Debugseite. Der Header steht deshalb in application.yml.
 */
@Component
@Order(80)
public class BilsteinQuelle extends JsonQuelleBasis {

    public BilsteinQuelle(QuellenProperties props) {
        super(props, "bilstein");
    }

    @Override
    public String name() {
        return "bilstein group";
    }

    @Override
    public List<Stellenanzeige> parse(String antwort) throws Exception {
        if (antwort != null && antwort.stripLeading().startsWith("<")) {
            throw new IllegalStateException("HTML statt JSON - fehlt der Accept-Header?");
        }
        List<Stellenanzeige> ergebnis = new ArrayList<>();
        for (JsonNode a : json.readTree(antwort).path("ausschreibungen")) {
            ergebnis.add(anzeige(
                    oder(text(a, "auftraggeber"), oder(konfig.getFirma(), name())),
                    text(a, "titel"),
                    text(a, "standorte", 0),
                    oder(text(a, "Q3iTalentsoft", "detailViewUrl"), text(a, "applyOnlineUrl")),
                    text(a, "ausschreibungUniqueId"),
                    datum(a.path("erstelltAm")),
                    null));
        }
        return ergebnis;
    }
}
