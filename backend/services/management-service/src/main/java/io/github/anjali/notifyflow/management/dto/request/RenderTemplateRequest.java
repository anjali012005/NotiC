package io.github.anjali.notifyflow.management.dto.request;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RenderTemplateRequest {

    @NotNull(message = "Variables are required")
    private Map<String, String> variables;
}
