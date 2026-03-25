package com.example.javamcp.analysis;

import com.example.javamcp.model.SymbolEdge;
import com.example.javamcp.model.SymbolGraphResponse;
import com.example.javamcp.model.SymbolNode;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SymbolGraphService {

    public SymbolGraphResponse extract(String code) {
        CompilationUnit compilationUnit = StaticJavaParser.parse(code);
        String packageName = compilationUnit.getPackageDeclaration()
                .map(pkg -> pkg.getNameAsString())
                .orElse("");

        Map<String, SymbolNode> nodes = new LinkedHashMap<>();
        Set<SymbolEdge> edges = new LinkedHashSet<>();

        for (ClassOrInterfaceDeclaration type : compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
            String className = type.getNameAsString();
            String classQualifiedName = packageName.isBlank() ? className : packageName + "." + className;
            String classId = "class:" + classQualifiedName;
            nodes.put(classId, new SymbolNode(classId, "Class", className, classQualifiedName));

            addInheritance(type, classId, packageName, nodes, edges);
            addFields(type, classId, classQualifiedName, nodes, edges);
            addMethods(type, classId, classQualifiedName, nodes, edges);
        }

        return new SymbolGraphResponse(nodes.size(), edges.size(), new ArrayList<>(nodes.values()), new ArrayList<>(edges));
    }

    private void addInheritance(ClassOrInterfaceDeclaration type,
                                String classId,
                                String packageName,
                                Map<String, SymbolNode> nodes,
                                Set<SymbolEdge> edges) {
        for (ClassOrInterfaceType extended : type.getExtendedTypes()) {
            String targetName = extended.getNameAsString();
            String targetQualifiedName = packageName.isBlank() ? targetName : packageName + "." + targetName;
            String targetId = "class:" + targetQualifiedName;
            nodes.putIfAbsent(targetId, new SymbolNode(targetId, "Class", targetName, targetQualifiedName));
            edges.add(new SymbolEdge(classId, targetId, "extends"));
        }

        for (ClassOrInterfaceType implemented : type.getImplementedTypes()) {
            String targetName = implemented.getNameAsString();
            String targetQualifiedName = packageName.isBlank() ? targetName : packageName + "." + targetName;
            String targetId = "interface:" + targetQualifiedName;
            nodes.putIfAbsent(targetId, new SymbolNode(targetId, "Interface", targetName, targetQualifiedName));
            edges.add(new SymbolEdge(classId, targetId, "implements"));
        }
    }

    private void addFields(ClassOrInterfaceDeclaration type,
                           String classId,
                           String classQualifiedName,
                           Map<String, SymbolNode> nodes,
                           Set<SymbolEdge> edges) {
        for (FieldDeclaration field : type.getFields()) {
            field.getVariables().forEach(variable -> {
                String fieldName = variable.getNameAsString();
                String fieldQualifiedName = classQualifiedName + "#" + fieldName;
                String fieldId = "field:" + fieldQualifiedName;
                nodes.put(fieldId, new SymbolNode(fieldId, "Field", fieldName, fieldQualifiedName));
                edges.add(new SymbolEdge(classId, fieldId, "declaresField"));
            });
        }
    }

    private void addMethods(ClassOrInterfaceDeclaration type,
                            String classId,
                            String classQualifiedName,
                            Map<String, SymbolNode> nodes,
                            Set<SymbolEdge> edges) {
        for (MethodDeclaration method : type.getMethods()) {
            String methodName = method.getNameAsString();
            String methodQualifiedName = classQualifiedName + "#" + methodName + signatureSuffix(method);
            String methodId = "method:" + methodQualifiedName;
            nodes.put(methodId, new SymbolNode(methodId, "Method", methodName, methodQualifiedName));
            edges.add(new SymbolEdge(classId, methodId, "declaresMethod"));

            method.findAll(MethodCallExpr.class).forEach(callExpr -> {
                String calledMethodName = callExpr.getNameAsString();
                String targetId = "methodref:" + calledMethodName;
                nodes.putIfAbsent(targetId, new SymbolNode(targetId, "MethodRef", calledMethodName, calledMethodName));
                edges.add(new SymbolEdge(methodId, targetId, "calls"));
            });
        }
    }

    private String signatureSuffix(MethodDeclaration method) {
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < method.getParameters().size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(method.getParameter(i).getTypeAsString());
        }
        builder.append(')');
        return builder.toString();
    }
}
