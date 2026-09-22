package de.bewater.jobradar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "stellenanzeige")
public class Stellenanzeige {

    /**
     * Fingerabdruck aus Firma + Titel + Ort. Bewusst NICHT die Referenznummer
     * der Arbeitsagentur: dieselbe Stelle wird oft mit neuer Nummer neu
     * eingestellt, und genau diese Wiederholungen sollen wegfallen.
     */
    @Id
    @Column(length = 200)
    private String fingerabdruck;

    private String firma;
    private String titel;
    private String ort;
    private String plz;
    private Integer entfernungKm;

    @Column(length = 500)
    private String link;

    private String quelle;
    private String refnr;

    private LocalDate veroeffentlicht;
    private LocalDateTime zuerstGesehen;

    /**
     * Quelle weist die Stelle ausdruecklich als Remote/Homeoffice aus.
     * Mit Default, weil SQLite einer bestehenden Tabelle keine NOT-NULL-Spalte
     * ohne Default anhaengen kann - sonst scheitert das Schema-Update.
     */
    @Column(columnDefinition = "boolean default false")
    private Boolean remote = false;

    /**
     * Ohne columnDefinition wuerde Hibernate eine CHECK-Constraint mit den
     * Enum-Werten anlegen. SQLite kann die spaeter nicht aendern - ein neuer
     * Status-Wert wuerde dann an der alten Datenbank scheitern.
     */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private Status status = Status.NEU;

    /** Passungspunkte aus den Filterregeln, hoeher ist besser. */
    private int punkte;

    /** Kurze Begruendung, warum die Anzeige diese Punktzahl hat. */
    @Column(length = 500)
    private String begruendung;

    protected Stellenanzeige() {
    }

    public Stellenanzeige(String firma, String titel, String ort) {
        this.firma = firma;
        this.titel = titel;
        this.ort = ort;
        this.fingerabdruck = bildeFingerabdruck(firma, titel, ort);
        this.zuerstGesehen = LocalDateTime.now();
    }

    /**
     * Normalisiert aggressiv: Gross-/Kleinschreibung, Geschlechterkuerzel wie
     * (m/w/d), Rechtsformen und Sonderzeichen fliegen raus. Damit gilt
     * "Junior Java-Entwickler (m/w/d)" bei der "Muster GmbH & Co. KG" als
     * dieselbe Stelle wie "junior java entwickler" bei "Muster".
     */
    public static String bildeFingerabdruck(String firma, String titel, String ort) {
        return normalisiere(firma) + "|" + normalisiere(titel) + "|" + normalisiere(ort);
    }

    private static String normalisiere(String text) {
        if (text == null) {
            return "";
        }
        String s = text.toLowerCase(Locale.GERMAN)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        s = s.replaceAll("\\(\\s*[mwdfx/\\s,.*:-]{3,}\\s*\\)", " ");
        s = s.replaceAll("\\b(m|w|d|f|x)\\s*/\\s*(m|w|d|f|x)(\\s*/\\s*(m|w|d|f|x))?\\b", " ");
        s = s.replaceAll("\\b(gmbh|mbh|co|kg|ag|se|ohg|gbr|kgaa|ug|e\\.?v|kdoer|und)\\b", " ");
        s = s.replaceAll("[^a-z0-9]+", " ");
        return s.trim().replaceAll("\\s+", " ");
    }

    public String getFingerabdruck() { return fingerabdruck; }
    public String getFirma() { return firma; }
    public String getTitel() { return titel; }
    public String getOrt() { return ort; }
    public String getPlz() { return plz; }
    public void setPlz(String plz) { this.plz = plz; }
    public Integer getEntfernungKm() { return entfernungKm; }
    public void setEntfernungKm(Integer entfernungKm) { this.entfernungKm = entfernungKm; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public String getQuelle() { return quelle; }
    public void setQuelle(String quelle) { this.quelle = quelle; }
    public String getRefnr() { return refnr; }
    public void setRefnr(String refnr) { this.refnr = refnr; }
    public LocalDate getVeroeffentlicht() { return veroeffentlicht; }
    public void setVeroeffentlicht(LocalDate veroeffentlicht) { this.veroeffentlicht = veroeffentlicht; }
    public LocalDateTime getZuerstGesehen() { return zuerstGesehen; }
    public boolean isRemote() { return Boolean.TRUE.equals(remote); }
    public void setRemote(boolean remote) { this.remote = remote; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getPunkte() { return punkte; }
    public void setPunkte(int punkte) { this.punkte = punkte; }
    public String getBegruendung() { return begruendung; }
    public void setBegruendung(String begruendung) { this.begruendung = begruendung; }
}
