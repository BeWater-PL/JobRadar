package de.bewater.jobradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Alles, was der Nutzer einstellen kann, ohne Code anzufassen.
 * Gepflegt wird das in application.yml.
 */
@ConfigurationProperties(prefix = "jobradar")
public class SuchProperties {

    /** Wie viele Tage zurueck eine Anzeige hoechstens alt sein darf. */
    private int maxAlterTage = 2;

    /** Suchprofile, die bei jedem Lauf abgefragt werden. */
    private List<Profil> profile = new ArrayList<>();

    /** Worte, die eine Anzeige sofort aussortieren. */
    private List<String> ausschlussWorte = new ArrayList<>();

    /**
     * Mindestens eines davon muss im Titel stehen, sonst ist es keine
     * Entwicklerstelle. Leere Liste = keine Pflicht (nur fuer Tests sinnvoll).
     */
    private List<String> pflichtWorte = new ArrayList<>();

    /**
     * Orte, aus denen Anzeigen ueberhaupt in Frage kommen. Leere Liste = kein
     * Ortsfilter. Remote-Stellen sind davon ausgenommen.
     */
    private List<String> erlaubteOrte = new ArrayList<>();

    /** Worte, die fuer einen Berufseinsteiger sprechen (Bonuspunkte). */
    private List<String> einsteigerWorte = new ArrayList<>();

    /** Technologien aus dem Lebenslauf (Bonuspunkte bei Treffer). */
    private List<String> technologien = new ArrayList<>();

    public static class Profil {
        /** Freitext Jobtitel, z.B. "Anwendungsentwickler". */
        private String was;
        /** Ort, leer lassen fuer deutschlandweit. */
        private String wo = "";
        /** Radius in km. */
        private int umkreis = 50;
        /** vz, tz, ho (Homeoffice), mj - mehrere mit Semikolon. */
        private String arbeitszeit = "vz";
        /** Sprechender Name fuer die Anzeige in der Oberflaeche. */
        private String name;

        public String getWas() { return was; }
        public void setWas(String was) { this.was = was; }
        public String getWo() { return wo; }
        public void setWo(String wo) { this.wo = wo; }
        public int getUmkreis() { return umkreis; }
        public void setUmkreis(int umkreis) { this.umkreis = umkreis; }
        public String getArbeitszeit() { return arbeitszeit; }
        public void setArbeitszeit(String arbeitszeit) { this.arbeitszeit = arbeitszeit; }
        public String getName() { return name != null ? name : was + " " + wo; }
        public void setName(String name) { this.name = name; }
    }

    public int getMaxAlterTage() { return maxAlterTage; }
    public void setMaxAlterTage(int maxAlterTage) { this.maxAlterTage = maxAlterTage; }
    public List<Profil> getProfile() { return profile; }
    public void setProfile(List<Profil> profile) { this.profile = profile; }
    public List<String> getAusschlussWorte() { return ausschlussWorte; }
    public void setAusschlussWorte(List<String> ausschlussWorte) { this.ausschlussWorte = ausschlussWorte; }
    public List<String> getPflichtWorte() { return pflichtWorte; }
    public void setPflichtWorte(List<String> pflichtWorte) { this.pflichtWorte = pflichtWorte; }
    public List<String> getErlaubteOrte() { return erlaubteOrte; }
    public void setErlaubteOrte(List<String> erlaubteOrte) { this.erlaubteOrte = erlaubteOrte; }
    public List<String> getEinsteigerWorte() { return einsteigerWorte; }
    public void setEinsteigerWorte(List<String> einsteigerWorte) { this.einsteigerWorte = einsteigerWorte; }
    public List<String> getTechnologien() { return technologien; }
    public void setTechnologien(List<String> technologien) { this.technologien = technologien; }
}
