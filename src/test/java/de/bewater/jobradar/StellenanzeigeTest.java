package de.bewater.jobradar;

import de.bewater.jobradar.domain.Stellenanzeige;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StellenanzeigeTest {

    @Test
    @DisplayName("Geschlechterkuerzel machen aus einer Stelle keine zwei")
    void kuerzelWerdenIgnoriert() {
        String a = Stellenanzeige.bildeFingerabdruck("Muster GmbH", "Junior Java-Entwickler (m/w/d)", "Wuppertal");
        String b = Stellenanzeige.bildeFingerabdruck("Muster GmbH", "Junior Java Entwickler (w/m/d)", "Wuppertal");
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("Rechtsform der Firma spielt keine Rolle")
    void rechtsformWirdIgnoriert() {
        String a = Stellenanzeige.bildeFingerabdruck("Muster GmbH & Co. KG", "Softwareentwickler", "Solingen");
        String b = Stellenanzeige.bildeFingerabdruck("Muster", "Softwareentwickler", "Solingen");
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("Umlaute und Gross-/Kleinschreibung fallen zusammen")
    void umlauteWerdenNormalisiert() {
        String a = Stellenanzeige.bildeFingerabdruck("Müller AG", "SOFTWAREENTWICKLER", "Köln");
        String b = Stellenanzeige.bildeFingerabdruck("Mueller ag", "softwareentwickler", "koeln");
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("Verschiedene Stellen bleiben verschieden")
    void echteUnterschiedeBleiben() {
        String a = Stellenanzeige.bildeFingerabdruck("Muster GmbH", "Junior Java-Entwickler", "Wuppertal");
        String b = Stellenanzeige.bildeFingerabdruck("Muster GmbH", "Junior PHP-Entwickler", "Wuppertal");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Gleiche Stelle an anderem Ort ist eine andere Stelle")
    void ortZaehlt() {
        String a = Stellenanzeige.bildeFingerabdruck("Muster GmbH", "Softwareentwickler", "Wuppertal");
        String b = Stellenanzeige.bildeFingerabdruck("Muster GmbH", "Softwareentwickler", "Hamburg");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Fehlende Angaben stuerzen nicht ab")
    void nullWerteSindHarmlos() {
        assertThat(Stellenanzeige.bildeFingerabdruck(null, "Entwickler", null)).isEqualTo("|entwickler|");
    }
}
