package de.bewater.jobradar.quelle;

import de.bewater.jobradar.domain.Stellenanzeige;

import java.util.List;

/**
 * Eine Herkunft von Stellenanzeigen - die Arbeitsagentur genauso wie der
 * Karriere-Feed eines einzelnen Arbeitgebers. Der Suchlauf kennt nur dieses
 * Interface und sammelt alle Beans ein, die es implementieren.
 */
public interface Jobquelle {

    /** Sprechender Name, landet in Stellenanzeige.quelle und im Log. */
    String name();

    /** Steht in application.yml auf aktiv? Deaktivierte Quellen ueberspringt der Lauf. */
    default boolean aktiv() {
        return true;
    }

    /**
     * Soll der Suchlauf Anzeigen dieser Quelle nach max-alter-tage verwerfen?
     * Nur sinnvoll, wenn das Veroeffentlichungsdatum wirklich eines ist.
     */
    default boolean altersfilterAnwenden() {
        return true;
    }

    /**
     * Holt alle aktuell verfuegbaren Anzeigen. Darf werfen - der Suchlauf
     * faengt das pro Quelle ab, damit die uebrigen trotzdem durchlaufen.
     */
    List<Stellenanzeige> lade();
}
