package de.bewater.jobradar;

import de.bewater.jobradar.repo.SchemaReparatur;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Baut eine Datenbank nach, wie sie eine aeltere Version angelegt hat,
 * und prueft, dass danach der neue Status gespeichert werden kann.
 */
class SchemaReparaturTest {

    private static final String ALTE_TABELLE = """
            create table stellenanzeige (
                fingerabdruck varchar(200) not null,
                firma varchar(255),
                titel varchar(255),
                status varchar(255) check (status in ('NEU','VORGEMERKT','BEWORBEN','VERWORFEN')),
                punkte integer not null,
                primary key (fingerabdruck)
            )""";

    private JdbcTemplate frischeDb() {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(ds);
    }

    @Test
    @DisplayName("Alte CHECK-Constraint wird entfernt, Daten bleiben erhalten")
    void checkWirdEntfernt() {
        JdbcTemplate jdbc = frischeDb();
        jdbc.execute(ALTE_TABELLE);
        jdbc.update("insert into stellenanzeige values ('a|b|c', 'Muster GmbH', 'Entwickler', 'BEWORBEN', 3)");

        new SchemaReparatur(jdbc).run(null);

        jdbc.update("update stellenanzeige set status = 'ABGELEHNT' where fingerabdruck = 'a|b|c'");
        assertThat(jdbc.queryForObject("select status from stellenanzeige", String.class)).isEqualTo("ABGELEHNT");
        assertThat(jdbc.queryForObject("select firma from stellenanzeige", String.class)).isEqualTo("Muster GmbH");
        assertThat(jdbc.queryForObject(
                "select sql from sqlite_master where name = 'stellenanzeige'", String.class)).doesNotContainIgnoringCase("check");
    }

    @Test
    @DisplayName("Ohne CHECK-Constraint passiert nichts")
    void ohneCheckBleibtAllesGleich() {
        JdbcTemplate jdbc = frischeDb();
        jdbc.execute("create table stellenanzeige (fingerabdruck varchar(200) primary key, status varchar(20))");
        jdbc.update("insert into stellenanzeige values ('x', 'NEU')");

        new SchemaReparatur(jdbc).run(null);

        assertThat(jdbc.queryForObject("select count(*) from stellenanzeige", Integer.class)).isEqualTo(1);
    }
}
