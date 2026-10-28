package com.workspace.document;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workspace.support.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

/** Brief section 14, "DB guards": triggers and constraints are part of the product. */
class DocumentSchemaTest extends IntegrationTestBase {

    private UUID owner;

    private UUID project() {
        owner = createUser("u" + UUID.randomUUID() + "@example.com", "correct-horse-battery");
        UUID p = UUID.randomUUID();
        jdbc.update("insert into project(id, name, created_by) values (?, 'p', ?)", p, owner);
        return p;
    }

    private UUID document(UUID project, String type) {
        UUID d = UUID.randomUUID();
        jdbc.update("insert into document(id, project_id, type, title) values (?, ?, ?, 't')", d, project, type);
        return d;
    }

    private UUID version(UUID doc, int no, String status) {
        UUID v = UUID.randomUUID();
        jdbc.update("""
                insert into document_version(id, document_id, version_no, content, content_hash, status,
                                             author_type, author_ref)
                values (?, ?, ?, 'c', 'sha256:x', ?, 'HUMAN', 'u')
                """, v, doc, no, status);
        return v;
    }

    private void setStatus(UUID v, String status) {
        jdbc.update("update document_version set status = ? where id = ?", status, v);
    }

    @Test
    void contentOfNonDraftVersionCannotChange() {
        UUID d = document(project(), "REQUIREMENTS");
        UUID v = version(d, 1, "IN_REVIEW");
        assertThatThrownBy(() -> jdbc.update("update document_version set content = 'x' where id = ?", v))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("content is immutable after submit");
        assertThatThrownBy(() -> jdbc.update("update document_version set content_hash = 'y' where id = ?", v))
                .hasMessageContaining("content is immutable after submit");
    }

    @Test
    void immutableColumnsCannotChange() {
        UUID d = document(project(), "REQUIREMENTS");
        UUID v = version(d, 1, "DRAFT");
        assertThatThrownBy(() -> jdbc.update("update document_version set version_no = 9 where id = ?", v))
                .hasMessageContaining("immutable column changed");
    }

    @Test
    void illegalStatusTransitionsAreRejected() {
        UUID p = project();

        UUID draft = version(document(p, "ADR"), 1, "DRAFT");
        assertThatThrownBy(() -> setStatus(draft, "APPROVED")).hasMessageContaining("illegal transition from DRAFT");
        assertThatThrownBy(() -> setStatus(draft, "REJECTED")).hasMessageContaining("illegal transition from DRAFT");

        UUID inReview = version(document(p, "ADR"), 1, "IN_REVIEW");
        assertThatThrownBy(() -> setStatus(inReview, "DRAFT")).hasMessageContaining("illegal status transition");
        assertThatThrownBy(() -> setStatus(inReview, "DISCARDED")).hasMessageContaining("illegal status transition");

        UUID approved = version(document(p, "ADR"), 1, "APPROVED");
        assertThatThrownBy(() -> setStatus(approved, "IN_REVIEW")).hasMessageContaining("illegal status transition");

        UUID rejected = version(document(p, "ADR"), 1, "REJECTED");
        assertThatThrownBy(() -> setStatus(rejected, "APPROVED")).hasMessageContaining("illegal status transition");

        UUID superseded = version(document(p, "ADR"), 1, "SUPERSEDED");
        assertThatThrownBy(() -> setStatus(superseded, "APPROVED")).hasMessageContaining("illegal status transition");
    }

    @Test
    void legalTransitionsWork() {
        UUID v = version(document(project(), "ADR"), 1, "DRAFT");
        setStatus(v, "IN_REVIEW");
        setStatus(v, "APPROVED");
        setStatus(v, "SUPERSEDED");
    }

    @Test
    void secondOpenVersionIsRejected_andDiscardReleasesTheSlot() {
        UUID d = document(project(), "REQUIREMENTS");
        UUID first = version(d, 1, "DRAFT");
        assertThatThrownBy(() -> version(d, 2, "DRAFT")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> version(d, 2, "IN_REVIEW")).isInstanceOf(DataIntegrityViolationException.class);

        setStatus(first, "DISCARDED");
        version(d, 2, "DRAFT");   // slot released
    }

    @Test
    void documentVersionAndApprovalCannotBeDeleted() {
        UUID p = project();
        UUID d = document(p, "REQUIREMENTS");
        UUID v = version(d, 1, "IN_REVIEW");
        UUID approval = UUID.randomUUID();
        jdbc.update("""
                insert into approval(id, document_version_id, gate_code, approver_id, decision, content_hash)
                values (?, ?, 'REQUIREMENTS_APPROVED', ?, 'APPROVED', 'sha256:x')
                """, approval, v, owner);

        assertThatThrownBy(() -> jdbc.update("delete from document_version where id = ?", v))
                .hasMessageContaining("delete not allowed");
        assertThatThrownBy(() -> jdbc.update("delete from document where id = ?", d))
                .hasMessageContaining("delete not allowed");
        assertThatThrownBy(() -> jdbc.update("delete from approval where id = ?", approval))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("update approval set comment = 'x' where id = ?", approval))
                .hasMessageContaining("append-only");
    }

    @Test
    void approverIdCannotBeNull() {
        UUID v = version(document(project(), "REQUIREMENTS"), 1, "IN_REVIEW");
        assertThatThrownBy(() -> jdbc.update("""
                insert into approval(id, document_version_id, gate_code, approver_id, decision, content_hash)
                values (?, ?, 'REQUIREMENTS_APPROVED', null, 'APPROVED', 'sha256:x')
                """, UUID.randomUUID(), v)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void approvedPointerMustReferenceAnApprovedVersionOfTheSameDocument() {
        UUID p = project();
        UUID d = document(p, "REQUIREMENTS");
        UUID draft = version(d, 1, "DRAFT");
        assertThatThrownBy(() -> jdbc.update(
                "update document set current_approved_version_id = ? where id = ?", draft, d))
                .hasMessageContaining("must reference an APPROVED version");

        UUID otherDoc = document(p, "ARCHITECTURE");
        UUID otherApproved = version(otherDoc, 1, "APPROVED");
        assertThatThrownBy(() -> jdbc.update(
                "update document set current_approved_version_id = ? where id = ?", otherApproved, d))
                .isInstanceOf(DataIntegrityViolationException.class);   // composite FK (id, document_id)

        UUID ownApproved = version(d, 2, "APPROVED");
        jdbc.update("update document set current_approved_version_id = ? where id = ?", ownApproved, d);
    }
}