package com.uireview.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CategoryDto(
    @NotBlank
    @JsonProperty("name")
    String name,

    @Min(0) @Max(100)
    @JsonProperty("score")
    int score,

    @JsonProperty("weight")
    double weight,

    @NotNull
    @Valid
    @JsonProperty("suggestions")
    List<SuggestionDto> suggestions
) {}
