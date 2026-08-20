package io.msc.storage;

import io.msc.domain.AuditEntry;
import io.msc.domain.Contribution;
import io.msc.domain.Decision;
import io.msc.domain.OriginKey;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;

/**
 * SQLite-backed repository. Single source of truth for contributions,
 * origin keys, and the append-only audit log.
 *
 * Public API is the minimum needed to support {@code POST /msc/send}:
 * key lookup, contribution insert, decision update, and audit append.
 * Everything else (list, rate buckets) is removed.
 */
public final class MscRepository {

    private static final String SCHEMA = """
        CREATE TABLE IF NOT EXISTS contributions (
            id           TEXT PRIMARY KEY,
            origin       TEXT NOT NULL,
            body         TEXT NOT NULL,
            confidence   TEXT NOT NULL,
            type         TEXT NOT NULL,
            language     TEXT,
            user_attr    TEXT NOT NULL,
            license      TEXT NOT NULL,
            tags_csv     TEXT NOT NULL DEFAULT '',
            source_url   TEXT NOT NULL,
            source_canon TEXT,
            timestamp    TEXT NOT NULL,
            created_at   TEXT NOT NULL,
            status       TEXT NOT NULL DEFAULT 'queued',
            decision     TEXT,
            reason       TEXT,
            moderator_id TEXT,
            decided_at   TEXT,
            moderated_body TEXT
        );
        CREATE INDEX IF NOT EXISTS idx_contrib_status ON contributions(status);
        CREATE INDEX IF NOT EXISTS idx_contrib_created ON contributions(created_at);

        CREATE TABLE IF NOT EXISTS keys (
            origin     TEXT PRIMARY KEY,
            public_key TEXT NOT NULL,
            revoked    INTEGER NOT NULL DEFAULT 0,
            added_at   TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS audit_log (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            contribution_id TEXT,
            decision        TEXT NOT NULL,
            reason          TEXT,
            moderator_id    TEXT NOT NULL,
            timestamp       TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_audit_contrib ON audit_log(contribution_id);
        """;

    private final DataSource dataSource;

    public MscRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            // SQLite JDBC's Statement.execute() runs only the first statement,
            // so we split the script and run each non-empty statement individually.
            for (String stmt : SCHEMA.split(";")) {
                String trimmed = stmt.trim();
                if (!trimmed.isEmpty()) {
                    s.execute(trimmed);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not initialise SQLite schema", e);
        }
    }

    // ---------- keys ----------

    public Optional<OriginKey> getActiveKey(String origin) {
        return queryOne("SELECT origin, public_key, revoked FROM keys " +
                        "WHERE origin = ? AND revoked = 0", ps -> ps.setString(1, origin),
            rs -> new OriginKey(rs.getString("origin"),
                                rs.getString("public_key"),
                                rs.getInt("revoked") == 1));
    }

    // ---------- contributions ----------

    /**
     * Insert a contribution. Returns false if the id already exists.
     */
    public boolean insertContribution(Contribution c) {
        try (Connection conn = dataSource.getConnection()) {
            try {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO contributions(id, origin, body, confidence, type,
                        language, user_attr, license, tags_csv, source_url,
                        source_canon, timestamp, created_at, status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """)) {
                    ps.setString(1, c.id());
                    ps.setString(2, c.origin());
                    ps.setString(3, c.body());
                    ps.setString(4, c.confidence().toPlainString());
                    ps.setString(5, c.contributionType());
                    ps.setString(6, c.language());
                    ps.setString(7, c.userAttribution());
                    ps.setString(8, c.license());
                    ps.setString(9, String.join(",", c.tags()));
                    ps.setString(10, c.sourceUrl());
                    ps.setString(11, c.sourceCanonical());
                    ps.setString(12, c.timestamp());
                    ps.setString(13, c.createdAt().toString());
                    ps.setString(14, c.statusWire());
                    ps.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                if ("UNIQUE constraint failed".contains(e.getMessage())) {
                    return false;
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("insertContribution failed", e);
        }
    }

    public Optional<Contribution> getContribution(String id) {
        return queryOne("SELECT * FROM contributions WHERE id = ?",
            ps -> ps.setString(1, id),
            MscRepository::mapContribution);
    }

    public void recordDecision(String contributionId, Decision decision,
                               String reason, String moderatorId,
                               String newStatus, Instant decidedAt,
                               String moderatedBody) {
        run("UPDATE contributions SET status = ?, decision = ?, reason = ?, " +
            "moderator_id = ?, decided_at = ?, moderated_body = ? WHERE id = ?",
            ps -> {
            ps.setString(1, newStatus);
            ps.setString(2, decision.wire());
            ps.setString(3, reason);
            ps.setString(4, moderatorId);
            ps.setString(5, decidedAt.toString());
            ps.setString(6, moderatedBody);
            ps.setString(7, contributionId);
        });
    }

    // ---------- audit log ----------

    public long appendAudit(String contributionId, Decision decision,
                            String reason, String moderatorId, Instant timestamp) {
        return insertAndReturnId(
            "INSERT INTO audit_log(contribution_id, decision, reason, moderator_id, timestamp) " +
            "VALUES (?, ?, ?, ?, ?)",
            ps -> {
            if (contributionId == null) ps.setNull(1, Types.VARCHAR);
            else ps.setString(1, contributionId);
            ps.setString(2, decision.wire());
            ps.setString(3, reason);
            ps.setString(4, moderatorId);
            ps.setString(5, timestamp.toString());
        });
    }

    // ---------- helpers ----------

    private static Contribution mapContribution(ResultSet rs) throws SQLException {
        String tagsCsv = rs.getString("tags_csv");
        java.util.List<String> tags = tagsCsv == null || tagsCsv.isEmpty()
            ? java.util.List.of() : java.util.List.of(tagsCsv.split(","));
        Contribution c = new Contribution(
            rs.getString("id"),
            rs.getString("origin"),
            rs.getString("body"),
            new BigDecimal(rs.getString("confidence")),
            rs.getString("type"),
            rs.getString("language"),
            rs.getString("user_attr"),
            rs.getString("license"),
            tags,
            rs.getString("source_url"),
            rs.getString("source_canon"),
            rs.getString("timestamp"),
            Instant.parse(rs.getString("created_at"))
        );
        String decisionWire = rs.getString("decision");
        String decidedAtStr = rs.getString("decided_at");
        Instant decidedAt = decidedAtStr != null ? Instant.parse(decidedAtStr) : null;
        if (decisionWire != null) {
            Decision d = Decision.valueOf(decisionWire.toUpperCase());
            String reason = rs.getString("reason");
            String moderatorId = rs.getString("moderator_id");
            c.recordDecision(d, reason, moderatorId,
                             decidedAt != null ? decidedAt : Instant.now());
        }
        return c;
    }

    private void run(String sql, SqlBinder binder) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("SQL error: " + sql, e);
        }
    }

    private long insertAndReturnId(String sql, SqlBinder binder) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            binder.bind(ps);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL error: " + sql, e);
        }
    }

    private <T> Optional<T> queryOne(String sql, SqlBinder binder, SqlMapper<T> mapper) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL error: " + sql, e);
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}