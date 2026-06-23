package com.uireview.controller;

import com.uireview.model.dto.AnalysisResultDto;
import com.uireview.model.dto.AnalysisSummaryDto;
import com.uireview.service.AnalysisService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for the UI Review analysis endpoints.
 *
 * <p>All paths are prefixed with {@code /api/v1} per the API contract.
 * Validation and orchestration are delegated to {@link AnalysisService}.
 * Error responses (400, 404, 422, 429, 500, 503) are produced by
 * {@link com.uireview.exception.GlobalExceptionHandler}.
 *
 * Requirements: 3.1, 7.1, 7.5, 7.6
 */
@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/analyze
    // -------------------------------------------------------------------------

    /**
     * Accepts a multipart image upload, validates it, runs the AI analysis
     * pipeline (with 24-hour hash cache), and returns the structured result.
     *
     * @param file       the uploaded image file (required)
     * @param focusHint  optional text directing the AI analysis
     * @param sessionId  optional browser session identifier for history grouping
     * @param request    used to resolve the client IP address
     * @return 200 with {@link AnalysisResultDto}
     */
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalysisResultDto> analyze(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String focusHint,
            @RequestParam(required = false) String sessionId,
            HttpServletRequest request) {

        String ipAddress = resolveClientIp(request);
        AnalysisResultDto result = analysisService.analyze(file, focusHint, sessionId, ipAddress);
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/history
    // -------------------------------------------------------------------------

    /**
     * Returns the most recent analyses for the given session.
     *
     * @param sessionId optional session filter
     * @param limit     max records to return (null → default 5, 0 → empty)
     * @return 200 with list of {@link AnalysisSummaryDto}
     */
    @GetMapping("/history")
    public ResponseEntity<List<AnalysisSummaryDto>> getHistory(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Integer limit) {

        return ResponseEntity.ok(analysisService.getHistory(sessionId, limit));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/analysis/{id}
    // -------------------------------------------------------------------------

    /**
     * Returns a single analysis record by UUID.
     *
     * @param id the UUID of the analysis record
     * @return 200 with {@link AnalysisResultDto}, or 404 if not found
     */
    @GetMapping("/analysis/{id}")
    public ResponseEntity<AnalysisResultDto> getAnalysis(@PathVariable String id) {
        return analysisService.getAnalysisById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/analysis/{id}
    // -------------------------------------------------------------------------

    /**
     * Deletes a single analysis record by UUID.
     * Returns 404 (via {@link com.uireview.exception.GlobalExceptionHandler})
     * when no record with the given id exists.
     *
     * @param id the UUID of the analysis record to delete
     * @return 204 No Content on success
     */
    @DeleteMapping("/analysis/{id}")
    public ResponseEntity<Void> deleteAnalysis(@PathVariable String id) {
        analysisService.deleteAnalysis(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the true client IP, honouring the {@code X-Forwarded-For} proxy
     * header when present (takes the first entry, which is the original client).
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
