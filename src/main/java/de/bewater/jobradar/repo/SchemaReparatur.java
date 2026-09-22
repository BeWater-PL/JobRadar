package de.bewater.jobradar.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Einmalige Reparatur aelterer Datenbanken: Fruehere Versionen haben die
 * Status-Spalte mit einer CHECK-Constraint auf die damaligen Enum-Werte
 * angelegt. SQLite kennt kein ALTER fuer Constraints - deshalb wird die
 * Tabelle hier ohne CHECK neu aufgebaut, mit allen Daten.
 * Laeuft vor dem ersten Suchlauf (Order 0, der Suchlauf haengt an ApplicationReady).
 */
@Component
@Order(0)
public class SchemaReparatur implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaReparatur.class);
    private static final String TABELLE = "stellenanzeige";
    private static final Pattern CHECK = Pattern.compile(",?\\s*check\\s*\\(status\\s+in\\s*\\([^)]*\\)\\)", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;

    public SchemaReparatur(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<String> sql = jdbc.queryForList(
                "select sql from sqlite_master where type = 'table' and name = ?", String.class, TABELLE);
        if (sql.isEmpty() || sql.get(0) == null) {
            return;
        }
        String alt = sql.get(0);
        Matcher m = CHECK.matcher(alt);
        if (!m.find()) {
            return;
        }
        String neu = m.replaceAll("");
        log.info("Entferne veraltete Status-CHECK-Constraint aus Tabelle {}", TABELLE);

        jdbc.execute("alter table " + TABELLE + " rename to " + TABELLE + "_alt");
        jdbc.execute(neu);
        jdbc.execute("insert into " + TABELLE + " select * from " + TABELLE + "_alt");
        jdbc.execute("drop table " + TABELLE + "_alt");
    }
}
