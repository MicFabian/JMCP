package com.example.javamcp.analysis;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class RuleLoader {

    private static final String DEFAULT_RULES_PATH = "classpath:rules/default-rules.yaml";

    private final ResourceLoader resourceLoader;

    public RuleLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public List<RuleDefinition> loadDefaultRules() {
        Resource resource = resourceLoader.getResource(DEFAULT_RULES_PATH);
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

        try (InputStream inputStream = resource.getInputStream()) {
            RuleDefinition[] rules = objectMapper.readValue(inputStream, RuleDefinition[].class);
            return validate(Arrays.asList(rules));
        } catch (Exception e) {
            throw new IllegalStateException("Could not load rules from " + DEFAULT_RULES_PATH, e);
        }
    }

    private List<RuleDefinition> validate(List<RuleDefinition> rules) {
        Set<String> ids = new LinkedHashSet<>();
        for (RuleDefinition rule : rules) {
            if (rule.id() == null || rule.id().isBlank()) {
                throw new IllegalStateException("Rule id must not be blank");
            }
            if (!ids.add(rule.id())) {
                throw new IllegalStateException("Duplicate rule id: " + rule.id());
            }
        }
        return rules;
    }
}
