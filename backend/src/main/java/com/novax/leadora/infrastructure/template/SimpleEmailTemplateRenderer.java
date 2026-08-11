package com.novax.leadora.infrastructure.template;

import com.novax.leadora.application.usecase.email.EmailTemplateRenderer;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class SimpleEmailTemplateRenderer implements EmailTemplateRenderer {

    @Override
    public String render(String templateContent, Map<String, Object> model) {
        if (templateContent == null) return "";
        String result = templateContent;
        for (Map.Entry<String, Object> entry : model.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
