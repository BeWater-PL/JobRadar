package de.bewater.jobradar.quelle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Nur die Felder der Arbeitsagentur-Antwort (Schnittstelle v6), die hier gebraucht werden.
 * Alles andere wird ignoriert, damit ein Feldzuwachs der API nichts kaputt macht.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AaAntwort(List<Stellenangebot> ergebnisliste, Integer maxErgebnisse) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stellenangebot(
            String referenznummer,
            String hauptberuf,
            String stellenangebotsTitel,
            String firma,
            String datumErsteVeroeffentlichung,
            String aenderungsdatum,
            Zeitraum veroeffentlichungszeitraum,
            Boolean homeofficemoeglich,
            Double entfernung,
            List<Lokation> stellenlokationen) {

        /** Die API liefert mal einen Titel, mal nur den Beruf. */
        public String anzeigeTitel() {
            if (stellenangebotsTitel != null && !stellenangebotsTitel.isBlank()) {
                return stellenangebotsTitel;
            }
            return hauptberuf != null ? hauptberuf : "(ohne Titel)";
        }

        /** Erster Arbeitsort, falls vorhanden. */
        public Adresse adresse() {
            if (stellenlokationen == null || stellenlokationen.isEmpty()) {
                return null;
            }
            return stellenlokationen.get(0).adresse();
        }

        /**
         * Datum, ab dem die Anzeige als "veroeffentlicht" gilt. Die API filtert
         * "veroeffentlichtseit" nach dem Aenderungsdatum - eine erneut eingestellte
         * Anzeige zaehlt also als neu. Das wird hier genauso gehalten.
         */
        public String veroeffentlichtAm() {
            if (aenderungsdatum != null && !aenderungsdatum.isBlank()) {
                return aenderungsdatum;
            }
            if (veroeffentlichungszeitraum != null && veroeffentlichungszeitraum.von() != null) {
                return veroeffentlichungszeitraum.von();
            }
            return datumErsteVeroeffentlichung;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Zeitraum(String von, String bis) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Lokation(Adresse adresse) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Adresse(String ort, String plz, String region) {
    }
}
