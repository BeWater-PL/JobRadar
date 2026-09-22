package de.bewater.jobradar.quelle;

import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.FlagTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Liest per IMAP die Job-Alert-Mails von Indeed, StepStone, kimeta und
 * meinestadt aus einem Postfach. Die Portale sperren automatischen Abruf
 * ihrer Websites - ihre Mails duerfen wir aber lesen.
 *
 * Zugangsdaten kommen ausschliesslich aus den Umgebungsvariablen
 * JOBRADAR_MAIL_USER und JOBRADAR_MAIL_PASSWORT; fehlen sie, ist die Quelle
 * inaktiv. Nur ungelesene Mails werden verarbeitet und danach als gelesen
 * markiert - so zaehlt jede Mail genau einmal.
 */
@Component
@Order(90)
public class JobalertQuelle implements Jobquelle {

    public static final String ENV_USER = "JOBRADAR_MAIL_USER";
    public static final String ENV_PASSWORT = "JOBRADAR_MAIL_PASSWORT";

    private static final Logger log = LoggerFactory.getLogger(JobalertQuelle.class);

    private final QuellenProperties.Quelle konfig;
    private final JobalertParser parser = new JobalertParser();

    public JobalertQuelle(QuellenProperties props) {
        this.konfig = props.quelle("mail");
    }

    @Override
    public String name() {
        return "Job-Alert";
    }

    @Override
    public boolean aktiv() {
        if (!konfig.isAktiv() || konfig.getHost() == null || konfig.getHost().isBlank()) {
            return false;
        }
        if (leer(System.getenv(ENV_USER)) || leer(System.getenv(ENV_PASSWORT))) {
            log.info("Job-Alert-Postfach nicht eingerichtet: {} und {} als Umgebungsvariablen setzen",
                    ENV_USER, ENV_PASSWORT);
            return false;
        }
        return true;
    }

    @Override
    public boolean altersfilterAnwenden() {
        return konfig.isAltersfilter();
    }

    @Override
    public List<Stellenanzeige> lade() {
        Properties p = new Properties();
        p.put("mail.store.protocol", "imaps");
        p.put("mail.imaps.host", konfig.getHost());
        p.put("mail.imaps.port", String.valueOf(konfig.getPort()));
        p.put("mail.imaps.ssl.enable", "true");
        p.put("mail.imaps.connectiontimeout", "20000");
        p.put("mail.imaps.timeout", "60000");
        Session session = Session.getInstance(p);

        List<Stellenanzeige> ergebnis = new ArrayList<>();
        int mails = 0;
        int fremd = 0;
        try (Store store = session.getStore("imaps")) {
            store.connect(konfig.getHost(), konfig.getPort(), System.getenv(ENV_USER), System.getenv(ENV_PASSWORT));
            Folder ordner = store.getFolder(konfig.getOrdner());
            ordner.open(Folder.READ_WRITE);
            try {
                Message[] ungelesen = ordner.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                for (Message mail : ungelesen) {
                    if (JobalertParser.portal(mail) == null) {
                        fremd++;
                        continue; // fremde Mails bleiben ungelesen liegen
                    }
                    mails++;
                    try {
                        List<Stellenanzeige> treffer = parser.parse(mail);
                        log.info("Job-Alert '{}': {} Anzeigen", mail.getSubject(), treffer.size());
                        ergebnis.addAll(treffer);
                    } catch (Exception e) {
                        // Eine kaputte Mail soll die anderen nicht aufhalten - trotzdem als gelesen
                        // markieren, sonst scheitert sie bei jedem Lauf erneut.
                        log.warn("Job-Alert-Mail '{}' nicht lesbar: {}", sicherBetreff(mail), e.getMessage());
                    }
                    mail.setFlag(Flags.Flag.SEEN, true);
                }
            } finally {
                ordner.close(false);
            }
        } catch (Exception e) {
            throw new IllegalStateException("IMAP " + konfig.getHost() + ": " + e.getMessage(), e);
        }
        log.info("{}: {} Anzeigen aus {} Mails ({} fremde Mails uebersprungen)", name(), ergebnis.size(), mails, fremd);
        return ergebnis;
    }

    private static String sicherBetreff(Message mail) {
        try {
            return mail.getSubject();
        } catch (Exception e) {
            return "?";
        }
    }

    private static boolean leer(String s) {
        return s == null || s.isBlank();
    }
}
