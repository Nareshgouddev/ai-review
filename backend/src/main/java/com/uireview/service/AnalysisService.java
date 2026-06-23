package com.uireview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uireview.model.AnalysisRecord;
import com.uireview.model.dto.AnalysisResultDto;
import com.uireview.model.dto.AnalysisSummaryDto;
import com.uireview.model.dto.CategoryDto;
import com.uireview.repository.AnalysisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the full screenshot analysis pipeline.
 *
 * <p>Pipeline (per {@code analyze()}):
 * <ol>
 *   <li>Validate file (MIME type, size, filename)</li>
 *   <li>Read bytes → compute SHA-256 hash</li>
 *   <li>Cache check: return stored result if found within 24 hours</li>
 *   <li>Call Anthropic Claude Vision API via {@link AnthropicClientService}</li>
 *   <li>Compute weighted overall score ({@link #computeOverallScore})</li>
 *   <li>Persist the {@link AnalysisRecord} with the full serialised result</li>
 *   <li>Return the populated {@link AnalysisResultDto}</li>
 * </ol>
 *
 * Requirements: 3.1–3.10, 4.4, 4.5, 7.1–7.3
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    /** Default history page size when the caller omits the {@code limit} parameter. */
    private static final int DEFAULT_HISTORY_LIMIT = 5;

    /**
     * Category weights used to compute the overall score (Requirement 4.5).
     * Keys must match the category names returned by Claude exactly.
     */
    private static final Map<String, Double> CATEGORY_WEIGHTS = Map.of(
            "Layout",           0.20,
            "Typography",       0.20,
            "Color & Contrast", 0.20,
            "Accessibility",    0.25,
            "Consistency",      0.15
    );

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private final FileValidationService     fileValidationService;
    private final AnthropicClientService    anthropicClientService;
    private final AnalysisRepository        analysisRepository;
    private final ObjectMapper              objectMapper;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public AnalysisService(
            FileValidationService  fileValidationService,
            AnthropicClientService anthropicClientService,
            AnalysisRepository     analysisRepository,
            ObjectMapper           objectMapper) {

        this.fileValidationService  = fileValidationService;
        this.anthropicClientService = anthropicClientService;
        this.analysisRepository     = analysisRepository;
        this.objectMapper           = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Validates, analyzes, caches, and persists a screenshot.
     *
     * @param file       the uploaded image
     * @param focusHint  optional user-supplied focus area (may be {@code null})
     * @param sessionId  optional browser session identifier (may be {@code null})
     * @param ipAddress  client IP address (for rate-limiting audit)
     * @return structured analysis result
     */
    public AnalysisResultDto analyze(MultipartFile file,
                                     String focusHint,
                                     String sessionId,
                                     String ipAddress) {
        // 1. Validate
        fileValidationService.validate(file);

        // 2. Read bytes and compute hash
        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file bytes", e);
        }
        String hash = fileValidationService.computeSha256(bytes);

        // 3. Cache check — return stored result if found within last 24 hours
        Optional<AnalysisRecord> cached = analysisRepository
                .findFirstByImageHashAndCreatedAtAfter(hash, LocalDateTime.now().minusHours(24));

        if (cached.isPresent()) {
            log.debug("Cache hit for image hash {}", hash);
            return deserializeCachedRecord(cached.get());
        }

        // 4. Cache miss — call Claude Vision API
        log.debug("Cache miss for image hash {}. Invoking Anthropic API.", hash);
        long startMs = System.currentTimeMillis();
        AnalysisResultDto rawResult = anthropicClientService.analyze(bytes, file.getContentType(), focusHint);

        // 5. Compute weighted overall score, overriding whatever Claude returned
        int overallScore = computeOverallScore(rawResult.categories());

        // 6. Build the final enriched DTO
        String id = UUID.randomUUID().toString();
        int processingMs = (int) (System.currentTimeMillis() - startMs);
        String resolvedSessionId = (sessionId != null) ? sessionId : "";

        AnalysisResultDto result = new AnalysisResultDto(
                id,
                resolvedSessionId,
                overallScore,
                processingMs,
                false,
                rawResult.categories()
        );

        // 7. Persist
        persistRecord(result, hash, focusHint, resolvedSessionId, ipAddress, processingMs);

        return result;
    }

    /**
     * Returns the most recent analyses for the given session.
     *
     * @param sessionId session identifier (may be {@code null}, treated as empty string)
     * @param limit     max entries to return; {@code null} → default 5, {@code 0} → empty list
     * @return list of summary DTOs, newest first
     */
    public List<AnalysisSummaryDto> getHistory(String sessionId, Integer limit) {
        int effectiveLimit = (limit == null) ? DEFAULT_HISTORY_LIMIT : limit;
        if (effectiveLimit == 0) {
            return List.of();
        }

        String resolvedSessionId = (sessionId != null) ? sessionId : "";
        List<AnalysisRecord> records = analysisRepository
                .findBySessionIdOrderByCreatedAtDesc(resolvedSessionId, PageRequest.of(0, effectiveLimit));

        return records.stream()
                .map(this::toSummaryDto)
                .toList();
    }

    /**
     * Returns a single analysis by its UUID identifier.
     *
     * @param id the UUID of the analysis record
     * @return the deserialized result DTO, or {@link Optional#empty()} if not found
     */
    public Optional<AnalysisResultDto> getAnalysisById(String id) {
        return analysisRepository.findById(id)
                .map(record -> {
                    try {
                        return objectMapper.readValue(record.getRawResponse(), AnalysisResultDto.class);
                    } catch (Exception e) {
                        log.error("Failed to deserialize rawResponse for record id={}", id, e);
                        throw new RuntimeException("Failed to deserialize analysis record", e);
                    }
                });
    }

    /**
     * Deletes a single analysis record by its UUID identifier.
     *
     * @param id the UUID of the analysis record to delete
     * @throws NoSuchElementException if no record with the given id exists
     */
    public void deleteAnalysis(String id) {
        if (!analysisRepository.existsById(id)) {
            throw new NoSuchElementException("Analysis record not found: " + id);
        }
        analysisRepository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // Weighted score computation (Property 3 / Requirement 4.4, 4.5)
    // -------------------------------------------------------------------------

    /**
     * Computes the weighted overall score from the given category list.
     *
     * <p>Formula: {@code round(sum(weight_i * score_i))} clamped to [0, 100].
     * Categories not present in the weight map contribute 0.
     *
     * @param categories list of category DTOs (may be empty)
     * @return weighted overall score in [0, 100]
     */
    int computeOverallScore(List<CategoryDto> categories) {
        if (categories == null || categories.isEmpty()) {
            return 0;
        }

        double weightedSum = 0.0;
        for (CategoryDto category : categories) {
            double weight = CATEGORY_WEIGHTS.getOrDefault(category.name(), 0.0);
            weightedSum += weight * category.score();
        }

        int score = Math.round((float) weightedSum);

        // Clamp to [0, 100]
        return Math.max(0, Math.min(100, score));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Deserializes the raw JSON stored in the cache record into an
     * {@link AnalysisResultDto} and marks it as cached.
     */
    private AnalysisResultDto deserializeCachedRecord(AnalysisRecord record) {
        try {
            // Deserialize via ObjectMapper for type-safety, then rebuild with cached=true
            AnalysisResultDto stored = objectMapper.readValue(record.getRawResponse(), AnalysisResultDto.class);

            return new AnalysisResultDto(
                    stored.id(),
                    stored.sessionId(),
                    stored.overallScore(),
                    stored.processingMs(),
                    true,          // mark as cached
                    stored.categories()
            );
        } catch (Exception e) {
            log.error("Failed to deserialize cached record id={}", record.getId(), e);
            throw new RuntimeException("Failed to deserialize cached analysis record", e);
        }
    }

    /**
     * Persists the final {@link AnalysisResultDto} as an {@link AnalysisRecord}.
     * The full DTO is serialized as {@code rawResponse} for future cache hits.
     */
    private void persistRecord(AnalysisResultDto result,
                               String hash,
                               String focusHint,
                               String sessionId,
                               String ipAddress,
                               int processingMs) {
        try {
            String rawJson = objectMapper.writeValueAsString(result);

            AnalysisRecord record = AnalysisRecord.builder()
                    .id(result.id())
                    .sessionId(sessionId)
                    .createdAt(LocalDateTime.now())
                    .overallScore(result.overallScore())
                    .processingMs(processingMs)
                    .imageHash(hash)
                    .rawResponse(rawJson)
                    .focusHint(focusHint)
                    .ipAddress((ipAddress != null) ? ipAddress : "")
                    .build();

            analysisRepository.save(record);
            log.debug("Persisted analysis record id={}", result.id());
        } catch (Exception e) {
            log.error("Failed to persist analysis record id={}", result.id(), e);
            throw new RuntimeException("Failed to persist analysis record", e);
        }
    }

    /**
     * Maps an {@link AnalysisRecord} to a lightweight {@link AnalysisSummaryDto}.
     */
    private AnalysisSummaryDto toSummaryDto(AnalysisRecord record) {
        return new AnalysisSummaryDto(
                record.getId(),
                record.getSessionId(),
                record.getCreatedAt(),
                record.getOverallScore(),
                record.getProcessingMs(),
                record.getImageHash()
        );
    }
}
