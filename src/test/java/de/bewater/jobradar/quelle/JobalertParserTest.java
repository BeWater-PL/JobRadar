package de.bewater.jobradar.quelle;

import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parser gegen gespeicherte Alert-Mails unter src/test/resources/mails - je
 * Portal eine .eml, nachgebaut nach dem Aufbau der echten Mails (Titel als
 * Link, darunter Firma und Ort, dazu Buttons, Gehalt, Datum als Rauschen).
 */
class JobalertParserTest {

    private final JobalertParser parser = new JobalertParser();

    private static MimeMessage mail(String datei) throws Exception {
        try (InputStream in = JobalertParserTest.class.getResourceAsStream("/mails/" + datei)) {
            assertThat(in).as("Beispielmail " + datei).isNotNull();
            return new MimeMessage(Session.getInstance(new Properties()), in);
        }
    }

    @Test
    @DisplayName("Indeed: fuenf Jobs, Titel-Link und Bewerben-Button zaehlen als eine Anzeige")
    void indeed() throws Exception {
        MimeMessage m = mail("indeed.eml");
        assertThat(JobalertParser.portal(m).name()).isEqualTo("Indeed");

        List<Stellenanzeige> liste = parser.parse(m);

        assertThat(liste).hasSize(5);
        Stellenanzeige a = liste.get(0);
        assertThat(a.getTitel()).isEqualTo("Junior Softwareentwickler Java (m/w/d)");
        assertThat(a.getFirma()).isEqualTo("Muster Software GmbH");
        assertThat(a.getOrt()).isEqualTo("Wuppertal");
        assertThat(a.getLink()).isEqualTo("https://de.indeed.com/viewjob?jk=7a1f3c9e2b4d5e60");
        assertThat(a.getRefnr()).isEqualTo("Indeed:7a1f3c9e2b4d5e60");
        assertThat(a.getQuelle()).isEqualTo("Job-Alert Indeed");
        assertThat(a.getVeroeffentlicht()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(a.getEntfernungKm()).isNull();

        Stellenanzeige b = liste.get(1);
        assertThat(b.getTitel()).isEqualTo("Fachinformatiker Anwendungsentwicklung (m/w/d)");
        assertThat(b.getFirma()).isEqualTo("Bergische Datentechnik AG");
        assertThat(b.getOrt()).isEqualTo("42103 Wuppertal");
        assertThat(b.getPlz()).isEqualTo("42103");

        assertThat(liste.get(2).getOrt()).isEqualTo("Solingen (42651)");
        assertThat(liste.get(2).getPlz()).isEqualTo("42651");
        assertThat(liste.get(3).getFirma()).isEqualTo("Nordlicht Systems GmbH & Co. KG");
        assertThat(liste.get(3).getOrt()).isEqualTo("Hamburg");
        assertThat(liste.get(4).getTitel()).isEqualTo("Industriemechaniker (m/w/d)");
    }

    @Test
    @DisplayName("StepStone: quoted-printable, Job-ID aus dem Link, Button-Link nicht doppelt")
    void stepstone() throws Exception {
        List<Stellenanzeige> liste = parser.parse(mail("stepstone.eml"));

        assertThat(liste).hasSize(3);
        Stellenanzeige a = liste.get(0);
        assertThat(a.getTitel()).isEqualTo("Softwareentwickler Java (m/w/d)");
        assertThat(a.getFirma()).isEqualTo("Bergische IT-Werke GmbH");
        assertThat(a.getOrt()).isEqualTo("Wuppertal");
        assertThat(a.getLink()).isEqualTo("https://www.stepstone.de/stellenangebote--Softwareentwickler-Java-m-w-d-Wuppertal-Bergische-IT-Werke-GmbH--11223344-inline.html");
        assertThat(a.getRefnr()).isEqualTo("StepStone:11223344");
        assertThat(a.getQuelle()).isEqualTo("Job-Alert StepStone");

        assertThat(liste.get(1).getTitel()).isEqualTo("Junior Fullstack Developer (w/m/d)");
        assertThat(liste.get(1).getFirma()).isEqualTo("Klingenstadt Digital GmbH");
        assertThat(liste.get(1).getOrt()).isEqualTo("Solingen");
        assertThat(liste.get(2).getFirma()).isEqualTo("Rechenwerk AG");
    }

    @Test
    @DisplayName("kimeta: Listen-Layout mit br-Umbruechen")
    void kimeta() throws Exception {
        List<Stellenanzeige> liste = parser.parse(mail("kimeta.eml"));

        assertThat(liste).hasSize(2);
        assertThat(liste.get(0).getTitel()).isEqualTo("Anwendungsentwickler (m/w/d)");
        assertThat(liste.get(0).getFirma()).isEqualTo("Talwerk Software GmbH");
        assertThat(liste.get(0).getOrt()).isEqualTo("Wuppertal");
        assertThat(liste.get(0).getLink()).isEqualTo("https://www.kimeta.de/stellenangebote/anwendungsentwickler-m-w-d-wuppertal-3927481");
        assertThat(liste.get(0).getRefnr()).isEqualTo("kimeta:3927481");
        assertThat(liste.get(1).getFirma()).isEqualTo("Velberter Maschinenbau AG");
        assertThat(liste.get(1).getOrt()).isEqualTo("Velbert (42549)");
        assertThat(liste.get(1).getVeroeffentlicht()).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    @DisplayName("meinestadt: Logo-Link zaehlt nicht doppelt, Ort notfalls aus dem Pfad")
    void meinestadt() throws Exception {
        List<Stellenanzeige> liste = parser.parse(mail("meinestadt.eml"));

        assertThat(liste).hasSize(3);
        assertThat(liste.get(0).getTitel()).isEqualTo("Softwareentwickler (m/w/d)");
        assertThat(liste.get(0).getFirma()).isEqualTo("Wupper Apps GmbH");
        assertThat(liste.get(0).getOrt()).isEqualTo("Wuppertal");
        assertThat(liste.get(0).getLink()).isEqualTo("https://jobs.meinestadt.de/wuppertal/standard?id=284719355");
        assertThat(liste.get(0).getRefnr()).isEqualTo("meinestadt:284719355");
        assertThat(liste.get(1).getFirma()).isEqualTo("Stadtsparkasse Wuppertal");
        // Dritter Eintrag hat keine Ortszeile - Stadt kommt aus dem Link-Pfad.
        assertThat(liste.get(2).getFirma()).isEqualTo("Agentur Nordbahntrasse");
        assertThat(liste.get(2).getOrt()).isEqualTo("Wuppertal");
    }

    @Test
    @DisplayName("Mails anderer Absender werden ignoriert")
    void fremdeMail() throws Exception {
        MimeMessage m = new MimeMessage(Session.getInstance(new Properties()));
        m.setFrom(new InternetAddress("newsletter@example.com"));
        m.setSubject("Angebote der Woche");
        m.setContent("<html><body><a href=\"https://de.indeed.com/viewjob?jk=abc\">Job</a></body></html>", "text/html; charset=UTF-8");
        m.saveChanges();

        assertThat(JobalertParser.portal(m)).isNull();
        assertThat(parser.parse(m)).isEmpty();
    }

    @Test
    @DisplayName("Ohne Umgebungsvariablen ist die Mail-Quelle inaktiv, ohne Host ebenfalls")
    void ohneZugangsdatenInaktiv() {
        QuellenProperties props = new QuellenProperties();
        QuellenProperties.Quelle q = new QuellenProperties.Quelle();
        q.setHost("imap.example.net");
        props.getQuellen().put("mail", q);
        boolean zugangsdatenGesetzt = System.getenv(JobalertQuelle.ENV_USER) != null
                && System.getenv(JobalertQuelle.ENV_PASSWORT) != null;

        assertThat(new JobalertQuelle(props).aktiv()).isEqualTo(zugangsdatenGesetzt);

        q.setHost(null);
        assertThat(new JobalertQuelle(props).aktiv()).isFalse();
        assertThat(new JobalertQuelle(new QuellenProperties()).aktiv()).isFalse();
    }

    @Test
    @DisplayName("Link-Bereinigung: Indeed auf jk reduziert, sonst Query weg")
    void linkBereinigung() {
        assertThat(JobalertParser.bereinigeLink("https://de.indeed.com/rc/clk/dl?jk=abc123&from=ja&tk=x"))
                .isEqualTo("https://de.indeed.com/viewjob?jk=abc123");
        assertThat(JobalertParser.bereinigeLink("https://www.kimeta.de/stellenangebote/x-123?utm=1"))
                .isEqualTo("https://www.kimeta.de/stellenangebote/x-123");
    }
}
