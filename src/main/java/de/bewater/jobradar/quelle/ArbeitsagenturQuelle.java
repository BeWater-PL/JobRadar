package de.bewater.jobradar.quelle;

import de.bewater.jobradar.config.SuchProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Bindet die Arbeitsagentur als Jobquelle ein: alle Suchprofile nacheinander,
 * genau wie der Suchlauf es vorher selbst gemacht hat.
 */
@Component
@Order(0)
public class ArbeitsagenturQuelle implements Jobquelle {

    private final ArbeitsagenturClient client;
    private final SuchProperties props;

    public ArbeitsagenturQuelle(ArbeitsagenturClient client, SuchProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public String name() {
        return "Arbeitsagentur";
    }

    @Override
    public List<Stellenanzeige> lade() {
        List<Stellenanzeige> gefunden = new ArrayList<>();
        for (SuchProperties.Profil profil : props.getProfile()) {
            gefunden.addAll(client.suche(profil, props.getMaxAlterTage()));
        }
        return gefunden;
    }
}
