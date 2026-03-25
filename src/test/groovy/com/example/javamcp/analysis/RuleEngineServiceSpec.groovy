package com.example.javamcp.analysis

import org.springframework.core.io.DefaultResourceLoader
import spock.lang.Specification

class RuleEngineServiceSpec extends Specification {

    def 'should report default rule violations'() {
        given:
        def ruleLoader = new RuleLoader(new DefaultResourceLoader())
        def service = new RuleEngineService(ruleLoader)

        String code = '''
                import org.springframework.beans.factory.annotation.Autowired;

                class MyService {
                    @Autowired
                    private Dependency dependency;

                    void run() {
                        System.out.println("debug");
                        Thread.sleep(10);
                    }
                }
                '''

        when:
        def response = service.analyze('MyService.java', code)

        then:
        response.issueCount() == 3
        response.issues()*.rule().toSet() == ['avoid-field-injection', 'no-system-out', 'no-thread-sleep'] as Set
    }

    def 'should expose enabled rules'() {
        given:
        def ruleLoader = new RuleLoader(new DefaultResourceLoader())
        def service = new RuleEngineService(ruleLoader)

        when:
        def rules = service.listRules()

        then:
        rules.size() >= 3
        rules*.id().containsAll(['avoid-field-injection', 'no-system-out'])
    }
}
