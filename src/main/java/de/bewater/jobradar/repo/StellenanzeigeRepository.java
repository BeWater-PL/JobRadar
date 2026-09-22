package de.bewater.jobradar.repo;

import de.bewater.jobradar.domain.Stellenanzeige;
import de.bewater.jobradar.domain.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StellenanzeigeRepository extends JpaRepository<Stellenanzeige, String> {

    List<Stellenanzeige> findByStatusOrderByPunkteDescVeroeffentlichtDesc(Status status);

    List<Stellenanzeige> findAllByOrderByZuerstGesehenDesc();
}
