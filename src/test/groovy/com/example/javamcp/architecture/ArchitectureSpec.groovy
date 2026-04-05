package com.example.javamcp.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import spock.lang.Shared
import spock.lang.Specification

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

class ArchitectureSpec extends Specification {

    @Shared
    JavaClasses importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption({ location -> !location.contains('/generated/') } as ImportOption)
            .importPackages('com.example.javamcp')

    def 'top-level packages should be free of cycles'() {
        expect:
        slices()
                .matching('com.example.javamcp.(*)..')
                .should()
                .beFreeOfCycles()
                .check(importedClasses)
    }

    def 'core services should not depend on transport adapters'() {
        expect:
        noClasses()
                .that().resideInAnyPackage('..analysis..', '..ingest..', '..observability..', '..search..', '..tools..')
                .should().dependOnClassesThat().resideInAnyPackage('..api..', '..graphql..', '..grpc..', '..mcp..')
                .check(importedClasses)
    }

    def 'transport adapters should not depend on each other'() {
        expect:
        noClasses().that().resideInAPackage('..api..')
                .should().dependOnClassesThat().resideInAnyPackage('..graphql..', '..grpc..', '..mcp..')
                .check(importedClasses)
        noClasses().that().resideInAPackage('..graphql..')
                .should().dependOnClassesThat().resideInAnyPackage('..api..', '..grpc..', '..mcp..')
                .check(importedClasses)
        noClasses().that().resideInAPackage('..grpc..')
                .should().dependOnClassesThat().resideInAnyPackage('..api..', '..graphql..', '..mcp..')
                .check(importedClasses)
        noClasses().that().resideInAPackage('..mcp..')
                .should().dependOnClassesThat().resideInAnyPackage('..api..', '..graphql..', '..grpc..')
                .check(importedClasses)
    }

    def 'rest controllers and advice should stay in the api adapter'() {
        expect:
        classes()
                .that().areAnnotatedWith(RestController)
                .or().areAnnotatedWith(RestControllerAdvice)
                .should().resideInAnyPackage('..api..')
                .check(importedClasses)
    }
}
