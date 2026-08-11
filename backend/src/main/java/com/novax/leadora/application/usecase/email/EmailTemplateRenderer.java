package com.novax.leadora.application.usecase.email;

import java.util.Map;

public interface EmailTemplateRenderer {
    String render(String templateName, Map<String, Object> model);
}
