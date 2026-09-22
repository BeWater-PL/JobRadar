package de.bewater.jobradar;

import de.bewater.jobradar.config.SuchProperties;
import de.bewater.jobradar.domain.Status;
import de.bewater.jobradar.domain.Stellenanzeige;
import de.bewater.jobradar.filter.Einsteigerfilter;
import de.bewater.jobradar.repo.StellenanzeigeRepository;
import de.bewater.jobradar.service.Nachbewertung;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Neue Regeln muessen auch auf die schon gespeicherten NEU-Anzeigen wirken. */
class NachbewertungTest {

    @Test
    @DisplayName("NEU-Eintraege, die durchfallen, werden VERWORFEN; passende bleiben NEU")
    void neuWirdNachbewertet() {
        SuchProperties props = new SuchProperties();
        props.setPflichtWorte(List.of("entwickler"));
        props.setErlaubteOrte(List.of("Wuppertal"));
        StellenanzeigeRepository repo = mock(StellenanzeigeRepository.class);

        Stellenanzeige schlosser = new Stellenanzeige("Muster", "Industriemechaniker", "Wuppertal");
        Stellenanzeige wien = new Stellenanzeige("Riedel", "Software Entwickler", "Wien");
        Stellenanzeige passt = new Stellenanzeige("Muster", "Java Entwickler", "Wuppertal");
        when(repo.findByStatusOrderByPunkteDescVeroeffentlichtDesc(Status.NEU))
                .thenReturn(List.of(schlosser, wien, passt));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Nachbewertung.Ergebnis e = new Nachbewertung(repo, new Einsteigerfilter(props)).nachbewerten();

        assertThat(e.geprueft()).isEqualTo(3);
        assertThat(e.verworfen()).isEqualTo(2);
        assertThat(schlosser.getStatus()).isEqualTo(Status.VERWORFEN);
        assertThat(schlosser.getBegruendung()).startsWith("nachbewertet:");
        assertThat(wien.getStatus()).isEqualTo(Status.VERWORFEN);
        assertThat(passt.getStatus()).isEqualTo(Status.NEU);
    }

    @Test
    @DisplayName("Nur NEU wird angefasst - der Rest wird gar nicht erst geladen")
    void nurNeuWirdGeladen() {
        StellenanzeigeRepository repo = mock(StellenanzeigeRepository.class);
        when(repo.findByStatusOrderByPunkteDescVeroeffentlichtDesc(Status.NEU)).thenReturn(List.of());

        Nachbewertung.Ergebnis e = new Nachbewertung(repo, new Einsteigerfilter(new SuchProperties())).nachbewerten();

        assertThat(e.geprueft()).isZero();
        verify(repo, never()).findByStatusOrderByPunkteDescVeroeffentlichtDesc(Status.VORGEMERKT);
        verify(repo, never()).findByStatusOrderByPunkteDescVeroeffentlichtDesc(Status.BEWORBEN);
        verify(repo, never()).findByStatusOrderByPunkteDescVeroeffentlichtDesc(Status.ABGELEHNT);
        verify(repo, never()).findAll();
        verify(repo, never()).save(any());
    }
}
