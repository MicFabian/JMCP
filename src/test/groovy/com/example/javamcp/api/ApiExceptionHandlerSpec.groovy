package com.example.javamcp.api

import com.example.javamcp.model.AnalyzeRequest
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ApiExceptionHandlerSpec extends Specification {

    def 'should enrich problem details for validation failures'() {
        given:
        def fixedClock = Clock.fixed(Instant.parse('2026-04-05T12:00:00Z'), ZoneOffset.UTC)
        def handler = new ApiExceptionHandler(fixedClock)
        def target = new AnalyzeRequest('Demo.java', '')
        def bindingResult = new BeanPropertyBindingResult(target, 'analyzeRequest')
        bindingResult.addError(new FieldError('analyzeRequest', 'code', '', false, null, null, 'must not be blank'))
        def method = SampleController.getDeclaredMethod('analyze', AnalyzeRequest)
        def parameter = new MethodParameter(method, 0)
        def exception = new MethodArgumentNotValidException(parameter, bindingResult)
        def request = new MockHttpServletRequest('POST', '/api/analyze')

        when:
        def problem = handler.onValidation(exception, request)

        then:
        problem.title == 'Validation failed'
        problem.status == 400
        problem.detail == 'Invalid request payload'
        problem.instance.toString() == '/api/analyze'
        problem.properties.path == '/api/analyze'
        problem.properties.timestamp == '2026-04-05T12:00:00Z'
        ((List<Map<String, Object>>) problem.properties.errors).first().field == 'code'
        ((List<Map<String, Object>>) problem.properties.errors).first().message == 'must not be blank'
    }

    private static final class SampleController {
        void analyze(AnalyzeRequest request) {
        }
    }
}
