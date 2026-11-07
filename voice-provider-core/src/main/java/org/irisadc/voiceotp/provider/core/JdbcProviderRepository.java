package org.irisadc.voiceotp.provider.core;

import org.irisadc.voiceotp.provider.api.ProviderRecord;
import org.irisadc.voiceotp.provider.api.ProviderRepository;
import org.irisadc.voiceotp.provider.api.ProviderScope;
import org.irisadc.voiceotp.provider.api.VoiceProviderException;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC persistence adapter. The DataSource is supplied by the host runtime (for example,
 * a WSO2-managed datasource); this class never creates credentials or owns the pool.
 * Schema creation is intentionally handled by the deployment migration.
 */
public final class JdbcProviderRepository implements ProviderRepository {
    private static final String COLUMNS = "tenant_domain, organization_id, provider_name, ari_url, "
            + "ari_username, secret_reference, application_name, outbound_endpoint, dialplan_context, "
            + "dialplan_extension, caller_id, ring_timeout_seconds, max_call_duration_seconds, "
            + "language, enabled, revision, created_at_epoch_ms, updated_at_epoch_ms";
    private final DataSource dataSource;

    public JdbcProviderRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public ProviderRecord create(ProviderScope scope, ProviderRecord record) throws VoiceProviderException {
        String sql = "INSERT INTO voice_provider (" + COLUMNS + ") VALUES ("
                + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            bindScopeAndRecord(p, scope, record);
            p.executeUpdate();
            return record;
        } catch (SQLException e) {
            if (isConstraintViolation(e)) {
                throw new VoiceProviderException(VoiceProviderException.Code.ALREADY_EXISTS,
                        "Voice provider already exists in this scope");
            }
            throw unavailable("Unable to persist voice provider", e);
        }
    }

    @Override
    public Optional<ProviderRecord> find(ProviderScope scope, String name) {
        String sql = "SELECT " + COLUMNS + " FROM voice_provider "
                + "WHERE tenant_domain = ? AND organization_id = ? AND provider_name = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            bindScope(p, scope);
            p.setString(3, name);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read voice provider repository", e);
        }
    }

    @Override
    public List<ProviderRecord> list(ProviderScope scope) {
        String sql = "SELECT " + COLUMNS + " FROM voice_provider "
                + "WHERE tenant_domain = ? AND organization_id = ? ORDER BY provider_name";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            bindScope(p, scope);
            try (ResultSet rs = p.executeQuery()) {
                List<ProviderRecord> records = new ArrayList<>();
                while (rs.next()) records.add(read(rs));
                return List.copyOf(records);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to list voice providers", e);
        }
    }

    @Override
    public ProviderRecord update(ProviderScope scope, ProviderRecord record, long expectedRevision)
            throws VoiceProviderException {
        String sql = "UPDATE voice_provider SET ari_url=?, ari_username=?, secret_reference=?, "
                + "application_name=?, outbound_endpoint=?, dialplan_context=?, dialplan_extension=?, "
                + "caller_id=?, ring_timeout_seconds=?, max_call_duration_seconds=?, language=?, "
                + "enabled=?, revision=?, updated_at_epoch_ms=? "
                + "WHERE tenant_domain=? AND organization_id=? AND provider_name=? AND revision=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            int i = 1;
            p.setString(i++, record.ariUrl());
            p.setString(i++, record.username());
            p.setString(i++, record.secretReference());
            p.setString(i++, record.application());
            p.setString(i++, record.outboundEndpoint());
            p.setString(i++, record.dialplanContext());
            p.setString(i++, record.dialplanExtension());
            p.setString(i++, record.callerId());
            p.setInt(i++, record.ringTimeoutSeconds());
            p.setInt(i++, record.maxCallDurationSeconds());
            p.setString(i++, record.language());
            p.setBoolean(i++, record.enabled());
            p.setLong(i++, record.revision());
            p.setLong(i++, record.updatedAt().toEpochMilli());
            p.setString(i++, scope.tenantDomain());
            p.setString(i++, scope.organizationId());
            p.setString(i++, record.name());
            p.setLong(i, expectedRevision);
            if (p.executeUpdate() != 1) throw revisionOrMissing(scope, record.name());
            return record;
        } catch (SQLException e) {
            throw unavailable("Unable to update voice provider", e);
        }
    }

    @Override
    public void delete(ProviderScope scope, String name, long expectedRevision) throws VoiceProviderException {
        String sql = "DELETE FROM voice_provider WHERE tenant_domain=? AND organization_id=? "
                + "AND provider_name=? AND revision=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            bindScope(p, scope);
            p.setString(3, name);
            p.setLong(4, expectedRevision);
            if (p.executeUpdate() != 1) throw revisionOrMissing(scope, name);
        } catch (SQLException e) {
            throw unavailable("Unable to delete voice provider", e);
        }
    }

    private VoiceProviderException revisionOrMissing(ProviderScope scope, String name) {
        return find(scope, name).isPresent()
                ? new VoiceProviderException(VoiceProviderException.Code.VERSION_CONFLICT,
                    "Provider revision conflict")
                : new VoiceProviderException(VoiceProviderException.Code.NOT_FOUND, "Voice provider not found");
    }

    private static void bindScopeAndRecord(PreparedStatement p, ProviderScope scope, ProviderRecord r)
            throws SQLException {
        bindScope(p, scope);
        p.setString(3, r.name());
        p.setString(4, r.ariUrl());
        p.setString(5, r.username());
        p.setString(6, r.secretReference());
        p.setString(7, r.application());
        p.setString(8, r.outboundEndpoint());
        p.setString(9, r.dialplanContext());
        p.setString(10, r.dialplanExtension());
        p.setString(11, r.callerId());
        p.setInt(12, r.ringTimeoutSeconds());
        p.setInt(13, r.maxCallDurationSeconds());
        p.setString(14, r.language());
        p.setBoolean(15, r.enabled());
        p.setLong(16, r.revision());
        p.setLong(17, r.createdAt().toEpochMilli());
        p.setLong(18, r.updatedAt().toEpochMilli());
    }

    private static void bindScope(PreparedStatement p, ProviderScope scope) throws SQLException {
        p.setString(1, scope.tenantDomain());
        p.setString(2, scope.organizationId());
    }

    private static ProviderRecord read(ResultSet rs) throws SQLException {
        return new ProviderRecord(rs.getString("provider_name"), rs.getString("ari_url"),
                rs.getString("ari_username"), rs.getString("secret_reference"),
                rs.getString("application_name"), rs.getString("outbound_endpoint"),
                rs.getString("dialplan_context"), rs.getString("dialplan_extension"),
                rs.getString("caller_id"), rs.getInt("ring_timeout_seconds"),
                rs.getInt("max_call_duration_seconds"), rs.getString("language"),
                rs.getBoolean("enabled"), rs.getLong("revision"),
                Instant.ofEpochMilli(rs.getLong("created_at_epoch_ms")),
                Instant.ofEpochMilli(rs.getLong("updated_at_epoch_ms")));
    }

    private static boolean isConstraintViolation(SQLException e) {
        String state = e.getSQLState();
        return state != null && (state.startsWith("23") || state.equals("23505"));
    }

    private static VoiceProviderException unavailable(String message, SQLException cause) {
        return new VoiceProviderException(VoiceProviderException.Code.SERVICE_UNAVAILABLE, message, cause);
    }
}
