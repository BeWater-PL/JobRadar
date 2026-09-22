package de.bewater.jobradar;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.bewater.jobradar.quelle.AaAntwort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueft das Einlesen einer echten Antwortstruktur der Arbeitsagentur (v6),
 * ohne dass dafuer das Netz erreichbar sein muss.
 */
class AaAntwortTest {

    private static final String BEISPIEL = """
            {
              "ergebnisliste": [
                {
                  "stellenangebotsart": "ARBEIT",
                  "stellenangebotsTitel": "Junior Softwareentwickler (m/w/d) Java",
                  "arbeitszeitVollzeit": true,
                  "stellenlokationen": [ {
                    "adresse": {
                      "plz": "42289",
                      "ort": "Wuppertal",
                      "region": "NORDRHEIN_WESTFALEN",
                      "land": "DEUTSCHLAND"
                    },
                    "breite": 51.27, "laenge": 7.18
                  } ],
                  "homeofficemoeglich": false,
                  "veroeffentlichungszeitraum": { "von": "2026-03-20" },
                  "datumErsteVeroeffentlichung": "2026-03-20",
                  "aenderungsdatum": "2026-09-15T11:35:01.129",
                  "hauptberuf": "Fachinformatiker/in - Anwendungsentwicklung",
                  "firma": "Muster IT GmbH",
                  "referenznummer": "10000-1191067932-S",
                  "entfernung": 3.4,
                  "unbekanntesNeuesFeld": "darf nicht stoeren"
                },
                {
                  "hauptberuf": "Softwareentwickler/in",
                  "firma": "Zweite GmbH",
                  "datumErsteVeroeffentlichung": "2026-09-14",
                  "referenznummer": "10000-1191067933-S",
                  "stellenlokationen": [ { "adresse": { "ort": "Solingen" } } ]
                }
              ],
              "maxErgebnisse": 2,
              "page": 1,
              "size": 100,
              "facetten": { "arbeitszeit": { "counts": { "VOLLZEIT": 2 } } }
            }
            """;

    @Test
    @DisplayName("Antwort der Jobsuche wird vollstaendig eingelesen")
    void antwortWirdGelesen() throws Exception {
        AaAntwort antwort = new ObjectMapper().readValue(BEISPIEL, AaAntwort.class);

        assertThat(antwort.ergebnisliste()).hasSize(2);

        AaAntwort.Stellenangebot erste = antwort.ergebnisliste().get(0);
        assertThat(erste.firma()).isEqualTo("Muster IT GmbH");
        assertThat(erste.anzeigeTitel()).isEqualTo("Junior Softwareentwickler (m/w/d) Java");
        assertThat(erste.referenznummer()).isEqualTo("10000-1191067932-S");
        assertThat(erste.adresse().ort()).isEqualTo("Wuppertal");
        assertThat(erste.adresse().plz()).isEqualTo("42289");
        assertThat(erste.entfernung()).isEqualTo(3.4);
    }

    @Test
    @DisplayName("Fehlt der Titel, wird der Beruf verwendet")
    void berufAlsErsatzTitel() throws Exception {
        AaAntwort antwort = new ObjectMapper().readValue(BEISPIEL, AaAntwort.class);
        assertThat(antwort.ergebnisliste().get(1).anzeigeTitel()).isEqualTo("Softwareentwickler/in");
    }

    @Test
    @DisplayName("Aenderungsdatum zaehlt als Veroeffentlichung, sonst Erstveroeffentlichung")
    void veroeffentlichungsdatum() throws Exception {
        AaAntwort antwort = new ObjectMapper().readValue(BEISPIEL, AaAntwort.class);
        assertThat(antwort.ergebnisliste().get(0).veroeffentlichtAm()).startsWith("2026-09-15");
        assertThat(antwort.ergebnisliste().get(1).veroeffentlichtAm()).isEqualTo("2026-09-14");
    }

    @Test
    @DisplayName("Unbekannte Felder der API brechen nichts")
    void unbekannteFelderStoerenNicht() throws Exception {
        AaAntwort antwort = new ObjectMapper().readValue(BEISPIEL, AaAntwort.class);
        assertThat(antwort.maxErgebnisse()).isEqualTo(2);
    }
}
