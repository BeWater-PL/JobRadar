package de.bewater.jobradar.quelle;

import de.bewater.jobradar.domain.Stellenanzeige;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holt aus einer Job-Alert-Mail die einzelnen Anzeigen heraus. Kein Netz,
 * keine Session - nur Mail rein, Anzeigen raus, deshalb gut testbar.
 *
 * Alle vier Portale bauen ihre Mails gleich: pro Stelle ein Block mit
 * verlinktem Titel, darunter Firma und Ort als eigene Zeilen. Der Parser
 * sucht die Job-Links, nimmt den kleinsten umschliessenden Block, der nur
 * diesen einen Job enthaelt, und liest Firma und Ort aus den Zeilen darunter.
 * Aendert ein Portal sein Layout, muss nur die Regel des Portals angepasst
 * werden, nicht der Rest.
 */
public class JobalertParser {

    private static final Logger log = LoggerFactory.getLogger(JobalertParser.class);

    /** Zeilen, die in Alert-Mails zwischen Titel, Firma und Ort auftauchen, aber keines davon sind. */
    private static final Pattern RAUSCHEN = Pattern.compile(
            "(?i)^(jetzt bewerben|bewerben|schnellbewerbung|job ansehen|ansehen|anzeigen|mehr|details|neu|top.?job|"
            + "gesponsert|anzeige|premium|empfohlen|heute|gestern|vor \\d+ (tag|tagen|stunde|stunden|minuten?|woche|wochen)"
            + "|(vollzeit|teilzeit|festanstellung|feste anstellung|befristet|unbefristet|homeoffice|remote|hybrid|minijob)([ ,/|]+.*)?"
            + "|\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}"
            + "|.*\\d[\\d.,]*\\s*(€|eur).*|\\d+([.,]\\d+)?\\s*km.*|\\*+)$");

    /** So sieht eine Ortszeile aus: "Wuppertal", "42103 Wuppertal", "Wuppertal (42103)", "Wuppertal, NRW". */
    private static final Pattern ORT = Pattern.compile(
            "^(\\d{5}\\s+)?[A-ZÄÖÜ][\\p{L}.\\-]+(\\s[\\p{L}.\\-]+){0,3}(\\s*\\(\\d{5}\\)|,\\s*[\\p{L} .\\-]+|\\s+\\d{5})?$");

    /** Erkennungsregel fuer ein Portal. */
    public record Portal(String name, Pattern absender, Pattern jobLink) {
    }

    public static final List<Portal> PORTALE = List.of(
            new Portal("Indeed",
                    Pattern.compile("(?i)@(.*\\.)?indeed\\.com$"),
                    Pattern.compile("(?i)indeed\\.com/(rc/clk|pagead/clk|viewjob|m/viewjob)")),
            new Portal("StepStone",
                    Pattern.compile("(?i)@(.*\\.)?stepstone\\.(de|com)$"),
                    Pattern.compile("(?i)stepstone\\.de/stellenangebote--")),
            new Portal("kimeta",
                    Pattern.compile("(?i)@(.*\\.)?kimeta\\.de$"),
                    Pattern.compile("(?i)kimeta\\.de/(stellenangebote|stellenanzeigen|job|jobs)/")),
            new Portal("meinestadt",
                    Pattern.compile("(?i)@(.*\\.)?meinestadt\\.de$"),
                    Pattern.compile("(?i)jobs\\.meinestadt\\.de/[a-z0-9\\-]+/")));

