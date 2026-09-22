package de.bewater.jobradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Konfiguration der Arbeitgeber-Feeds unter jobradar.quellen.NAME.
 * Jede Quelle hat dieselben Felder; was sie nicht braucht, bleibt leer.
 */
@ConfigurationProperties(prefix = "jobradar")
public class QuellenProperties {

    private Map<String, Quelle> quellen = new LinkedHashMap<>();

    public static class Quelle {
        /** false schaltet die Quelle ab, ohne sie aus der Datei zu loeschen. */
        private boolean aktiv = true;
        /** Endpunkt. Bei Interamt mit {partner} als Platzhalter. */
        private String url;
        /** Zusaetzliche HTTP-Header, z.B. Accept bei bilstein. */
        private Map<String, String> header = new LinkedHashMap<>();
        /** Nur Interamt: eine Abfrage je Partner-ID. */
        private List<Integer> partnerIds = new ArrayList<>();
        /** Nur paginierte Quellen (Gothaer): Seitengroesse. */
        private int limit = 100;
        /** Fester Firmenname, falls der Feed keinen liefert. */
        private String firma;
        /**
         * false, wenn das Datum im Feed kein echtes Veroeffentlichungsdatum ist
         * (Anlagedatum, Dauerbrenner). Dann greift nur der Fingerabdruck.
         */
        private boolean altersfilter = true;
        /** Nur Mail-Quelle: IMAP-Server, Port und Ordner. Zugangsdaten kommen aus Umgebungsvariablen. */
        private String host;
        private int port = 993;
        private String ordner = "INBOX";

        public boolean isAktiv() { return aktiv; }
        public void setAktiv(boolean aktiv) { this.aktiv = aktiv; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public Map<String, String> getHeader() { return header; }
        public void setHeader(Map<String, String> header) { this.header = header; }
        public List<Integer> getPartnerIds() { return partnerIds; }
        public void setPartnerIds(List<Integer> partnerIds) { this.partnerIds = partnerIds; }
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
        public String getFirma() { return firma; }
        public void setFirma(String firma) { this.firma = firma; }
        public boolean isAltersfilter() { return altersfilter; }
        public void setAltersfilter(boolean altersfilter) { this.altersfilter = altersfilter; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getOrdner() { return ordner; }
        public void setOrdner(String ordner) { this.ordner = ordner; }
    }

    /** Liefert die Konfiguration einer Quelle; fehlt der Eintrag, gilt sie als inaktiv. */
    public Quelle quelle(String schluessel) {
        Quelle q = quellen.get(schluessel);
        if (q == null) {
            q = new Quelle();
            q.setAktiv(false);
        }
        return q;
    }

    public Map<String, Quelle> getQuellen() { return quellen; }
    public void setQuellen(Map<String, Quelle> quellen) { this.quellen = quellen; }
}
