package dev.gkit.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * PostgreSQL store wrapper with connection pooling and transaction helpers.
 *
 * <pre>{@code
 * Store store = Store.open(Store.Config.builder()
 *     .host("localhost").port(5432)
 *     .database("mydb").user("app").password("secret")
 *     .build());
 *
 * store.withTx(jdbc -> {
 *     jdbc.update("INSERT INTO orders (id) VALUES (?)", orderId);
 *     return null;
 * });
 * }</pre>
 */
public final class Store {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate txTemplate;

    private Store(DataSource ds) {
        this.dataSource = ds;
        this.jdbc = new JdbcTemplate(ds);
        this.txTemplate = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }

    public static Store open(Config cfg) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(String.format("jdbc:postgresql://%s:%d/%s", cfg.host, cfg.port, cfg.database));
        ds.setUsername(cfg.user);
        ds.setPassword(cfg.password);
        return new Store(ds);
    }

    public JdbcTemplate jdbc() { return jdbc; }
    public DataSource dataSource() { return dataSource; }

    public <T> T withTx(Function<JdbcTemplate, T> fn) {
        return txTemplate.execute(status -> fn.apply(jdbc));
    }

    public void withTx(Runnable fn) {
        txTemplate.execute(status -> { fn.run(); return null; });
    }

    public List<Map<String, Object>> query(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    public static final class Config {
        private final String host, database, user, password;
        private final int port;

        private Config(Builder b) {
            this.host = b.host; this.port = b.port; this.database = b.database;
            this.user = b.user; this.password = b.password;
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String host = "localhost", database = "postgres", user = "postgres", password = "";
            private int port = 5432;

            public Builder host(String v) { this.host = v; return this; }
            public Builder port(int v) { this.port = v; return this; }
            public Builder database(String v) { this.database = v; return this; }
            public Builder user(String v) { this.user = v; return this; }
            public Builder password(String v) { this.password = v; return this; }
            public Config build() { return new Config(this); }
        }
    }
}
