package de.bewater.jobradar.web;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Faengt der Port schon jemanden? Ein blosser Socket-Test wuerde nur sagen
 * "belegt" - hier wird gefragt, ob dahinter wirklich JobRadar steckt. Nur dann
 * darf der zweite Doppelklick einfach die Oberflaeche zeigen; ein fremdes
 * Programm auf dem Port muss auffallen statt still zu scheitern.
 */
public final class Instanzpruefung {

    /** Kurz halten: der Nutzer hat gerade doppelt geklickt und wartet. */
    private static final int TIMEOUT_MS = 1000;
    private static final String KENNUNG = "jobradar";

    public enum Zustand {
        /** Niemand antwortet - normal starten. */
        FREI,
        /** JobRadar laeuft schon - nur die Oberflaeche zeigen. */
        LAEUFT_SCHON,
        /** Ein anderes Programm sitzt auf dem Port. */
        FREMD
    }

    private Instanzpruefung() {
    }

    public static Zustand pruefe(int port) {
        String status = hole("http://localhost:" + port + "/status");
        if (status != null && status.toLowerCase().contains(KENNUNG)) {
            return Zustand.LAEUFT_SCHON;
        }
        // Aeltere Staende kennen /status noch nicht - dann reicht der Seitentitel.
        String seite = hole("http://localhost:" + port + "/");
        if (seite != null && seite.toLowerCase().contains("<title>jobradar</title>")) {
            return Zustand.LAEUFT_SCHON;
        }
        return status == null && seite == null ? Zustand.FREI : Zustand.FREMD;
    }

    /** Antworttext oder null, wenn niemand erreichbar ist. */
    private static String hole(String adresse) {
        HttpURLConnection verbindung = null;
        try {
            verbindung = (HttpURLConnection) URI.create(adresse).toURL().openConnection();
            verbindung.setConnectTimeout(TIMEOUT_MS);
            verbindung.setReadTimeout(TIMEOUT_MS);
            verbindung.setRequestMethod("GET");
            int code = verbindung.getResponseCode();
            try (InputStream in = code < 400 ? verbindung.getInputStream() : verbindung.getErrorStream()) {
                // Ein fremdes Programm antwortet auch - der Text entscheidet, nicht der Code.
                return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (verbindung != null) {
                verbindung.disconnect();
            }
        }
    }
}
