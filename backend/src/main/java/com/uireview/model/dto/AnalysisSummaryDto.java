package com.uireview.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record AnalysisSummaryDto(
    @NotBlank
    @JsonProperty("id")
    String id,

    @JsonProperty("sessionId")
    String sessionId,

    @JsonProperty("createdAt")
    LocalDateTime createdAt,

    @Min(0) @Max(100)
    @JsonProperty("overallScore")
    int overallScore,

    @JsonProperty("processingMs")
    int processingMs,

    @JsonProperty("imageHash")
    String imageHash
) {}
