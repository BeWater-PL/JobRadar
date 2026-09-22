package de.bewater.jobradar.web;

import de.bewater.jobradar.domain.Stellenanzeige;
import de.bewater.jobradar.domain.Status;
import de.bewater.jobradar.repo.StellenanzeigeRepository;
import de.bewater.jobradar.service.SuchlaufService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class JobController {

    private final StellenanzeigeRepository repo;
    private final SuchlaufService suchlauf;

    public JobController(StellenanzeigeRepository repo, SuchlaufService suchlauf) {
        this.repo = repo;
        this.suchlauf = suchlauf;
    }

    /**
     * Kennung fuer den zweiten Doppelklick: Daran erkennt eine frisch gestartete
     * .exe, dass auf dem Port wirklich JobRadar sitzt und kein fremdes Programm.
     * Gleicher Pfad wie das POST weiter unten, aber anderes Verb - das POST
     * setzt den Status einer Anzeige, dieses GET beantwortet nur "wer da?".
     */
    @GetMapping("/status")
    @ResponseBody
    public String kennung() {
        return "jobradar";
    }

    @GetMapping("/")
    public String uebersicht(Model model) {
        List<Stellenanzeige> neu = repo.findByStatusOrderByPunkteDescVeroeffentlichtDesc(Status.NEU);
        List<Stellenanzeige> vorgemerkt = repo.findByStatusOrderByPunkteDescVeroeffentlichtDesc(Status.VORGEMERKT);
        List<Stellenanzeige> beworben = repo.findByStatusOrderByPunkteDescVeroeffentlichtDesc(Status.BEWORBEN);
        List<Stellenanzeige> abgelehnt = repo.findByStatusOrderByPunkteDescVeroeffentlichtDesc(Status.ABGELEHNT);

        model.addAttribute("neu", neu);
        model.addAttribute("vorgemerkt", vorgemerkt);
        model.addAttribute("beworben", beworben);
        model.addAttribute("abgelehnt", abgelehnt);
        model.addAttribute("letzterLauf", suchlauf.getLetzterLauf());
        return "index";
    }

    @PostMapping("/suchen")
    public String jetztSuchen() {
        suchlauf.lauf();
        return "redirect:/";
    }

    /**
     * ID und Status kommen als Formularfelder, nicht im Pfad: Der Fingerabdruck
     * enthaelt "|" und Leerzeichen, die Tomcat in der URL mit 400 ablehnt.
     */
    @PostMapping("/status")
    public String statusSetzen(@RequestParam String id, @RequestParam Status status) {
        repo.findById(id).ifPresent(anzeige -> {
            anzeige.setStatus(status);
            repo.save(anzeige);
        });
        return "redirect:/";
    }
}
