package com.bensopher.miniwsa.ingest;

import com.bensopher.miniwsa.common.validation.EnumValue;
import com.bensopher.miniwsa.event.Category;
import com.bensopher.miniwsa.event.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RulePayload(
        @NotBlank @Size(max = 32) String id,
        @Size(max = 128) String name,
        @Size(max = 512) String message,
        @NotNull @EnumValue(Severity.class) String severity,
        @NotNull @EnumValue(Category.class) String category) {
}
