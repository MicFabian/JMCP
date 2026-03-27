package com.example.javamcp.tools;

import com.example.javamcp.analysis.RuleEngineService;
import com.example.javamcp.model.AnalyzeResponse;
import com.example.javamcp.model.LibraryDoc;
import com.example.javamcp.model.LibraryDocsResponse;
import com.example.javamcp.model.MigrationAssistantRequest;
import com.example.javamcp.model.MigrationAssistantResponse;
import com.example.javamcp.model.MigrationFinding;
import com.example.javamcp.model.MigrationReference;
import com.example.javamcp.model.RuleIssue;
import com.example.javamcp.search.SearchMode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MigrationAssistantService {

    private static final int DEFAULT_TARGET_JAVA = 25;
    private static final String DEFAULT_TARGET_SPRING_BOOT = "4.0.0";
    private static final double DEFAULT_ALPHA = 0.65d;

    private static final Pattern JAVA_X_OF = Pattern.compile("JavaLanguageVersion\\.of\\((\\d{1,2})\\)");
    private static final Pattern JAVA_VERSION_CONSTANT = Pattern.compile("JavaVersion\\.VERSION_(\\d{1,2})");
    private static final Pattern JAVA_COMPATIBILITY_NUMBER = Pattern.compile("(?:source|target)Compatibility\\s*=?\\s*['\"]?(\\d{1,2})['\"]?");
    private static final Pattern JAVA_RELEASE = Pattern.compile("(?:options\\.release|--release)\\s*[=:]\\s*(\\d{1,2})");
    private static final Pattern JAVA_MAVEN_TAG = Pattern.compile("<(?:maven\\.compiler\\.(?:source|target|release)|java\\.version)>(\\d{1,2})<");

    private static final Pattern SPRING_BOOT_PLUGIN_GROOVY = Pattern.compile("id\\s*['\"]org\\.springframework\\.boot['\"]\\s*version\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern SPRING_BOOT_PLUGIN_KTS = Pattern.compile("id\\(\"org\\.springframework\\.boot\"\\)\\s*version\\s*\"([^\"]+)\"");
    private static final Pattern SPRING_BOOT_VERSION_VAR = Pattern.compile("springBootVersion\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern SPRING_BOOT_MAVEN_PARENT = Pattern.compile("<artifactId>spring-boot-starter-parent</artifactId>\\s*<version>([^<]+)</version>");
    private static final Pattern SPRING_BOOT_MAVEN_PROPERTY = Pattern.compile("<spring-boot\\.version>([^<]+)</spring-boot\\.version>");

    private static final Pattern JAVAX_IMPORT = Pattern.compile("\\bjavax\\.");
    private static final Pattern WEB_SECURITY_CONFIGURER_ADAPTER = Pattern.compile("\\bWebSecurityConfigurerAdapter\\b");
    private static final Pattern ANT_MATCHERS = Pattern.compile("\\.antMatchers\\s*\\(");
    private static final Pattern BLOCKING_REACTOR_CALL = Pattern.compile("\\.block\\s*\\(");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final RuleEngineService ruleEngineService;
    private final LibraryToolsService libraryToolsService;
    private final MeterRegistry meterRegistry;

    public MigrationAssistantService(RuleEngineService ruleEngineService,
                                     LibraryToolsService libraryToolsService,
                                     MeterRegistry meterRegistry) {
        this.ruleEngineService = ruleEngineService;
        this.libraryToolsService = libraryToolsService;
        this.meterRegistry = meterRegistry;
    }

    public MigrationAssistantResponse assess(MigrationAssistantRequest request) {
        return withMetrics("migration-assistant", () -> assessInternal(request));
    }

    private MigrationAssistantResponse assessInternal(MigrationAssistantRequest request) {
        MigrationAssistantRequest safeRequest = request == null
                ? new MigrationAssistantRequest(null, null, null, null, null, null)
                : request;

        String buildFile = normalize(safeRequest.buildFile());
        String buildFilePath = normalize(safeRequest.buildFilePath());
        String code = safeRequest.code() == null ? "" : safeRequest.code();

        if (buildFile.isBlank() && code.isBlank()) {
            throw new IllegalArgumentException("Either buildFile or code must be provided");
        }

        int targetJava = safeRequest.targetJavaVersion() == null
                ? DEFAULT_TARGET_JAVA
                : Math.max(8, safeRequest.targetJavaVersion());
        String targetBoot = normalize(safeRequest.targetSpringBootVersion()).isBlank()
                ? DEFAULT_TARGET_SPRING_BOOT
                : normalize(safeRequest.targetSpringBootVersion());
        boolean includeDocs = safeRequest.includeDocs() == null || safeRequest.includeDocs();

        String buildTool = detectBuildTool(buildFilePath, buildFile);
        String detectedJava = detectJavaVersion(buildFile);
        String detectedBoot = detectSpringBootVersion(buildFile);

        List<MigrationFinding> findings = new ArrayList<>();
        addVersionFindings(findings, detectedJava, targetJava, detectedBoot, targetBoot);
        addCodeFindings(findings, code);
        addRuleFindings(findings, code);

        List<String> actions = findings.stream()
                .map(MigrationFinding::recommendation)
                .filter(recommendation -> recommendation != null && !recommendation.isBlank())
                .distinct()
                .toList();

        List<MigrationReference> references = includeDocs
                ? collectReferences(targetJava, targetBoot)
                : List.of();

        return new MigrationAssistantResponse(
                buildTool,
                blankToUnknown(detectedJava),
                String.valueOf(targetJava),
                blankToUnknown(detectedBoot),
                targetBoot,
                findings.size(),
                List.copyOf(findings),
                actions,
                references
        );
    }

    private void addVersionFindings(List<MigrationFinding> findings,
                                    String detectedJava,
                                    int targetJava,
                                    String detectedBoot,
                                    String targetBoot) {
        Integer detectedJavaInt = parseInteger(detectedJava);
        if (detectedJavaInt == null) {
            findings.add(new MigrationFinding(
                    "java-version-unknown",
                    "MEDIUM",
                    "Could not detect Java target version from the build file.",
                    "Set the Gradle toolchain to Java " + targetJava + " with JavaLanguageVersion.of(" + targetJava + ").",
                    null,
                    String.valueOf(targetJava)
            ));
        } else if (detectedJavaInt < targetJava) {
            findings.add(new MigrationFinding(
                    "java-version-upgrade-required",
                    "HIGH",
                    "Project targets Java " + detectedJavaInt + ", but target is Java " + targetJava + ".",
                    "Upgrade toolchain and compiler release to Java " + targetJava + ".",
                    String.valueOf(detectedJavaInt),
                    String.valueOf(targetJava)
            ));
        }

        Integer detectedBootMajor = parseVersionMajor(detectedBoot);
        Integer targetBootMajor = parseVersionMajor(targetBoot);
        if (detectedBoot == null || detectedBoot.isBlank()) {
            findings.add(new MigrationFinding(
                    "spring-boot-version-unknown",
                    "MEDIUM",
                    "Could not detect Spring Boot plugin version from the build file.",
                    "Pin org.springframework.boot plugin to version " + targetBoot + ".",
                    null,
                    targetBoot
            ));
            return;
        }

        if (detectedBootMajor != null && targetBootMajor != null && detectedBootMajor < targetBootMajor) {
            findings.add(new MigrationFinding(
                    "spring-boot-major-upgrade-required",
                    "HIGH",
                    "Project uses Spring Boot " + detectedBoot + ", target is " + targetBoot + ".",
                    "Upgrade Spring Boot plugin and validate Jakarta/Security migration changes.",
                    detectedBoot,
                    targetBoot
            ));
            return;
        }

        if (compareVersionTokens(detectedBoot, targetBoot) < 0) {
            findings.add(new MigrationFinding(
                    "spring-boot-minor-upgrade-recommended",
                    "LOW",
                    "Project is below preferred Spring Boot baseline " + targetBoot + ".",
                    "Upgrade to Spring Boot " + targetBoot + " and run full regression tests.",
                    detectedBoot,
                    targetBoot
            ));
        }
    }

    private void addCodeFindings(List<MigrationFinding> findings, String code) {
        if (code == null || code.isBlank()) {
            return;
        }

        if (JAVAX_IMPORT.matcher(code).find()) {
            findings.add(new MigrationFinding(
                    "jakarta-namespace-migration",
                    "MEDIUM",
                    "Detected javax.* imports. Spring Boot 4 stack is Jakarta-first.",
                    "Replace javax.* imports with jakarta.* equivalents.",
                    "javax.*",
                    "jakarta.*"
            ));
        }

        if (WEB_SECURITY_CONFIGURER_ADAPTER.matcher(code).find()) {
            findings.add(new MigrationFinding(
                    "legacy-security-configurer",
                    "HIGH",
                    "Detected WebSecurityConfigurerAdapter, which is deprecated/removed in modern Spring Security.",
                    "Define a SecurityFilterChain bean and switch to component-based security config.",
                    "WebSecurityConfigurerAdapter",
                    "SecurityFilterChain @Bean"
            ));
        }

        if (ANT_MATCHERS.matcher(code).find()) {
            findings.add(new MigrationFinding(
                    "legacy-ant-matchers",
                    "HIGH",
                    "Detected antMatchers usage in security config.",
                    "Replace antMatchers with requestMatchers in HttpSecurity DSL.",
                    "antMatchers(...)",
                    "requestMatchers(...)"
            ));
        }

        if (BLOCKING_REACTOR_CALL.matcher(code).find()) {
            findings.add(new MigrationFinding(
                    "reactive-blocking-call",
                    "MEDIUM",
                    "Detected blocking Reactor call (.block()) in code.",
                    "Avoid blocking in reactive paths; compose using map/flatMap and return Mono/Flux.",
                    ".block()",
                    "non-blocking operator chain"
            ));
        }
    }

    private void addRuleFindings(List<MigrationFinding> findings, String code) {
        if (code == null || code.isBlank()) {
            return;
        }

        AnalyzeResponse analysis;
        try {
            analysis = ruleEngineService.analyze("Snippet.java", code);
        } catch (Exception ignored) {
            return;
        }

        Set<String> existingCodes = findings.stream()
                .map(MigrationFinding::code)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        for (RuleIssue issue : analysis.issues()) {
            String codeKey = "rule-" + normalize(issue.rule()).toLowerCase(Locale.ROOT);
            if (existingCodes.contains(codeKey)) {
                continue;
            }
            findings.add(new MigrationFinding(
                    codeKey,
                    normalizeSeverity(issue.severity()),
                    issue.message(),
                    normalize(issue.suggestion()).isBlank() ? "Apply recommended refactor for rule " + issue.rule() + "." : issue.suggestion(),
                    issue.rule(),
                    null
            ));
            existingCodes.add(codeKey);
        }
    }

    private List<MigrationReference> collectReferences(int targetJava, String targetBoot) {
        List<MigrationReference> references = new ArrayList<>();
        addReferences(references, "/spring-projects/spring-framework", "jakarta migration constructor injection", null);
        addReferences(references, "/spring-projects/spring-security", "security csrf requestMatchers migration", null);
        addReferences(references, "/openjdk/jdk", "java " + targetJava + " virtual threads structured concurrency", String.valueOf(targetJava));
        addReferences(references, "/spring-projects/spring-boot", "spring boot migration guide " + targetBoot, targetBoot);

        Map<String, MigrationReference> unique = new LinkedHashMap<>();
        for (MigrationReference reference : references) {
            String key = normalize(reference.sourceUrl()).toLowerCase(Locale.ROOT);
            if (key.isBlank()) {
                key = reference.libraryId() + "|" + reference.title();
            }
            unique.putIfAbsent(key, reference);
        }

        return unique.values().stream()
                .sorted((left, right) -> Float.compare(right.score(), left.score()))
                .limit(8)
                .toList();
    }

    private void addReferences(List<MigrationReference> references,
                               String libraryId,
                               String query,
                               String version) {
        try {
            LibraryDocsResponse docs = libraryToolsService.queryDocs(
                    libraryId,
                    query,
                    2_000,
                    3,
                    version,
                    SearchMode.HYBRID,
                    DEFAULT_ALPHA
            );
            for (LibraryDoc doc : docs.documents()) {
                references.add(new MigrationReference(
                        libraryId,
                        doc.title(),
                        doc.sourceUrl(),
                        doc.version(),
                        doc.score()
                ));
            }
        } catch (Exception ignored) {
            // Best-effort enrichment for migration references.
        }
    }

    private String detectBuildTool(String buildFilePath, String buildFile) {
        String lowerPath = normalize(buildFilePath).toLowerCase(Locale.ROOT);
        String lowerBuild = normalize(buildFile).toLowerCase(Locale.ROOT);

        if (lowerPath.endsWith("build.gradle.kts")) {
            return "GRADLE_KOTLIN";
        }
        if (lowerPath.endsWith("build.gradle")) {
            return "GRADLE_GROOVY";
        }
        if (lowerPath.endsWith("pom.xml")) {
            return "MAVEN";
        }
        if (lowerBuild.contains("<project") && lowerBuild.contains("</project>")) {
            return "MAVEN";
        }
        if (lowerBuild.contains("plugins {") || lowerBuild.contains("dependencies {")) {
            return "GRADLE";
        }
        return "UNKNOWN";
    }

    private String detectJavaVersion(String buildFile) {
        if (buildFile == null || buildFile.isBlank()) {
            return "";
        }
        for (Pattern pattern : List.of(
                JAVA_X_OF,
                JAVA_VERSION_CONSTANT,
                JAVA_COMPATIBILITY_NUMBER,
                JAVA_RELEASE,
                JAVA_MAVEN_TAG
        )) {
            String value = findFirstGroup(buildFile, pattern);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String detectSpringBootVersion(String buildFile) {
        if (buildFile == null || buildFile.isBlank()) {
            return "";
        }
        for (Pattern pattern : List.of(
                SPRING_BOOT_PLUGIN_GROOVY,
                SPRING_BOOT_PLUGIN_KTS,
                SPRING_BOOT_VERSION_VAR,
                SPRING_BOOT_MAVEN_PARENT,
                SPRING_BOOT_MAVEN_PROPERTY
        )) {
            String value = findFirstGroup(buildFile, pattern);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String findFirstGroup(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            return "";
        }
        return normalize(matcher.group(1));
    }

    private Integer parseVersionMajor(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        String[] parts = version.split("\\.");
        if (parts.length == 0) {
            return null;
        }
        return parseInteger(parts[0]);
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digitsOnly = value.replaceAll("[^0-9]", "");
        if (digitsOnly.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(digitsOnly);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int compareVersionTokens(String left, String right) {
        List<Integer> leftTokens = versionTokens(left);
        List<Integer> rightTokens = versionTokens(right);
        int max = Math.max(leftTokens.size(), rightTokens.size());
        for (int i = 0; i < max; i++) {
            int leftValue = i < leftTokens.size() ? leftTokens.get(i) : 0;
            int rightValue = i < rightTokens.size() ? rightTokens.get(i) : 0;
            if (leftValue < rightValue) {
                return -1;
            }
            if (leftValue > rightValue) {
                return 1;
            }
        }
        return 0;
    }

    private List<Integer> versionTokens(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        Matcher matcher = DIGITS.matcher(value);
        List<Integer> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(Integer.parseInt(matcher.group()));
        }
        return tokens;
    }

    private String normalizeSeverity(String severity) {
        String normalized = normalize(severity).toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "MEDIUM";
        }
        return switch (normalized) {
            case "CRITICAL", "BLOCKER", "HIGH", "MEDIUM", "LOW", "INFO" -> normalized;
            default -> "MEDIUM";
        };
    }

    private String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private <T> T withMetrics(String operation, Supplier<T> supplier) {
        long started = System.nanoTime();
        String status = "ok";
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            status = "error";
            throw ex;
        } finally {
            meterRegistry.counter("mcp.server.tool.calls", "mcp.method.name", operation, "mcp.status", status)
                    .increment();
            Timer.builder("mcp.server.operation.duration")
                    .tag("mcp.method.name", operation)
                    .tag("mcp.status", status)
                    .register(meterRegistry)
                    .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }
}
