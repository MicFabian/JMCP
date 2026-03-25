package com.example.javamcp.analysis

import spock.lang.Specification

class AstServiceSpec extends Specification {

    private final AstService astService = new AstService()

    def 'should parse class and methods'() {
        given:
        String code = '''
                package demo;

                public class DemoService {
                    /** Returns greeting */
                    public String hello() {
                        return "hi";
                    }
                }
                '''

        when:
        def response = astService.parse(code)

        then:
        response.classCount() == 1
        response.classes().size() == 1
        response.classes().first().name() == 'DemoService'
        response.classes().first().methods().size() == 1
        response.classes().first().methods().first().name() == 'hello'
    }
}
