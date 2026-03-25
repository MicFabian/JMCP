package com.example.javamcp.analysis;

import com.example.javamcp.model.AstClass;
import com.example.javamcp.model.AstMethod;
import com.example.javamcp.model.AstResponse;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AstService {

    public AstResponse parse(String code) {
        CompilationUnit compilationUnit = StaticJavaParser.parse(code);

        List<AstClass> classes = compilationUnit.findAll(ClassOrInterfaceDeclaration.class)
                .stream()
                .map(type -> new AstClass(
                        type.getNameAsString(),
                        compilationUnit.getPackageDeclaration()
                                .map(pkg -> pkg.getName().asString())
                                .orElse(""),
                        toMethods(type.getMethods())
                ))
                .toList();

        return new AstResponse(classes.size(), classes);
    }

    private List<AstMethod> toMethods(List<MethodDeclaration> methods) {
        return methods.stream()
                .map(method -> new AstMethod(
                        method.getNameAsString(),
                        method.getDeclarationAsString(false, false, false),
                        method.getTypeAsString(),
                        method.getJavadoc().map(j -> j.toText()).orElse("")
                ))
                .toList();
    }
}
