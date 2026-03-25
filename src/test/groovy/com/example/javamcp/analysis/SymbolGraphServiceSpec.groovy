package com.example.javamcp.analysis

import spock.lang.Specification

class SymbolGraphServiceSpec extends Specification {

    private final SymbolGraphService service = new SymbolGraphService()

    def 'should extract class method field and call edges'() {
        given:
        String code = '''
                package demo;

                class UserService extends BaseService {
                    private Repo repo;

                    void run() {
                        repo.findAll();
                    }
                }
                '''

        when:
        def graph = service.extract(code)

        then:
        graph.nodeCount() > 0
        graph.edgeCount() > 0
        graph.nodes().any { it.type() == 'Class' && it.name() == 'UserService' }
        graph.edges().any { it.relation() == 'declaresMethod' }
        graph.edges().any { it.relation() == 'calls' }
    }
}
