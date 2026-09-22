package de.bewater.jobradar;

import de.bewater.jobradar.config.SuchProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import de.bewater.jobradar.filter.Einsteigerfilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EinsteigerfilterTest {

    private Einsteigerfilter filter;

    @BeforeEach
    void aufbauen() {
        SuchProperties props = new SuchProperties();
        props.setAusschlussWorte(List.of("senior", "teamleiter", "ausbildung", "werkstudent"));
        props.setPflichtWorte(List.of("entwickler", "developer", "fachinformatik", "it-anwendung", "it-entwickl",
                "software engineer", "systems engineer"));
        props.setErlaubteOrte(List.of("Wuppertal", "Solingen", "Ennepetal", "Hagen"));
        props.setEinsteigerWorte(List.of("junior", "berufseinsteiger", "absolvent"));
        props.setTechnologien(List.of("java", "spring", "sql"));
        filter = new Einsteigerfilter(props);
    }

    @Test
    @DisplayName("Senior-Stellen fliegen raus")
    void seniorFliegtRaus() {
        Stellenanzeige a = new Stellenanzeige("Muster GmbH", "Senior Java Entwickler", "Wuppertal");
        Einsteigerfilter.Urteil urteil = filter.bewerte(a);
        assertThat(urteil.passt()).isFalse();
        assertThat(urteil.begruendung()).contains("senior");
    }

    @Test
    @DisplayName("Ausbildungsstellen fliegen raus - der Abschluss ist durch")
    void ausbildungFliegtRaus() {
        Stellenanzeige a = new Stellenanzeige("Muster GmbH", "Ausbildung Fachinformatiker", "Wuppertal");
        assertThat(filter.bewerte(a).passt()).isFalse();
    }

    @Test
    @DisplayName("Junior mit passender Technik bekommt viele Punkte")
    void juniorMitTechnikPunktet() {
        Stellenanzeige a = new Stellenanzeige("Muster GmbH", "Junior Java Entwickler Spring", "Wuppertal");
        a.setEntfernungKm(5);
        Einsteigerfilter.Urteil urteil = filter.bewerte(a);

        assertThat(urteil.passt()).isTrue();
        // 30 (junior) + 15 (java) + 15 (spring) + 25 (bis 7 km)
        assertThat(urteil.punkte()).isEqualTo(85);
        assertThat(urteil.begruendung()).contains("junior").contains("java");
    }

    @Test
    @DisplayName("Naehe schlaegt Ferne")
    void naeheSchlaegtFerne() {
        Stellenanzeige nah = new Stellenanzeige("A GmbH", "Java Entwickler", "Wuppertal");
        nah.setEntfernungKm(8);
        Stellenanzeige fern = new Stellenanzeige("B GmbH", "Java Entwickler", "Hagen");
        fern.setEntfernungKm(300);

        assertThat(filter.bewerte(nah).punkte()).isGreaterThan(filter.bewerte(fern).punkte());
    }

    @Test
    @DisplayName("Neutrale Anzeige kommt durch, sammelt aber wenig Punkte")
    void neutraleAnzeigeKommtDurch() {
        Stellenanzeige a = new Stellenanzeige("Muster GmbH", "Entwickler", "Solingen");
        Einsteigerfilter.Urteil urteil = filter.bewerte(a);
        assertThat(urteil.passt()).isTrue();
        assertThat(urteil.punkte()).isZero();
    }

    @Test
    @DisplayName("Ohne Entwickler-Wort im Titel fliegt die Anzeige raus")
    void ohnePflichtwortFliegtRaus() {
        for (String titel : List.of("Industriemechaniker (m/w/d)", "Sachbearbeiter Buchhaltung",
                "Gebietsverkaufsleiter Aussendienst", "Staplerfahrer")) {
            Einsteigerfilter.Urteil urteil = filter.bewerte(new Stellenanzeige("Muster GmbH", titel, "Wuppertal"));
            assertThat(urteil.passt()).as(titel).isFalse();
            assertThat(urteil.begruendung()).contains("kein Entwickler-Wort");
        }
    }

    @Test
    @DisplayName("Pflichtwort zaehlt als Teilstring und ohne Ruecksicht auf Gross-/Kleinschreibung")
    void pflichtwortAlsTeilstring() {
        for (String titel : List.of("Softwareentwicklerin Java", "Web Developer",
                "Fachinformatikerin Anwendungsentwicklung", "Junior Software Engineer", "Systems Engineer",
                "IT-Anwendungsbetreuer", "IT-Entwicklerin")) {
            assertThat(filter.bewerte(new Stellenanzeige("Muster GmbH", titel, "Wuppertal")).passt())
                    .as(titel).isTrue();
        }
    }

    @Test
    @DisplayName("Pflichtwort nur im Firmennamen reicht nicht")
    void pflichtwortImFirmennamenReichtNicht() {
        Stellenanzeige a = new Stellenanzeige("Web Solutions GmbH", "Vertriebsmitarbeiter", "Wuppertal");
        assertThat(filter.bewerte(a).passt()).isFalse();
    }

    @Test
    @DisplayName("Blosses 'Engineer', 'Web' oder 'IT-' im Titel reicht nicht mehr")
    void engineerUndWebAlleinReichenNicht() {
        for (String titel : List.of("5G RF Network Engineer (m/f/d)", "Development Engineer - Private 5G",
                "Digital Marketing Specialist - Paid Media & Web", "IT-Systemelektroniker / Elektroniker Produktion",
                "IT-Systemadministrator")) {
            assertThat(filter.bewerte(new Stellenanzeige("Muster GmbH", titel, "Wuppertal")).passt())
                    .as(titel).isFalse();
        }
    }

    @Test
    @DisplayName("Ort ausserhalb der Liste fliegt raus, Umkreisorte und Teilstrings bleiben")
    void ortsfilter() {
        assertThat(filter.bewerte(new Stellenanzeige("Muster", "Java Entwickler", "Wien")).passt()).isFalse();
        assertThat(filter.bewerte(new Stellenanzeige("Muster", "Java Entwickler", "Laval")).begruendung())
                .contains("Laval").contains("nicht im Umkreis");
        assertThat(filter.bewerte(new Stellenanzeige("Muster", "Java Entwickler", "Solingen")).passt()).isTrue();
        assertThat(filter.bewerte(new Stellenanzeige("Muster", "Java Entwickler", "Wuppertal-Elberfeld")).passt()).isTrue();
        assertThat(filter.bewerte(new Stellenanzeige("Muster", "Java Entwickler", "42119 Wuppertal")).passt()).isTrue();
    }

    @Test
    @DisplayName("Fehlt der Ort, bleibt die Anzeige drin")
    void ohneOrtBleibtDrin() {
        assertThat(filter.bewerte(new Stellenanzeige("Muster", "Java Entwickler", null)).passt()).isTrue();
        assertThat(filter.bewerte(new Stellenanzeige("Muster", "Java Entwickler", "  ")).passt()).isTrue();
    }

    @Test
    @DisplayName("Remote laut Quelle oder Titel hebt den Ortsfilter auf")
    void remoteHebtOrtsfilterAuf() {
        Stellenanzeige lautQuelle = new Stellenanzeige("Muster", "Java Entwickler", "Berlin");
        lautQuelle.setRemote(true);
        assertThat(filter.bewerte(lautQuelle).passt()).isTrue();
        assertThat(filter.bewerte(lautQuelle).begruendung()).contains("Remote");

        for (String titel : List.of("Java Entwickler (Remote)", "Java Entwickler im Homeoffice",
                "Java Entwickler - 100% Home Office", "Java Entwickler Home-Office")) {
            assertThat(filter.bewerte(new Stellenanzeige("Muster", titel, "Hamburg")).passt()).as(titel).isTrue();
        }
        assertThat(filter.bewerte(new Stellenanzeige("Muster", "Java Entwickler", "Hamburg")).passt()).isFalse();
    }

    @Test
    @DisplayName("Leere Ortsliste heisst: kein Ortsfilter")
    void leereOrtslisteIstKeinFilter() {
        SuchProperties props = new SuchProperties();
        assertThat(new Einsteigerfilter(props).bewerte(
                new Stellenanzeige("Muster", "Java Entwickler", "Wien")).passt()).isTrue();
    }

    @Test
    @DisplayName("Leere Pflichtliste heisst: keine Pflicht")
    void leerePflichtlisteIstKeinePflicht() {
        SuchProperties props = new SuchProperties();
        assertThat(new Einsteigerfilter(props).bewerte(
                new Stellenanzeige("Muster GmbH", "Schlosser", "Wuppertal")).passt()).isTrue();
    }
}
