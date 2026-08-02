package io.github.anjali.notifyflow.management.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RenderTemplateResponse {
    private final String subject;
    private final String body;
}
