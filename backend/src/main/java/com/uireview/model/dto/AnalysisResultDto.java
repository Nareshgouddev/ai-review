package com.uireview.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AnalysisResultDto(
    @NotBlank
    @JsonProperty("id")
    String id,

    @JsonProperty("sessionId")
    String sessionId,

    @Min(0) @Max(100)
    @JsonProperty("overallScore")
    int overallScore,

    @JsonProperty("processingMs")
    int processingMs,

    @JsonProperty("cached")
    boolean cached,

    @NotNull
    @Valid
    @JsonProperty("categories")
    List<CategoryDto> categories
) {}
