package de.bewater.jobradar.service;

import de.bewater.jobradar.config.SuchProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import de.bewater.jobradar.domain.Status;
import de.bewater.jobradar.filter.Einsteigerfilter;
import de.bewater.jobradar.quelle.Jobquelle;
import de.bewater.jobradar.repo.StellenanzeigeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SuchlaufService {

    private static final Logger log = LoggerFactory.getLogger(SuchlaufService.class);

    private final List<Jobquelle> quellen;
    private final Einsteigerfilter filter;
    private final StellenanzeigeRepository repo;
    private final SuchProperties props;

    private volatile String letzterLauf = "noch nicht gelaufen";

    /** Spring reicht hier alle Jobquelle-Beans herein, sortiert nach @Order. */
    public SuchlaufService(List<Jobquelle> quellen, Einsteigerfilter filter,
                           StellenanzeigeRepository repo, SuchProperties props) {
        this.quellen = quellen;
        this.filter = filter;
        this.repo = repo;
        this.props = props;
    }

    /** Einmal beim Start, damit man nach dem Doppelklick sofort etwas sieht. */
    @EventListener(ApplicationReadyEvent.class)
    public void beimStart() {
        lauf();
    }

    /** Zweimal taeglich - oefter bringt nichts, Anzeigen erscheinen nicht im Minutentakt. */
    @Scheduled(cron = "${jobradar.zeitplan:0 0 8,17 * * *}")
    public void geplanterLauf() {
        lauf();
    }

    @Transactional
    public List<Stellenanzeige> lauf() {
        log.info("Suchlauf startet");
        List<Stellenanzeige> gefunden = new ArrayList<>();
        Map<String, Zaehler> proQuelle = new LinkedHashMap<>();
        int quellenOk = 0;
        int quellenFehler = 0;

        for (Jobquelle quelle : quellen) {
            if (!quelle.aktiv()) {
                log.debug("Quelle '{}' ist deaktiviert", quelle.name());
                continue;
            }
            // Jede Quelle einzeln absichern: ein toter Feed darf den Lauf nicht beenden.
            try {
                List<Stellenanzeige> treffer = quelle.lade();
                proQuelle.put(quelle.name(), new Zaehler(treffer.size(), quelle.altersfilterAnwenden()));
                gefunden.addAll(treffer);
                quellenOk++;
            } catch (Exception e) {
                quellenFehler++;
                log.warn("Quelle '{}' fehlgeschlagen: {}", quelle.name(), e.getMessage());
            }
        }

        // Dieselbe Stelle taucht in mehreren Profilen/Quellen auf - innerhalb des Laufs entdoppeln.
        Map<String, Stellenanzeige> einmalig = new LinkedHashMap<>();
        for (Stellenanzeige a : gefunden) {
            einmalig.putIfAbsent(a.getFingerabdruck(), a);
        }

        List<Stellenanzeige> wirklichNeu = new ArrayList<>();
        int schonBekannt = 0;
        int aussortiert = 0;

        for (Stellenanzeige anzeige : einmalig.values()) {
            Zaehler zaehler = zaehlerFuer(proQuelle, anzeige.getQuelle());

            boolean bekannt = repo.existsById(anzeige.getFingerabdruck());
            boolean alt = zaehler.altersfilter && zuAlt(anzeige);
            if (!alt) {
                zaehler.nachAltersfilter++;
            }
            if (bekannt) {
                zaehler.schonBekannt++;
                schonBekannt++;
                continue;
            }
            if (alt) {
                continue;
            }

            Einsteigerfilter.Urteil urteil = filter.bewerte(anzeige);
            if (!urteil.passt()) {
                aussortiert++;
                // Trotzdem speichern: so wird dieselbe Anzeige morgen nicht erneut geprueft.
                anzeige.setStatus(Status.VERWORFEN);
                anzeige.setBegruendung(urteil.begruendung());
                repo.save(anzeige);
                continue;
            }

            anzeige.setPunkte(urteil.punkte());
            anzeige.setBegruendung(urteil.begruendung());
            anzeige.setStatus(Status.NEU);
            repo.save(anzeige);
            zaehler.neu++;
            wirklichNeu.add(anzeige);
        }

        for (Map.Entry<String, Zaehler> e : proQuelle.entrySet()) {
            Zaehler z = e.getValue();
            log.info("Quelle '{}': {} geladen, {} nach Altersfilter{}, {} schon bekannt, {} neu",
                    e.getKey(), z.geladen, z.nachAltersfilter, z.altersfilter ? "" : " (ausgesetzt)",
                    z.schonBekannt, z.neu);
        }
        log.info("Altersfilter: max-alter-tage={}", props.getMaxAlterTage());

        wirklichNeu.sort(Comparator.comparingInt(Stellenanzeige::getPunkte).reversed());

        letzterLauf = String.format("%s - %d neu, %d schon bekannt, %d aussortiert - Quellen: %d ok, %d fehlgeschlagen",
                java.time.LocalDateTime.now().withNano(0), wirklichNeu.size(), schonBekannt, aussortiert,
                quellenOk, quellenFehler);
        log.info("Suchlauf fertig: {}", letzterLauf);
        return wirklichNeu;
    }

    /**
     * Anzeige zur Quelle zuordnen. Die Mail-Quelle haengt das Portal an ihren
     * Namen ("Job-Alert Indeed"), zaehlt aber unter "Job-Alert".
     */
    private static Zaehler zaehlerFuer(Map<String, Zaehler> proQuelle, String quelle) {
        String q = String.valueOf(quelle);
        Zaehler direkt = proQuelle.get(q);
        if (direkt != null) {
            return direkt;
        }
        for (Map.Entry<String, Zaehler> e : proQuelle.entrySet()) {
            if (q.startsWith(e.getKey() + " ")) {
                return e.getValue();
            }
        }
        return proQuelle.computeIfAbsent(q, k -> new Zaehler(0, true));
    }

    /** Zaehlt je Quelle mit; "nach Altersfilter" zaehlt entdoppelte Anzeigen. */
    private static final class Zaehler {
        final int geladen;
        final boolean altersfilter;
        int nachAltersfilter;
        int schonBekannt;
        int neu;

        Zaehler(int geladen, boolean altersfilter) {
            this.geladen = geladen;
            this.altersfilter = altersfilter;
        }
    }

    /**
     * Gilt nur fuer Quellen mit verlaesslichem Veroeffentlichungsdatum. Arbeitgeber-
     * Feeds liefern oft nur ein Anlagedatum oder fuehren Anzeigen monatelang - dort
     * ist der Filter ausgesetzt; der Fingerabdruck verhindert Wiederholungen ohnehin,
     * eine alte Anzeige kommt also hoechstens einmal durch.
     */
    private boolean zuAlt(Stellenanzeige anzeige) {
        LocalDate datum = anzeige.getVeroeffentlicht();
        if (datum == null) {
            return false; // ohne Datum lieber behalten als faelschlich wegwerfen
        }
        return datum.isBefore(LocalDate.now().minusDays(props.getMaxAlterTage()));
    }

    public String getLetzterLauf() {
        return letzterLauf;
    }
}
