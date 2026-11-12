package com.workspace.context.infrastructure;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** context_snapshot and snapshot_item are immutable (triggers); this store only inserts and reads. */
@Repository
public class SnapshotStore {

    public record ItemRow(UUID documentVersionId, String contentHash, String inclusionMode) {}

    public record SnapshotRow(UUID id, String assemblerVersion, int tokenEstimate, Integer lastMessageSeq,
                              List<String> missingDocumentTypes, List<UUID> dependencyRunIds, List<ItemRow> items) {}

    private final JdbcTemplate jdbc;

    public SnapshotStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(String assemblerVersion, int tokenEstimate, Integer lastMessageSeq,
                       List<String> missingDocumentTypes, List<ItemRow> items) {
        UUID id = UUID.randomUUID();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    insert into context_snapshot(id, assembler_version, token_estimate, last_message_seq,
                                                 missing_document_types, dependency_run_ids)
                    values (?, ?, ?, ?, ?, ?)""");
            ps.setObject(1, id);
            ps.setString(2, assemblerVersion);
            ps.setInt(3, tokenEstimate);
            ps.setObject(4, lastMessageSeq);
            ps.setArray(5, con.createArrayOf("text", missingDocumentTypes.toArray()));
            ps.setArray(6, con.createArrayOf("uuid", new Object[0]));
            return ps;
        });
        for (ItemRow i : items) {
            jdbc.update("insert into snapshot_item(snapshot_id, document_version_id, content_hash, inclusion_mode) values (?, ?, ?, ?)",
                    id, i.documentVersionId(), i.contentHash(), i.inclusionMode());
        }
        return id;
    }

    public Optional<SnapshotRow> find(UUID id) {
        List<SnapshotRow> rows = jdbc.query("""
                select id, assembler_version, token_estimate, last_message_seq, missing_document_types, dependency_run_ids
                from context_snapshot where id = ?""", (rs, n) -> {
            int seq = rs.getInt("last_message_seq");
            Integer lastSeq = rs.wasNull() ? null : seq;
            return new SnapshotRow(rs.getObject("id", UUID.class), rs.getString("assembler_version"),
                    rs.getInt("token_estimate"), lastSeq, strings(rs.getArray("missing_document_types")),
                    uuids(rs.getArray("dependency_run_ids")), List.of());
        }, id);
        if (rows.isEmpty()) return Optional.empty();
        SnapshotRow r = rows.get(0);
        List<ItemRow> items = jdbc.query("""
                select document_version_id, content_hash, inclusion_mode from snapshot_item
                where snapshot_id = ? order by document_version_id""", (rs, n) -> new ItemRow(
                rs.getObject("document_version_id", UUID.class), rs.getString("content_hash"),
                rs.getString("inclusion_mode")), id);
        return Optional.of(new SnapshotRow(r.id(), r.assemblerVersion(), r.tokenEstimate(), r.lastMessageSeq(),
                r.missingDocumentTypes(), r.dependencyRunIds(), items));
    }

    private static List<String> strings(Array a) throws SQLException {
        List<String> out = new ArrayList<>();
        if (a != null) for (Object o : (Object[]) a.getArray()) out.add(String.valueOf(o));
        return out;
    }

    private static List<UUID> uuids(Array a) throws SQLException {
        List<UUID> out = new ArrayList<>();
        if (a != null) for (Object o : (Object[]) a.getArray()) out.add(UUID.fromString(String.valueOf(o)));
        return out;
    }
}
