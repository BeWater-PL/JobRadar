package de.bewater.jobradar.service;

import de.bewater.jobradar.domain.Stellenanzeige;
import de.bewater.jobradar.domain.Status;
import de.bewater.jobradar.filter.Einsteigerfilter;
import de.bewater.jobradar.repo.StellenanzeigeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Wendet die aktuellen Filterregeln beim Start auf alles an, was noch als NEU
 * in der Datenbank liegt. Aendern sich Pflichtworte oder erlaubte Orte, muss
 * niemand die alte Liste von Hand ausmisten. Alles, was der Nutzer schon
 * angefasst hat (VORGEMERKT, BEWORBEN, ABGELEHNT), bleibt unangetastet -
 * ebenso bereits VERWORFENES, das kommt nicht zurueck.
 * Order 1: nach der SchemaReparatur, vor dem ersten Suchlauf (ApplicationReady).
 */
@Component
@Order(1)
public class Nachbewertung implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Nachbewertung.class);

    private final StellenanzeigeRepository repo;
    private final Einsteigerfilter filter;

    public Nachbewertung(StellenanzeigeRepository repo, Einsteigerfilter filter) {
        this.repo = repo;
        this.filter = filter;
    }

    @Override
    public void run(ApplicationArguments args) {
        Ergebnis e = nachbewerten();
        if (e.geprueft() > 0) {
            log.info("Nachbewertung: {} NEU geprueft, {} verworfen, {} bleiben", e.geprueft(), e.verworfen(), e.behalten());
        }
    }

    public record Ergebnis(int geprueft, int verworfen, int behalten) {
    }

    @Transactional
    public Ergebnis nachbewerten() {
        List<Stellenanzeige> neue = repo.findByStatusOrderByPunkteDescVeroeffentlichtDesc(Status.NEU);
        int verworfen = 0;
        for (Stellenanzeige anzeige : neue) {
            Einsteigerfilter.Urteil urteil = filter.bewerte(anzeige);
            if (urteil.passt()) {
                // Punkte koennen sich ebenfalls geaendert haben - mitziehen.
                anzeige.setPunkte(urteil.punkte());
                anzeige.setBegruendung(urteil.begruendung());
            } else {
                anzeige.setStatus(Status.VERWORFEN);
                anzeige.setPunkte(0);
                anzeige.setBegruendung("nachbewertet: " + urteil.begruendung());
                verworfen++;
            }
            repo.save(anzeige);
        }
        return new Ergebnis(neue.size(), verworfen, neue.size() - verworfen);
    }
}