    /** Welches Portal hat die Mail geschickt? null, wenn keins der bekannten. */
    public static Portal portal(Message mail) {
        try {
            Address[] von = mail.getFrom();
            if (von == null) {
                return null;
            }
            for (Address a : von) {
                String adresse = a instanceof InternetAddress ia ? ia.getAddress() : a.toString();
                for (Portal p : PORTALE) {
                    if (p.absender().matcher(adresse).find()) {
                        return p;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Absender nicht lesbar: {}", e.getMessage());
        }
        return null;
    }

    public List<Stellenanzeige> parse(Message mail) throws Exception {
        Portal portal = portal(mail);
        if (portal == null) {
            return List.of();
        }
        String html = htmlTeil(mail);
        if (html == null || html.isBlank()) {
            log.debug("Mail '{}' hat keinen HTML-Teil", mail.getSubject());
            return List.of();
        }
        Date gesendet = mail.getSentDate() != null ? mail.getSentDate() : mail.getReceivedDate();
        LocalDate datum = gesendet != null
                ? gesendet.toInstant().atZone(ZoneId.of("Europe/Berlin")).toLocalDate() : null;
        return parse(portal, html, datum);
    }

    /** Kern ohne Mail-Objekt: HTML rein, Anzeigen raus. */
    public List<Stellenanzeige> parse(Portal portal, String html, LocalDate datum) {
        Document doc = Jsoup.parse(html);
        // Reihenfolge behalten, aber jeden Link nur einmal - Portale verlinken Titel und Button auf dieselbe Stelle.
        Map<String, Element> jobLinks = new LinkedHashMap<>();
        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href");
            if (!portal.jobLink().matcher(href).find()) {
                continue;
            }
            String schluessel = bereinigeLink(href);
            Element vorhanden = jobLinks.get(schluessel);
            // Den Link mit Text bevorzugen - Bilder und Buttons tragen den Titel nicht.
            if (vorhanden == null || (vorhanden.text().isBlank() && !a.text().isBlank())) {
                jobLinks.put(schluessel, a);
            }
        }

        List<Stellenanzeige> ergebnis = new ArrayList<>();
        for (Map.Entry<String, Element> e : jobLinks.entrySet()) {
            Stellenanzeige anzeige = anzeigeAus(portal, e.getKey(), e.getValue(), jobLinks, datum);
            if (anzeige != null) {
                ergebnis.add(anzeige);
            }
        }
        return ergebnis;
    }

    private Stellenanzeige anzeigeAus(Portal portal, String link, Element a,
                                      Map<String, Element> alleLinks, LocalDate datum) {
        String titel = saeubere(a.text());
        if (titel.isBlank()) {
            titel = saeubere(a.attr("title"));
        }
        if (titel.isBlank()) {
            return null; // Button oder Bild ohne Titel - kommt ueber den Titel-Link noch einmal
        }

        Element block = block(a, alleLinks, link);
        List<String> zeilen = zeilen(block, titel);

        // Bei allen vier Portalen: erste Zeile unter dem Titel ist die Firma,
        // der Ort folgt kurz danach als eigene Zeile.
        String firma = zeilen.isEmpty() ? null : zeilen.get(0);
        String ort = null;
        for (int i = 1; i < zeilen.size() && i <= 3; i++) {
            if (ORT.matcher(zeilen.get(i)).matches()) {
                ort = zeilen.get(i);
                break;
            }
        }
        // Manche Portale schreiben Firma und Ort in eine Zeile: "Muster GmbH - Wuppertal" / "Muster GmbH, Wuppertal".
        if (ort == null && firma != null) {
            Matcher m = Pattern.compile("^(.+?)\\s+[-–|•·]\\s+(.+)$").matcher(firma);
            if (m.matches() && ORT.matcher(m.group(2)).matches()) {
                firma = m.group(1);
                ort = m.group(2);
            }
        }
        if (ort == null) {
            ort = ortAusLink(portal, link);
        }

        Stellenanzeige anzeige = new Stellenanzeige(
                firma != null ? firma : "(unbekannt, " + portal.name() + ")", titel, ort);
        anzeige.setQuelle("Job-Alert " + portal.name());
        anzeige.setLink(link);
        anzeige.setRefnr(refnr(portal, link));
        anzeige.setVeroeffentlicht(datum);
        anzeige.setPlz(plzAus(ort));
        return anzeige;
    }

    /**
     * Kleinster Vorfahre, der keinen anderen Job-Link enthaelt. Das ist bei
     * allen vier Portalen die Tabellenzeile bzw. der Container einer Stelle.
     */
    private static Element block(Element a, Map<String, Element> alleLinks, String eigenerLink) {
        Element aktuell = a;
        while (aktuell.parent() != null && !"body".equals(aktuell.parent().tagName())) {
            Element kandidat = aktuell.parent();
            for (Element anderer : kandidat.select("a[href]")) {
                String href = bereinigeLink(anderer.attr("href"));
                if (alleLinks.containsKey(href) && !href.equals(eigenerLink)) {
                    return aktuell;
                }
            }
            aktuell = kandidat;
        }
        return aktuell;
    }

    /** Sichtbare Textzeilen des Blocks ohne den Titel und ohne Rauschen. */
    private static List<String> zeilen(Element block, String titel) {
        String html = block.outerHtml()
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|td|th|tr|li|h[1-6]|table|span)>", "$0\n");
        String text = Jsoup.parse(html).wholeText();
        List<String> ergebnis = new ArrayList<>();
        for (String roh : text.split("\\r?\\n")) {
            String zeile = saeubere(roh);
            if (zeile.isEmpty() || zeile.equalsIgnoreCase(titel) || zeile.length() > 90
                    || RAUSCHEN.matcher(zeile).matches() || ergebnis.contains(zeile)) {
                continue;
            }
            ergebnis.add(zeile);
        }
        return ergebnis;
    }

    private static String saeubere(String s) {
        if (s == null) {
            return "";
        }
        return s.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    /** Tracking-Parameter weg, damit derselbe Job aus zwei Links nur einmal zaehlt. */
    static String bereinigeLink(String href) {
        String h = href.trim();
        Matcher jk = Pattern.compile("(?i)indeed\\.com/.*[?&]jk=([a-f0-9]+)").matcher(h);
        if (jk.find()) {
            return "https://de.indeed.com/viewjob?jk=" + jk.group(1);
        }
        int frage = h.indexOf('?');
        if (frage < 0) {
            return h;
        }
        // Nur die Parameter behalten, die die Stelle identifizieren (meinestadt: ?id=...).
        StringBuilder behalten = new StringBuilder();
        for (String param : h.substring(frage + 1).split("&")) {
            String name = param.contains("=") ? param.substring(0, param.indexOf('=')) : param;
            if (name.equalsIgnoreCase("id") || name.equalsIgnoreCase("jobid")) {
                behalten.append(behalten.length() == 0 ? "?" : "&").append(param);
            }
        }
        return h.substring(0, frage) + behalten;
    }

    static String refnr(Portal portal, String link) {
        Matcher m = switch (portal.name()) {
            case "Indeed" -> Pattern.compile("jk=([a-f0-9]+)").matcher(link);
            case "StepStone" -> Pattern.compile("--(\\d+)-inline").matcher(link);
            default -> Pattern.compile("(\\d{5,})(?!.*\\d{5,})").matcher(link);
        };
        return m.find() ? portal.name() + ":" + m.group(1) : portal.name() + ":" + link.hashCode();
    }

    /** meinestadt traegt die Stadt im Pfad: jobs.meinestadt.de/wuppertal/... */
    static String ortAusLink(Portal portal, String link) {
        if (!"meinestadt".equals(portal.name())) {
            return null;
        }
        try {
            String pfad = URI.create(link).getPath();
            String[] teile = pfad.split("/");
            if (teile.length > 1 && !teile[1].isBlank()) {
                String stadt = teile[1].replace('-', ' ');
                return Character.toUpperCase(stadt.charAt(0)) + stadt.substring(1).toLowerCase(Locale.GERMAN);
            }
        } catch (Exception e) {
            // kein brauchbarer Pfad
        }
        return null;
    }

    private static String plzAus(String ort) {
        if (ort == null) {
            return null;
        }
        Matcher m = Pattern.compile("\\b(\\d{5})\\b").matcher(ort);
        return m.find() ? m.group(1) : null;
    }

    /** HTML-Teil der Mail, notfalls der Text-Teil; Multipart wird rekursiv durchsucht. */
    static String htmlTeil(Part teil) throws Exception {
        if (teil.isMimeType("text/html")) {
            return String.valueOf(teil.getContent());
        }
        if (teil.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) teil.getContent();
            String text = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part unter = mp.getBodyPart(i);
                String gefunden = htmlTeil(unter);
                if (gefunden != null && unter.isMimeType("text/html")) {
                    return gefunden;
                }
                if (gefunden != null && text == null) {
                    text = gefunden;
                }
            }
            return text;
        }
        if (teil.isMimeType("text/plain")) {
            // Nur-Text-Mails: Zeilen als Absaetze, damit die Link-Suche etwas findet.
            return "<pre>" + String.valueOf(teil.getContent()).replaceAll("(https?://\\S+)", "<a href=\"$1\">$1</a>") + "</pre>";
        }
        return null;
    }
}
