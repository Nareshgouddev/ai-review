package com.uireview.repository;

import com.uireview.model.AnalysisRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link AnalysisRecord} persistence.
 *
 * <p>Custom query methods are resolved by Spring Data JPA via method-name
 * derivation, so no JPQL/SQL is required here.
 *
 * <ul>
 *   <li>{@link #findBySessionIdOrderByCreatedAtDesc} — supports Requirement 7.1/7.2
 *       (history endpoint with session isolation and pageable limit).</li>
 *   <li>{@link #findFirstByImageHashAndCreatedAtAfter} — supports Requirement 3.10
 *       (24-hour image-hash cache deduplication).</li>
 * </ul>
 */
@Repository
public interface AnalysisRepository extends JpaRepository<AnalysisRecord, String> {

    /**
     * Returns analysis records for the given session, newest first.
     *
     * @param sessionId the session identifier (max 64 chars)
     * @param pageable  controls the maximum number of results returned
     * @return ordered list of matching records
     */
    List<AnalysisRecord> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    /**
     * Returns the most recent record whose image hash matches and whose
     * {@code createdAt} timestamp is after the supplied cutoff (used to
     * enforce the 24-hour cache window).
     *
     * @param imageHash SHA-256 hex digest of the uploaded image
     * @param cutoff    lower-bound timestamp (exclusive); typically {@code now() - 24h}
     * @return the cached record, or {@link Optional#empty()} on a cache miss
     */
    Optional<AnalysisRecord> findFirstByImageHashAndCreatedAtAfter(String imageHash, LocalDateTime cutoff);
}
