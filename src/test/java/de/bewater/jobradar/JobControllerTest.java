package de.bewater.jobradar;

import de.bewater.jobradar.domain.Status;
import de.bewater.jobradar.domain.Stellenanzeige;
import de.bewater.jobradar.repo.StellenanzeigeRepository;
import de.bewater.jobradar.service.SuchlaufService;
import de.bewater.jobradar.web.JobController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobControllerTest {

    private final StellenanzeigeRepository repo = mock(StellenanzeigeRepository.class);
    private final SuchlaufService suchlauf = mock(SuchlaufService.class);
    private final JobController controller = new JobController(repo, suchlauf);

    @Test
    @DisplayName("Absage setzt den Status auf ABGELEHNT")
    void absageSetztStatus() {
        Stellenanzeige a = new Stellenanzeige("Muster GmbH", "Softwareentwickler", "Wuppertal");
        a.setStatus(Status.BEWORBEN);
        when(repo.findById(a.getFingerabdruck())).thenReturn(Optional.of(a));

        String ziel = controller.statusSetzen(a.getFingerabdruck(), Status.ABGELEHNT);

        assertThat(ziel).isEqualTo("redirect:/");
        assertThat(a.getStatus()).isEqualTo(Status.ABGELEHNT);
        verify(repo).save(a);
    }

    @Test
    @DisplayName("Uebersicht liefert den Abschnitt Abgelehnt getrennt von Neu")
    void uebersichtHatAbgelehnt() {
        Stellenanzeige abgelehnt = new Stellenanzeige("Muster GmbH", "Softwareentwickler", "Wuppertal");
        abgelehnt.setStatus(Status.ABGELEHNT);
        when(repo.findByStatusOrderByPunkteDescVeroeffentlichtDesc(Status.ABGELEHNT)).thenReturn(List.of(abgelehnt));
        when(repo.findByStatusOrderByPunkteDescVeroeffentlichtDesc(Status.NEU)).thenReturn(List.of());
        when(suchlauf.getLetzterLauf()).thenReturn("test");

        ExtendedModelMap model = new ExtendedModelMap();
        String sicht = controller.uebersicht(model);

        assertThat(sicht).isEqualTo("index");
        assertThat(model.get("abgelehnt")).isEqualTo(List.of(abgelehnt));
        assertThat(model.get("neu")).isEqualTo(List.of());
    }
}
