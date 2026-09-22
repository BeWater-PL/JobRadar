package de.bewater.jobradar.quelle;

import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.config.SuchProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Jede Arbeitgeber-Quelle gegen eine echte, gespeicherte Antwort aus
 * src/test/resources/quellen - ohne Netz. Grosse Antworten sind auf die
 * ersten drei Eintraege gekuerzt, die Struktur ist unveraendert. Namen,
 * Mailadressen, Durchwahlen und Fotos der Ansprechpartner sind durch
 * erfundene ersetzt (Max Beispiel, Erika Muster, ... @example.com).
 */
class JobquellenTest {

    private static final QuellenProperties LEER = new QuellenProperties();

    private static String beispiel(String datei) throws IOException {
        try (InputStream in = JobquellenTest.class.getResourceAsStream("/quellen/" + datei)) {
            assertThat(in).as("Beispieldatei " + datei).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static QuellenProperties aptivKonfig() {
        QuellenProperties props = new QuellenProperties();
        QuellenProperties.Quelle q = new QuellenProperties.Quelle();
        q.setFirma("Aptiv");
        q.setLinkBasis("https://aptiv.wd5.myworkdayjobs.com/de-DE/APTIV_CAREERS");
        props.getQuellen().put("aptiv", q);
        return props;
    }

    private static QuellenProperties codecentricKonfig() {
        QuellenProperties props = new QuellenProperties();
        QuellenProperties.Quelle q = new QuellenProperties.Quelle();
        q.setFirma("codecentric");
        props.getQuellen().put("codecentric", q);
        return props;
    }

    private static SuchProperties orte() {
        SuchProperties suche = new SuchProperties();
        suche.setErlaubteOrte(List.of("Wuppertal", "Solingen", "Remscheid"));
        return suche;
    }

    @Test
    @DisplayName("Interamt: Behoerde, Ort, PLZ und deutsches Datum")
    void interamt() throws Exception {
        List<Stellenanzeige> liste = new InteramtQuelle(LEER).parse(beispiel("interamt-1714.json"));

        assertThat(liste).hasSize(1);
        Stellenanzeige a = liste.get(0);
        assertThat(a.getFirma()).isEqualTo("Stadt Wuppertal");
        assertThat(a.getTitel()).isEqualTo("Rechtssachbearbeitung für den offenen Ganztag");
        assertThat(a.getOrt()).isEqualTo("Wuppertal");
        assertThat(a.getPlz()).isEqualTo("42275");
        assertThat(a.getRefnr()).isEqualTo("1488408");
        assertThat(a.getLink()).isEqualTo("https://interamt.de/koop/app/stelle?id=1488408");
        assertThat(a.getVeroeffentlicht()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(a.getQuelle()).isEqualTo("Interamt");
        assertThat(a.getEntfernungKm()).isNull();
    }

    @Test
    @DisplayName("Interamt: Fehlerantwort wird als Fehler gemeldet, nicht als leere Liste")
    void interamtFehler() {
        assertThatThrownBy(() -> new InteramtQuelle(LEER).parse("{\"error\":\"Kein Parameter angegeben\"}"))
                .hasMessageContaining("Kein Parameter");
    }

    @Test
    @DisplayName("Wupperverband: Ort ohne Gebaeudezusatz, Referenz und ISO-Datum")
    void wupperverband() throws Exception {
        List<Stellenanzeige> liste = new WupperverbandQuelle(LEER).parse(beispiel("wupperverband.json"));

        assertThat(liste).hasSize(3);
        Stellenanzeige a = liste.get(0);
        assertThat(a.getFirma()).isEqualTo("Wupperverband");
        assertThat(a.getTitel()).isEqualTo("Assistent*in der Geschäftsführung (w/m/d)");
        assertThat(a.getOrt()).isEqualTo("Wuppertal");
        assertThat(a.getRefnr()).isEqualTo("2026-0043");
        assertThat(a.getLink()).startsWith("https://karriere.wupperverband.de/de/jobs/10411/");
        assertThat(a.getVeroeffentlicht()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(a.getEntfernungKm()).isNull();
        assertThat(WupperverbandQuelle.nurOrt("Klaeranlage Buchenhofen, Wuppertal")).isEqualTo("Wuppertal");
        assertThat(WupperverbandQuelle.nurOrt("Wuppertal")).isEqualTo("Wuppertal");
    }

    @Test
    @DisplayName("Barmenia Gothaer: SmartRecruiters-Felder, nur Deutschland")
    void gothaer() throws Exception {
        List<Stellenanzeige> liste = new GothaerQuelle(LEER).parse(beispiel("gothaer.json"));

        assertThat(liste).hasSize(3);
        Stellenanzeige a = liste.get(2);
        assertThat(a.getFirma()).isEqualTo("BarmeniaGothaer AG");
        assertThat(a.getTitel()).isEqualTo("SAP-Spezialist*in");
        assertThat(a.getOrt()).isEqualTo("Wuppertal");
        assertThat(a.getPlz()).isEqualTo("42119");
        assertThat(a.getRefnr()).isEqualTo("744000149597231");
        assertThat(a.getLink()).isEqualTo("https://jobs.smartrecruiters.com/BarmeniaGothaerAG/744000149597231");
        assertThat(a.getVeroeffentlicht()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(a.getEntfernungKm()).isNull();

        String ausland = "{\"content\":[{\"id\":\"1\",\"name\":\"X\",\"location\":{\"city\":\"Wien\",\"country\":\"at\"}}]}";
        assertThat(new GothaerQuelle(LEER).parse(ausland)).isEmpty();
    }

    @Test
    @DisplayName("Riedel: Werte aus config-Arrays und Epoch-Millis")
    void riedel() throws Exception {
        List<Stellenanzeige> liste = new RiedelQuelle(LEER).parse(beispiel("riedel.json"));

        assertThat(liste).hasSize(3);
        Stellenanzeige a = liste.get(0);
        assertThat(a.getFirma()).isEqualTo("Riedel Communications GmbH");
        assertThat(a.getTitel()).isEqualTo("Motorsport Solutions Specialist (m/w/d)");
        assertThat(a.getOrt()).isEqualTo("Wuppertal");
        assertThat(a.getRefnr()).isEqualTo("36196151");
        assertThat(a.getLink()).isEqualTo("https://riedelcommunications.softgarden.io/job/36196151");
        assertThat(a.getVeroeffentlicht()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(a.getEntfernungKm()).isNull();
        assertThat(a.isRemote()).as("sg_remote_status REMOTE_FLEXIBLE").isTrue();

        String vorOrt = "{\"results\":[{\"jobPostingId\":1,\"title\":\"T\",\"config\":{\"sg_remote_status\":[\"ON_SITE\"]}}]}";
        assertThat(new RiedelQuelle(LEER).parse(vorOrt).get(0).isRemote()).isFalse();
    }

    @Test
    @DisplayName("Knipex: schema.org-Feed, jobLocation als Objekt oder Array")
    void knipex() throws Exception {
        List<Stellenanzeige> liste = new KnipexQuelle(LEER).parse(beispiel("knipex.json"));

        assertThat(liste).hasSize(3);
        Stellenanzeige a = liste.get(0);
        assertThat(a.getFirma()).isEqualTo("KNIPEX-Werk C. Gustav Putsch KG");
        assertThat(a.getTitel()).isEqualTo("Initiativbewerbung Studierende und Abschlussarbeiten (all genders)");
        assertThat(a.getOrt()).isEqualTo("Wuppertal");
        assertThat(a.getPlz()).isEqualTo("42349");
        assertThat(a.getRefnr()).isEqualTo("46336169");
        assertThat(a.getLink()).startsWith("https://karriere.knipex.de/jobs/46336169/");
        assertThat(a.getVeroeffentlicht()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(a.getEntfernungKm()).isNull();

        String alsArray = "{\"dataFeedElement\":[{\"item\":{\"title\":\"T\",\"jobLocation\":[{\"address\":{\"addressLocality\":\"Cronenberg\"}}]}}]}";
        assertThat(new KnipexQuelle(LEER).parse(alsArray).get(0).getOrt()).isEqualTo("Cronenberg");
    }

    @Test
    @DisplayName("Schmersal: onlyfy-Liste mit Firmenname aus der Wurzel")
    void schmersal() throws Exception {
        List<Stellenanzeige> liste = new SchmersalQuelle(LEER).parse(beispiel("schmersal.json"));

        assertThat(liste).hasSize(3);
        Stellenanzeige a = liste.get(0);
        assertThat(a.getFirma()).isEqualTo("K.A. Schmersal GmbH & Co. KG");
        assertThat(a.getTitel()).isEqualTo("Ausbildung Elektroniker für Betriebstechnik 2027 (m/w/d)");
        assertThat(a.getOrt()).isEqualTo("Wuppertal");
        assertThat(a.getRefnr()).isEqualTo("7u269rm8");
        assertThat(a.getLink()).isEqualTo("https://schmersal.onlyfy.jobs/job/5yikbjt2qe4nv0z6bpuat1252mzvkj");
        assertThat(a.getVeroeffentlicht()).isEqualTo(LocalDate.of(2026, 6, 19));
        assertThat(a.getEntfernungKm()).isNull();
    }

    @Test
    @DisplayName("Erfurt & Sohn: Talention-Antwort mit deutschem Datum")
    void erfurt() throws Exception {
        List<Stellenanzeige> liste = new ErfurtQuelle(LEER).parse(beispiel("erfurt.json"));

        assertThat(liste).hasSize(7);
        Stellenanzeige a = liste.get(0);
        assertThat(a.getFirma()).isEqualTo("Erfurt & Sohn");
        assertThat(a.getTitel()).isEqualTo("Gebietsverkaufsleiter / Außendienst im Raum Hessen (w/m/d)");
        assertThat(a.getOrt()).isEqualTo("Stockelsdorf");
        assertThat(a.getRefnr()).isEqualTo("391420787");
        assertThat(a.getLink()).startsWith("https://jobs.erfurt.com/talention/stellenangebote/");
        assertThat(a.getVeroeffentlicht()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(a.getEntfernungKm()).isNull();
    }

    @Test
    @DisplayName("bilstein group: Guidecom-Felder, HTML statt JSON ist ein Fehler")
    void bilstein() throws Exception {
        List<Stellenanzeige> liste = new BilsteinQuelle(LEER).parse(beispiel("bilstein.json"));

        assertThat(liste).hasSize(3);
        Stellenanzeige a = liste.get(0);
        assertThat(a.getFirma()).isEqualTo("Ferdinand Bilstein GmbH + Co. KG");
        assertThat(a.getTitel()).isEqualTo("Mitarbeiter Anlagenbetreuung/Störungsbehebung Logistik (m/w/d) Quereinsteiger");
        assertThat(a.getOrt()).isEqualTo("Gelsenkirchen");
        assertThat(a.getRefnr()).isEqualTo("2026-069");
        assertThat(a.getLink()).isEqualTo("https://bilsteingroup.com/jobs/stellenangebot/job/Offer/show/0/guide/2026-069/");
        assertThat(a.getVeroeffentlicht()).isEqualTo(LocalDate.of(2026, 6, 11));
        assertThat(a.getEntfernungKm()).isNull();

        assertThatThrownBy(() -> new BilsteinQuelle(LEER).parse("<!DOCTYPE html><html></html>"))
                .hasMessageContaining("Accept");
    }

    @Test
    @DisplayName("Aptiv: Workday-Seite, Link aus link-basis, relatives Datum")
    void aptiv() throws Exception {
        List<Stellenanzeige> liste = new AptivQuelle(aptivKonfig()).parse(beispiel("aptiv.json"));

        assertThat(liste).hasSize(3);
        Stellenanzeige a = liste.get(0);
        assertThat(a.getFirma()).isEqualTo("Aptiv");
        assertThat(a.getTitel()).isEqualTo("Regional Chief of Staff – EMEA (f/m/d)");
        // locationsText taugt nicht ("2 Standorte"), gefiltert wird ueber das Standort-Facet.
        assertThat(a.getOrt()).isEqualTo("Wuppertal");
        assertThat(a.getRefnr()).isEqualTo("J000704207");
        assertThat(a.getLink()).isEqualTo("https://aptiv.wd5.myworkdayjobs.com/de-DE/APTIV_CAREERS"
                + "/job/Wuppertal-Germany/Regional-Chief-of-Staff---EMEA--f-m-d-_J000704207");
        assertThat(a.getVeroeffentlicht()).isEqualTo(LocalDate.now());
        assertThat(a.getEntfernungKm()).isNull();

        assertThat(liste.get(1).getVeroeffentlicht()).isEqualTo(LocalDate.now().minusDays(4));
        assertThat(liste.get(2).getVeroeffentlicht()).isEqualTo(LocalDate.now().minusDays(30));
    }

    @Test
    @DisplayName("Aptiv: postedOn ist Text - unbekannte Formulierung bleibt leer")
    void aptivRelativesDatum() {
        AptivQuelle q = new AptivQuelle(aptivKonfig());

        assertThat(q.relativesDatum("Heute ausgeschrieben")).isEqualTo(LocalDate.now());
        assertThat(q.relativesDatum("Posted Today")).isEqualTo(LocalDate.now());
        assertThat(q.relativesDatum("Vor 3 Tagen ausgeschrieben")).isEqualTo(LocalDate.now().minusDays(3));
        assertThat(q.relativesDatum("Posted 3 Days Ago")).isEqualTo(LocalDate.now().minusDays(3));
        assertThat(q.relativesDatum("Vor mehr als 30 Tagen ausgeschrieben")).isEqualTo(LocalDate.now().minusDays(30));
        assertThat(q.relativesDatum("Posted 30+ Days Ago")).isEqualTo(LocalDate.now().minusDays(30));
        assertThat(q.relativesDatum("Demnächst")).isNull();
        assertThat(q.relativesDatum(null)).isNull();
    }

    @Test
    @DisplayName("codecentric: Personio-XML, Ort kommt aus den erlaubten Orten")
    void codecentric() throws Exception {
        List<Stellenanzeige> liste =
                new CodecentricQuelle(codecentricKonfig(), orte()).parse(beispiel("codecentric.xml"));

        assertThat(liste).hasSize(3);
        Stellenanzeige a = liste.get(0);
        assertThat(a.getFirma()).isEqualTo("codecentric");
        assertThat(a.getTitel()).isEqualTo("AI Software Engineer and Consultant (w/d/m)");
        assertThat(a.getRefnr()).isEqualTo("2717132");
        assertThat(a.getLink()).isEqualTo("https://codecentric.jobs.personio.de/job/2717132?language=de");
        assertThat(a.getVeroeffentlicht()).isEqualTo(LocalDate.of(2026, 7, 17));
        assertThat(a.getEntfernungKm()).isNull();

        // office ist "Hybrid", Solingen steht nur unter additionalOffices.
        assertThat(a.getOrt()).isEqualTo("Solingen");
        // Diese Stelle hat Solingen direkt im office-Feld.
        assertThat(liste.get(1).getOrt()).isEqualTo("Solingen");
        // office Muenchen, Solingen unter additionalOffices - der erlaubte Ort gewinnt.
        assertThat(liste.get(2).getOrt()).isEqualTo("Solingen");
    }

    @Test
    @DisplayName("codecentric: ohne passenden Ort bleibt der Hybrid-Eintrag stehen")
    void codecentricOhneErlaubtenOrt() throws Exception {
        List<Stellenanzeige> liste =
                new CodecentricQuelle(codecentricKonfig(), new SuchProperties()).parse(beispiel("codecentric.xml"));

        assertThat(liste.get(0).getOrt()).isEqualTo("Hybrid");
    }

    @Test
    @DisplayName("Quelle ohne Eintrag in application.yml gilt als inaktiv")
    void ohneKonfigurationInaktiv() {
        assertThat(new SchmersalQuelle(LEER).aktiv()).isFalse();

        QuellenProperties props = new QuellenProperties();
        QuellenProperties.Quelle q = new QuellenProperties.Quelle();
        q.setUrl("https://example.org");
        props.getQuellen().put("schmersal", q);
        assertThat(new SchmersalQuelle(props).aktiv()).isTrue();
        q.setAktiv(false);
        assertThat(new SchmersalQuelle(props).aktiv()).isFalse();
    }

    @Test
    @DisplayName("Beispieldaten enthalten keine echten Ansprechpartner")
    void beispieldatenSindAnonymisiert() throws Exception {
        Pattern mail = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+[.][a-z]{2,}");
        // Rollenadressen der Arbeitgeber und Bewerbungs-Aliase des Portals duerfen bleiben.
        Pattern erlaubt = Pattern.compile("^(.*@example[.]com|support@.*|jobs@.*|application[+]job[+].*@mail[.]onlyfy[.]jobs)$");
        List<String> verdaechtig = new ArrayList<>();
        for (String datei : List.of("interamt-1714.json", "wupperverband.json", "gothaer.json", "riedel.json",
                "knipex.json", "schmersal.json", "erfurt.json", "bilstein.json",
                "aptiv.json", "codecentric.xml")) {
            String inhalt = beispiel(datei);
            Matcher m = mail.matcher(inhalt);
            while (m.find()) {
                if (!erlaubt.matcher(m.group()).matches()) {
                    verdaechtig.add(datei + ": " + m.group());
                }
            }
            // Fotos von Personen aus dem Bewerbermanagement haben hier nichts verloren.
            assertThat(inhalt).as(datei).doesNotContain("/users/").doesNotContain("/photo/");
        }
        assertThat(verdaechtig).as("personenbezogene Mailadressen").isEmpty();

        // Die erfundenen Personen stehen wirklich drin - sonst hat jemand die Datei neu gezogen.
        assertThat(beispiel("bilstein.json")).contains("max.beispiel@example.com").contains("\"nachname\": \"Muster\"");
        assertThat(beispiel("wupperverband.json")).contains("Tim").contains("Testmann").contains("lena.probe@example.com");
        assertThat(beispiel("gothaer.json")).contains("Nina Platzhalter").contains("paula.attrappe@example.com");
        assertThat(beispiel("schmersal.json")).contains("Jonas Fiktiv").contains("mia.dummy@example.com");
        assertThat(beispiel("codecentric.xml")).contains("Maria Beispiel").contains("erika.muster@example.com");
    }
}
