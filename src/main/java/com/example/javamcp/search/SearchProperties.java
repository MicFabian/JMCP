package com.example.javamcp.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "mcp.search")
public class SearchProperties {

    private int defaultLimit = 10;
    private int maxLimit = 50;
    private int candidateMultiplier = 3;
    private int rrfK = 60;
    private double lexicalWeight = 1.0;
    private double vectorWeight = 0.9;
    private double exactVersionBoost = 0.20;
    private Map<String, List<String>> synonyms = Map.of(
            "csrf", List.of("cross site request forgery", "xsrf"),
            "db", List.of("database"),
            "jpa", List.of("hibernate", "entity manager")
    );

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(int maxLimit) {
        this.maxLimit = maxLimit;
    }

    public int getCandidateMultiplier() {
        return candidateMultiplier;
    }

    public void setCandidateMultiplier(int candidateMultiplier) {
        this.candidateMultiplier = candidateMultiplier;
    }

    public int getRrfK() {
        return rrfK;
    }

    public void setRrfK(int rrfK) {
        this.rrfK = rrfK;
    }

    public double getLexicalWeight() {
        return lexicalWeight;
    }

    public void setLexicalWeight(double lexicalWeight) {
        this.lexicalWeight = lexicalWeight;
    }

    public double getVectorWeight() {
        return vectorWeight;
    }

    public void setVectorWeight(double vectorWeight) {
        this.vectorWeight = vectorWeight;
    }

    public double getExactVersionBoost() {
        return exactVersionBoost;
    }

    public void setExactVersionBoost(double exactVersionBoost) {
        this.exactVersionBoost = exactVersionBoost;
    }

    public Map<String, List<String>> getSynonyms() {
        return synonyms;
    }

    public void setSynonyms(Map<String, List<String>> synonyms) {
        this.synonyms = synonyms == null ? Map.of() : synonyms;
    }
}
