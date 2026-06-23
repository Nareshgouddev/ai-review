package com.uireview.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "analysis_records",
    indexes = {
        @Index(name = "idx_session_id", columnList = "session_id"),
        @Index(name = "idx_image_hash", columnList = "image_hash")
    }
)
public class AnalysisRecord {

    @Id
    private String id;                                              // UUID v4, VARCHAR(36)

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "overall_score", nullable = false)
    private int overallScore;                                       // TINYINT 0-100

    @Column(name = "processing_ms", nullable = false)
    private int processingMs;

    @Column(name = "image_hash", length = 64)
    private String imageHash;                                       // SHA-256 hex, nullable

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "raw_response", nullable = false)
    private String rawResponse;                                     // LONGTEXT

    @Column(name = "focus_hint", length = 256)
    private String focusHint;                                       // nullable

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;                                       // IPv4 or IPv6

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // -------------------------------------------------------------------------
    // No-args constructor (required by JPA)
    // -------------------------------------------------------------------------

    public AnalysisRecord() {}

    // -------------------------------------------------------------------------
    // Full-args constructor
    // -------------------------------------------------------------------------

    public AnalysisRecord(
            String id,
            String sessionId,
            LocalDateTime createdAt,
            int overallScore,
            int processingMs,
            String imageHash,
            String rawResponse,
            String focusHint,
            String ipAddress) {
        this.id = id;
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.overallScore = overallScore;
        this.processingMs = processingMs;
        this.imageHash = imageHash;
        this.rawResponse = rawResponse;
        this.focusHint = focusHint;
        this.ipAddress = ipAddress;
    }

    // -------------------------------------------------------------------------
    // Getters and Setters
    // -------------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(int overallScore) {
        this.overallScore = overallScore;
    }

    public int getProcessingMs() {
        return processingMs;
    }

    public void setProcessingMs(int processingMs) {
        this.processingMs = processingMs;
    }

    public String getImageHash() {
        return imageHash;
    }

    public void setImageHash(String imageHash) {
        this.imageHash = imageHash;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public String getFocusHint() {
        return focusHint;
    }

    public void setFocusHint(String focusHint) {
        this.focusHint = focusHint;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String sessionId;
        private LocalDateTime createdAt;
        private int overallScore;
        private int processingMs;
        private String imageHash;
        private String rawResponse;
        private String focusHint;
        private String ipAddress;

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder overallScore(int overallScore) {
            this.overallScore = overallScore;
            return this;
        }

        public Builder processingMs(int processingMs) {
            this.processingMs = processingMs;
            return this;
        }

        public Builder imageHash(String imageHash) {
            this.imageHash = imageHash;
            return this;
        }

        public Builder rawResponse(String rawResponse) {
            this.rawResponse = rawResponse;
            return this;
        }

        public Builder focusHint(String focusHint) {
            this.focusHint = focusHint;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public AnalysisRecord build() {
            return new AnalysisRecord(
                    id, sessionId, createdAt,
                    overallScore, processingMs,
                    imageHash, rawResponse,
                    focusHint, ipAddress);
        }
    }
}
