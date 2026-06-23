package com.uireview.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SuggestionDto(
    @NotNull
    @Pattern(regexp = "Critical|Warning|Suggestion")
    @JsonProperty("severity")
    String severity,

    @NotBlank
    @JsonProperty("title")
    String title,

    @NotBlank
    @JsonProperty("description")
    String description,

    @NotBlank
    @JsonProperty("recommendation")
    String recommendation
) {}
