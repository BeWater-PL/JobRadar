package de.bewater.jobradar.quelle;

import com.fasterxml.jackson.databind.JsonNode;
import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Riedel Communications spiegelt seine softgarden-Liste als jobs.json auf der
 * eigenen Website. Alle Zusatzfelder liegen in "config" als String-Arrays.
 */
@Component
@Order(40)
public class RiedelQuelle extends JsonQuelleBasis {

    private static final String ANZEIGEN_BASIS = "https://riedelcommunications.softgarden.io/job/";

    public RiedelQuelle(QuellenProperties props) {
        super(props, "riedel");
    }

    @Override
    public String name() {
        return "Riedel Communications";
    }

    @Override
    public List<Stellenanzeige> parse(String antwort) throws Exception {
        List<Stellenanzeige> ergebnis = new ArrayList<>();
        for (JsonNode r : json.readTree(antwort).path("results")) {
            JsonNode config = r.path("config");
            String id = text(r, "jobPostingId");
            Stellenanzeige a = anzeige(
                    oder(text(config, "sg_company", 0), oder(konfig.getFirma(), name())),
                    text(r, "title"),
                    text(config, "ProjectGeoLocationCity", 0),
                    id != null ? ANZEIGEN_BASIS + id : null,
                    id,
                    datum(r.path("jobStartDate")),
                    null);
            // z.B. "REMOTE_FLEXIBLE" oder "REMOTE_ONLY"; "ON_SITE" zaehlt nicht.
            String remote = text(config, "sg_remote_status", 0);
            a.setRemote(remote != null && remote.toUpperCase().startsWith("REMOTE"));
            ergebnis.add(a);
        }
        return ergebnis;
    }
}
