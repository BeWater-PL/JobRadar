package de.bewater.jobradar;

import de.bewater.jobradar.config.SuchProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import de.bewater.jobradar.filter.Einsteigerfilter;
import de.bewater.jobradar.quelle.Jobquelle;
import de.bewater.jobradar.repo.StellenanzeigeRepository;
import de.bewater.jobradar.service.SuchlaufService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Der Suchlauf muss auch dann durchlaufen, wenn einzelne Quellen sterben. */
class SuchlaufServiceTest {

    private static Jobquelle quelle(String name, boolean aktiv, Stellenanzeige... treffer) {
        return quelle(name, aktiv, true, treffer);
    }

    private static Jobquelle quelle(String name, boolean aktiv, boolean altersfilter, Stellenanzeige... treffer) {
        return new Jobquelle() {
            @Override public String name() { return name; }
            @Override public boolean aktiv() { return aktiv; }
            @Override public boolean altersfilterAnwenden() { return altersfilter; }
            @Override public List<Stellenanzeige> lade() {
                for (Stellenanzeige a : treffer) {
                    a.setQuelle(name);
                }
                return List.of(treffer);
            }
        };
    }

    private static Stellenanzeige alt(String firma, String titel) {
        Stellenanzeige a = new Stellenanzeige(firma, titel, "Wuppertal");
        a.setVeroeffentlicht(LocalDate.now().minusDays(60));
        return a;
    }

    private static Jobquelle kaputt(String name) {
        return new Jobquelle() {
            @Override public String name() { return name; }
            @Override public List<Stellenanzeige> lade() { throw new IllegalStateException("Feed antwortet 500"); }
        };
    }

    private static Stellenanzeige frisch(String firma, String titel) {
        Stellenanzeige a = new Stellenanzeige(firma, titel, "Wuppertal");
        a.setVeroeffentlicht(LocalDate.now());
        return a;
    }

    @Test
    @DisplayName("Eine werfende Quelle bricht den Lauf nicht ab, die anderen werden gespeichert")
    void fehlerEinerQuelleStopptNicht() {
        SuchProperties props = new SuchProperties();
        StellenanzeigeRepository repo = mock(StellenanzeigeRepository.class);
        when(repo.existsById(anyString())).thenReturn(false);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SuchlaufService service = new SuchlaufService(List.of(
                quelle("Erste", true, frisch("Stadt Wuppertal", "Entwickler")),
                kaputt("Kaputte"),
                quelle("Dritte", true, frisch("Wupperverband", "Java Entwickler"))),
                new Einsteigerfilter(props), repo, props);

        List<Stellenanzeige> neu = service.lauf();

        assertThat(neu).extracting(Stellenanzeige::getFirma)
                .containsExactlyInAnyOrder("Stadt Wuppertal", "Wupperverband");
        assertThat(service.getLetzterLauf()).contains("2 neu").contains("2 ok, 1 fehlgeschlagen");
    }

    @Test
    @DisplayName("Deaktivierte Quellen werden nicht abgefragt")
    void deaktivierteQuelleWirdUebersprungen() {
        SuchProperties props = new SuchProperties();
        StellenanzeigeRepository repo = mock(StellenanzeigeRepository.class);

        SuchlaufService service = new SuchlaufService(List.of(
                quelle("Aus", false, frisch("Firma", "Titel"))),
                new Einsteigerfilter(props), repo, props);

        assertThat(service.lauf()).isEmpty();
        verify(repo, never()).save(any());
        assertThat(service.getLetzterLauf()).contains("0 ok, 0 fehlgeschlagen");
    }

    @Test
    @DisplayName("Dieselbe Stelle aus zwei Quellen wird nur einmal gespeichert")
    void gleicheStelleAusZweiQuellen() {
        SuchProperties props = new SuchProperties();
        StellenanzeigeRepository repo = mock(StellenanzeigeRepository.class);
        when(repo.existsById(anyString())).thenReturn(false);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SuchlaufService service = new SuchlaufService(List.of(
                quelle("Arbeitsagentur", true, frisch("Stadt Wuppertal", "Anwendungsentwickler (m/w/d)")),
                quelle("Interamt", true, frisch("Stadt Wuppertal", "Anwendungsentwickler"))),
                new Einsteigerfilter(props), repo, props);

        service.lauf();

        ArgumentCaptor<Stellenanzeige> gespeichert = ArgumentCaptor.forClass(Stellenanzeige.class);
        verify(repo).save(gespeichert.capture());
        assertThat(gespeichert.getAllValues()).hasSize(1);
    }

    @Test
    @DisplayName("Ohne Altersfilter kommt eine alte Anzeige durch, mit Altersfilter nicht")
    void altersfilterProQuelle() {
        SuchProperties props = new SuchProperties(); // max-alter-tage = 2
        StellenanzeigeRepository repo = mock(StellenanzeigeRepository.class);
        when(repo.existsById(anyString())).thenReturn(false);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SuchlaufService service = new SuchlaufService(List.of(
                quelle("Interamt", true, false, alt("Stadt Wuppertal", "Anwendungsentwickler")),
                quelle("Arbeitsagentur", true, true, alt("Alte GmbH", "Softwareentwickler"))),
                new Einsteigerfilter(props), repo, props);

        List<Stellenanzeige> neu = service.lauf();

        assertThat(neu).extracting(Stellenanzeige::getFirma).containsExactly("Stadt Wuppertal");
        verify(repo, never()).save(org.mockito.ArgumentMatchers.argThat(a -> "Alte GmbH".equals(a.getFirma())));
    }
}
