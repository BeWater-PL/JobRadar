package de.bewater.jobradar.filter;

import de.bewater.jobradar.config.SuchProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Entscheidet, ob eine Anzeige fuer einen Berufseinsteiger ueberhaupt
 * in Frage kommt, und wie gut sie passt.
 */
@Component
public class Einsteigerfilter {

    private final SuchProperties props;

    public Einsteigerfilter(SuchProperties props) {
        this.props = props;
    }

    public record Urteil(boolean passt, int punkte, String begruendung) {
    }

    public Urteil bewerte(Stellenanzeige anzeige) {
        String text = (anzeige.getTitel() + " " + anzeige.getFirma()).toLowerCase(Locale.GERMAN);

        for (String wort : props.getAusschlussWorte()) {
            if (text.contains(wort.toLowerCase(Locale.GERMAN))) {
                return new Urteil(false, 0, "aussortiert wegen \"" + wort + "\"");
            }
        }

        // Positivbedingung: Arbeitgeber-Feeds liefern auch Schlosser, Vertrieb und
        // Buchhaltung. Ohne ein Entwickler-Wort im Titel ist es nichts fuer uns.
        if (!props.getPflichtWorte().isEmpty()) {
            String titel = String.valueOf(anzeige.getTitel()).toLowerCase(Locale.GERMAN);
            boolean passend = props.getPflichtWorte().stream()
                    .anyMatch(wort -> titel.contains(wort.toLowerCase(Locale.GERMAN)));
            if (!passend) {
                return new Urteil(false, 0, "kein Entwickler-Wort im Titel");
            }
        }

        // Ortsfilter: Arbeitgeber-Feeds bringen Standorte in Wien oder Kanada mit,
        // Entfernung gibt es dort nicht. Ohne Ort lieber behalten als falsch verwerfen.
        if (!props.getErlaubteOrte().isEmpty() && !istRemote(anzeige)) {
            String ort = anzeige.getOrt();
            if (ort != null && !ort.isBlank() && !ortErlaubt(ort)) {
                return new Urteil(false, 0, "Ort \"" + ort.trim() + "\" nicht im Umkreis");
            }
        }

        int punkte = 0;
        List<String> gruende = new ArrayList<>();

        for (String wort : props.getEinsteigerWorte()) {
            if (text.contains(wort.toLowerCase(Locale.GERMAN))) {
                punkte += 30;
                gruende.add(wort);
                break;
            }
        }

        List<String> technik = new ArrayList<>();
        for (String tech : props.getTechnologien()) {
            if (text.contains(tech.toLowerCase(Locale.GERMAN))) {
                punkte += 15;
                technik.add(tech);
            }
        }
        if (!technik.isEmpty()) {
            gruende.add("Technik: " + String.join(", ", technik));
        }

        // Naehe zaehlt: Wuppertal und direkte Nachbarstaedte vor allem anderen.
        Integer km = anzeige.getEntfernungKm();
        if (km != null) {
            if (km <= 7) {
                punkte += 25;
                gruende.add("vor der Haustuer");
            } else if (km <= 20) {
                punkte += 15;
                gruende.add("gut erreichbar");
            } else if (km > 80) {
                punkte -= 10;
            }
        }

        if (istRemote(anzeige)) {
            punkte += 10;
            gruende.add("Remote moeglich");
        }

        String begruendung = gruende.isEmpty() ? "keine Auffaelligkeiten" : String.join("; ", gruende);
        return new Urteil(true, punkte, begruendung);
    }

    /** Remote laut Quelle oder laut Titel ("Remote", "Homeoffice", "Home Office", "Home-Office"). */
    static boolean istRemote(Stellenanzeige anzeige) {
        if (anzeige.isRemote()) {
            return true;
        }
        String titel = String.valueOf(anzeige.getTitel()).toLowerCase(Locale.GERMAN);
        return titel.contains("remote") || titel.replace("-", "").replace(" ", "").contains("homeoffice");
    }

    private boolean ortErlaubt(String ort) {
        String o = ort.toLowerCase(Locale.GERMAN);
        return props.getErlaubteOrte().stream()
                .anyMatch(erlaubt -> o.contains(erlaubt.toLowerCase(Locale.GERMAN)));
    }
}
