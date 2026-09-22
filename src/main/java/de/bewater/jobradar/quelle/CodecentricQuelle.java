package de.bewater.jobradar.quelle;

import de.bewater.jobradar.config.QuellenProperties;
import de.bewater.jobradar.config.SuchProperties;
import de.bewater.jobradar.domain.Stellenanzeige;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * codecentric ueber den Personio-XML-Feed. Die Stellen sind meist "Hybrid" mit
 * einer langen Liste weiterer Standorte - fuer den Ortsfilter zaehlt der erste
 * Standort, der in jobradar.erlaubte-orte steht (bei codecentric: Solingen).
 */
@Component
@Order(84)
public class CodecentricQuelle extends JsonQuelleBasis {

    private static final String ANZEIGEN_BASIS = "https://codecentric.jobs.personio.de/job/";

    private final SuchProperties suche;

    public CodecentricQuelle(QuellenProperties props, SuchProperties suche) {
        super(props, "codecentric");
        this.suche = suche;
    }

    @Override
    public String name() {
        return "codecentric";
    }

    @Override
    public List<Stellenanzeige> parse(String antwort) throws Exception {
        List<Stellenanzeige> ergebnis = new ArrayList<>();
        for (Element position : positionen(antwort)) {
            String id = kind(position, "id");
            ergebnis.add(anzeige(
                    oder(konfig.getFirma(), name()),
                    kind(position, "name"),
                    ort(position),
                    id != null ? ANZEIGEN_BASIS + id + "?language=de" : null,
                    id,
                    datum(kind(position, "createdAt")),
                    null));
        }
        return ergebnis;
    }

    // ---- XML --------------------------------------------------------------

    /** Parser ohne DOCTYPE und ohne externe Entities - der Feed kommt aus dem Netz. */
    private List<Element> positionen(String xml) throws Exception {
        DocumentBuilderFactory fabrik = DocumentBuilderFactory.newInstance();
        fabrik.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        fabrik.setFeature("http://xml.org/sax/features/external-general-entities", false);
        fabrik.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        fabrik.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        fabrik.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        fabrik.setXIncludeAware(false);
        fabrik.setExpandEntityReferences(false);

        Document dok = fabrik.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        List<Element> ergebnis = new ArrayList<>();
        NodeList liste = dok.getElementsByTagName("position");
        for (int i = 0; i < liste.getLength(); i++) {
            ergebnis.add((Element) liste.item(i));
        }
        return ergebnis;
    }

    /** Direktes Kindelement - nicht getElementsByTagName, das greift zu tief. */
    private static String kind(Element eltern, String name) {
        for (Node n = eltern.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && name.equals(n.getNodeName())) {
                String wert = n.getTextContent();
                return wert == null || wert.isBlank() ? null : wert.trim();
            }
        }
        return null;
    }

    // ---- Ort --------------------------------------------------------------

    /**
     * Erst ein Standort aus erlaubte-orte, sonst der erste Remote-/Hybrid-Eintrag,
     * sonst das office-Feld - damit der Ortsfilter im Suchlauf richtig greift.
     */
    private String ort(Element position) {
        List<String> orte = new ArrayList<>();
        String office = kind(position, "office");
        if (office != null) {
            orte.add(office);
        }
        for (Node n = position.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && "additionalOffices".equals(n.getNodeName())) {
                for (Node o = n.getFirstChild(); o != null; o = o.getNextSibling()) {
                    if (o.getNodeType() == Node.ELEMENT_NODE && "office".equals(o.getNodeName())) {
                        String wert = o.getTextContent();
                        if (wert != null && !wert.isBlank()) {
                            orte.add(wert.trim());
                        }
                    }
                }
            }
        }

        for (String ort : orte) {
            for (String erlaubt : suche.getErlaubteOrte()) {
                if (ort.toLowerCase().contains(erlaubt.toLowerCase())) {
                    return ort;
                }
            }
        }
        for (String ort : orte) {
            String klein = ort.toLowerCase();
            if (klein.contains("remote") || klein.contains("hybrid")) {
                return ort;
            }
        }
        return office;
    }
}
