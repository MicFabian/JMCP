package com.example.javamcp.analysis;

import com.example.javamcp.model.AnalyzeResponse;
import com.example.javamcp.model.RuleIssue;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RuleEngineService {

    private final List<RuleDefinition> defaultRules;

    public RuleEngineService(RuleLoader ruleLoader) {
        this.defaultRules = List.copyOf(ruleLoader.loadDefaultRules());
    }

    public List<RuleDefinition> listRules() {
        return defaultRules.stream()
                .filter(rule -> Boolean.TRUE.equals(rule.enabled()))
                .toList();
    }

    public AnalyzeResponse analyze(String fileName, String code) {
        String effectiveFile = (fileName == null || fileName.isBlank()) ? "InlineSnippet.java" : fileName;
        CompilationUnit compilationUnit = StaticJavaParser.parse(code);

        List<RuleIssue> issues = new ArrayList<>();
        for (RuleDefinition rule : listRules()) {
            switch (rule.matchType()) {
                case AST_METHOD_CALL -> detectMethodCall(compilationUnit, rule, issues);
                case AST_FIELD_ANNOTATION -> detectFieldAnnotation(compilationUnit, rule, issues);
                case REGEX -> detectRegex(rule, code, issues);
                case CONTAINS -> detectContains(rule, code, issues);
            }
        }

        List<RuleIssue> deduplicated = deduplicate(issues);
        deduplicated.sort(Comparator
                .comparingInt((RuleIssue issue) -> severityRank(issue.severity()))
                .reversed()
                .thenComparingInt(RuleIssue::line));

        return new AnalyzeResponse(effectiveFile, deduplicated.size(), deduplicated);
    }

    private void detectMethodCall(CompilationUnit compilationUnit, RuleDefinition rule, List<RuleIssue> issues) {
        String expected = normalizePattern(rule.pattern());
        compilationUnit.findAll(MethodCallExpr.class).forEach(callExpr -> {
            String renderedCall = callExpr.toString();
            String callName = callExpr.getNameAsString();
            if (matchesMethodPattern(expected, renderedCall, callName)) {
                int line = callExpr.getRange().map(range -> range.begin.line).orElse(-1);
                issues.add(toIssue(rule, line));
            }
        });
    }

    private boolean matchesMethodPattern(String expected, String renderedCall, String callName) {
        if (expected.isBlank()) {
            return false;
        }

        String normalizedCall = renderedCall.toLowerCase(Locale.ROOT);
        String normalizedName = callName.toLowerCase(Locale.ROOT);
        if (normalizedCall.startsWith(expected)) {
            return true;
        }

        if (expected.contains(".")) {
            int lastDot = expected.lastIndexOf('.');
            return normalizedName.equals(expected.substring(lastDot + 1));
        }

        return normalizedName.equals(expected);
    }

    private void detectFieldAnnotation(CompilationUnit compilationUnit, RuleDefinition rule, List<RuleIssue> issues) {
        String expectedAnnotation = normalizeAnnotation(rule.pattern());
        compilationUnit.findAll(FieldDeclaration.class).forEach(field -> {
            boolean hasAnnotation = field.getAnnotations().stream()
                    .anyMatch(annotationExpr -> expectedAnnotation.equals(annotationExpr.getNameAsString().toLowerCase(Locale.ROOT)));
            if (hasAnnotation) {
                int line = field.getRange().map(range -> range.begin.line).orElse(-1);
                issues.add(toIssue(rule, line));
            }
        });
    }

    private void detectRegex(RuleDefinition rule, String code, List<RuleIssue> issues) {
        String regex = rule.pattern();
        if (regex == null || regex.isBlank()) {
            return;
        }

        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        var matcher = pattern.matcher(code);
        while (matcher.find()) {
            int line = lineAt(code, matcher.start());
            issues.add(toIssue(rule, line));
        }
    }

    private void detectContains(RuleDefinition rule, String code, List<RuleIssue> issues) {
        String pattern = rule.pattern();
        if (pattern == null || pattern.isBlank()) {
            return;
        }

        int fromIndex = 0;
        while (fromIndex < code.length()) {
            int matchIndex = code.indexOf(pattern, fromIndex);
            if (matchIndex < 0) {
                return;
            }
            issues.add(toIssue(rule, lineAt(code, matchIndex)));
            fromIndex = matchIndex + pattern.length();
        }
    }

    private List<RuleIssue> deduplicate(List<RuleIssue> issues) {
        Set<String> seen = new LinkedHashSet<>();
        List<RuleIssue> deduplicated = new ArrayList<>();
        for (RuleIssue issue : issues) {
            String key = issue.rule() + "|" + issue.line() + "|" + issue.message();
            if (seen.add(key)) {
                deduplicated.add(issue);
            }
        }
        return deduplicated;
    }

    private RuleIssue toIssue(RuleDefinition rule, int line) {
        return new RuleIssue(
                rule.id(),
                line,
                defaultSeverity(rule),
                rule.description(),
                rule.fix()
        );
    }

    private String defaultSeverity(RuleDefinition rule) {
        return (rule.severity() == null || rule.severity().isBlank()) ? "MEDIUM" : rule.severity();
    }

    private int lineAt(String code, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (code.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private int severityRank(String severity) {
        if (severity == null) {
            return 2;
        }
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 2;
        };
    }

    private String normalizePattern(String pattern) {
        return pattern == null ? "" : pattern.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAnnotation(String pattern) {
        return normalizePattern(pattern).replace("@", "");
    }
}
