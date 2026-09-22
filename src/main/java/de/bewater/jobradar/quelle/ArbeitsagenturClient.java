package de.bewater.jobradar.quelle;

import de.bewater.jobradar.config.SuchProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Spricht die offene Jobsuche-Schnittstelle der Bundesagentur fuer Arbeit an.
 * Dokumentation: https://github.com/bundesAPI/jobsuche-api
 */
@Component
public class ArbeitsagenturClient {

    private static final Logger log = LoggerFactory.getLogger(ArbeitsagenturClient.class);
    private static final String QUELLE = "Arbeitsagentur";

    private final RestClient rest;
    private final String apiKey;
    private final String jobdetailBasis;

    public ArbeitsagenturClient(
            @Value("${jobradar.arbeitsagentur.basis-url}") String basisUrl,
            @Value("${jobradar.arbeitsagentur.api-key}") String apiKey,
            @Value("${jobradar.arbeitsagentur.jobdetail-basis}") String jobdetailBasis) {
        this.apiKey = apiKey;
        this.jobdetailBasis = jobdetailBasis;
        this.rest = RestClient.builder().baseUrl(basisUrl).build();
    }

    /**
     * Fragt ein Suchprofil ab. Ein Fehler beendet nie den ganzen Lauf -
     * die uebrigen Profile und Quellen sollen trotzdem durchlaufen.
     */
    public List<Stellenanzeige> suche(SuchProperties.Profil profil, int maxAlterTage) {
        try {
            AaAntwort antwort = rest.get()
                    .uri(uriBauer -> {
                        uriBauer.path("/pc/v6/jobs")
                                .queryParam("was", profil.getWas())
                                .queryParam("umkreis", profil.getUmkreis())
                                .queryParam("angebotsart", 1)
                                .queryParam("zeitarbeit", false)
                                .queryParam("veroeffentlichtseit", maxAlterTage)
                                .queryParam("size", 100)
                                .queryParam("page", 1);
                        if (profil.getWo() != null && !profil.getWo().isBlank()) {
                            uriBauer.queryParam("wo", profil.getWo());
                        }
                        if (profil.getArbeitszeit() != null && !profil.getArbeitszeit().isBlank()) {
                            uriBauer.queryParam("arbeitszeit", profil.getArbeitszeit());
                        }
                        return uriBauer.build();
                    })
                    .header("X-API-Key", apiKey)
                    .retrieve()
                    .body(AaAntwort.class);

            if (antwort == null || antwort.ergebnisliste() == null) {
                log.info("Profil '{}': keine Treffer", profil.getName());
                return List.of();
            }

            List<Stellenanzeige> ergebnis = new ArrayList<>();
            for (AaAntwort.Stellenangebot angebot : antwort.ergebnisliste()) {
                ergebnis.add(uebersetze(angebot));
            }
            log.info("Profil '{}': {} Treffer", profil.getName(), ergebnis.size());
            return ergebnis;

        } catch (Exception e) {
            log.warn("Profil '{}' fehlgeschlagen: {}", profil.getName(), e.getMessage());
            return List.of();
        }
    }

    private Stellenanzeige uebersetze(AaAntwort.Stellenangebot angebot) {
        AaAntwort.Adresse adresse = angebot.adresse();
        String ort = adresse != null ? adresse.ort() : null;
        Stellenanzeige anzeige = new Stellenanzeige(
                angebot.firma(), angebot.anzeigeTitel(), ort);

        anzeige.setQuelle(QUELLE);
        anzeige.setRefnr(angebot.referenznummer());
        anzeige.setVeroeffentlicht(parseDatum(angebot.veroeffentlichtAm()));
        anzeige.setRemote(Boolean.TRUE.equals(angebot.homeofficemoeglich()));

        if (adresse != null) {
            anzeige.setPlz(adresse.plz());
        }
        if (angebot.entfernung() != null) {
            anzeige.setEntfernungKm((int) Math.round(angebot.entfernung()));
        }

        // Die Trefferliste kennt keine Arbeitgeber-URL - der Link fuehrt auf die Jobboerse.
        if (angebot.referenznummer() != null) {
            anzeige.setLink(jobdetailBasis + URLEncoder.encode(angebot.referenznummer(), StandardCharsets.UTF_8));
        }
        return anzeige;
    }

    private LocalDate parseDatum(String roh) {
        if (roh == null || roh.isBlank()) {
            return null;
        }
        try {
            // Kommt als "2026-09-15" oder als vollstaendiger Zeitstempel.
            return LocalDate.parse(roh.substring(0, 10));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            log.debug("Datum nicht lesbar: {}", roh);
            return null;
        }
    }
}
