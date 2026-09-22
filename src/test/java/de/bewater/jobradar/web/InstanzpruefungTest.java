package de.bewater.jobradar.web;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Der zweite Doppelklick darf nur dann still die Oberflaeche zeigen, wenn auf
 * dem Port auch wirklich JobRadar sitzt. Geprueft wird gegen einen echten
 * kleinen HTTP-Server auf einem freien Port - ohne Spring, ohne die Anwendung.
 */
class InstanzpruefungTest {

    private static int freierPort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** Server, der auf jeden Pfad dieselbe Antwort gibt. */
    private static HttpServer server(int port, String antwort) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/", austausch -> {
            byte[] koerper = antwort.getBytes(StandardCharsets.UTF_8);
            austausch.sendResponseHeaders(200, koerper.length);
            try (OutputStream out = austausch.getResponseBody()) {
                out.write(koerper);
            }
        });
        server.start();
        return server;
    }

    @Test
    @DisplayName("JobRadar-Kennung auf dem Port: laeuft schon")
    void laeuftSchon() throws Exception {
        int port = freierPort();
        HttpServer server = server(port, "jobradar");
        try {
            assertThat(Instanzpruefung.pruefe(port)).isEqualTo(Instanzpruefung.Zustand.LAEUFT_SCHON);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Alter Stand ohne /status: Seitentitel reicht als Kennung")
    void laeuftSchonUeberSeitentitel() throws Exception {
        int port = freierPort();
        HttpServer server = server(port, "<!doctype html><html><head><title>JobRadar</title></head><body></body></html>");
        try {
            assertThat(Instanzpruefung.pruefe(port)).isEqualTo(Instanzpruefung.Zustand.LAEUFT_SCHON);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Fremdes Programm auf dem Port wird nicht fuer JobRadar gehalten")
    void fremd() throws Exception {
        int port = freierPort();
        HttpServer server = server(port, "<html><title>Irgendein Dienst</title></html>");
        try {
            assertThat(Instanzpruefung.pruefe(port)).isEqualTo(Instanzpruefung.Zustand.FREMD);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Niemand auf dem Port: frei, normal starten")
    void frei() throws Exception {
        assertThat(Instanzpruefung.pruefe(freierPort())).isEqualTo(Instanzpruefung.Zustand.FREI);
    }
}
