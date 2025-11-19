package io.github.easy.tools.service.api.annotation;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.*;
import io.github.easy.tools.entity.api.ApiInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 默认注解适配器实现
 * 处理Spring MVC、Swagger、OpenAPI等标准注解
 */
public class DefaultAnnotationAdapter implements AnnotationAdapter {
    
    @Override
    public boolean hasAnnotationRecursively(@NotNull PsiClass psiClass, String annotationFqn, Set<String> visited) {
        return ReadAction.compute(() -> {
            // 防止循环引用导致的无限递归
            String classQualifiedName = psiClass.getQualifiedName();
            if (classQualifiedName != null && visited.contains(classQualifiedName)) {
                return false;
            }
            
            // 将当前类添加到已访问集合
            if (classQualifiedName != null) {
                visited.add(classQualifiedName);
            }
            
            // 直接检查类上的注解
            if (psiClass.getAnnotation(annotationFqn) != null) {
                return true;
            }
            
            // 检查类上的所有注解，看它们是否包含目标注解作为元注解
            for (PsiAnnotation classAnnotation : psiClass.getAnnotations()) {
                PsiJavaCodeReferenceElement referenceElement = classAnnotation.getNameReferenceElement();
                if (referenceElement != null) {
                    PsiElement resolved = referenceElement.resolve();
                    if (resolved instanceof PsiClass annotationClass) {
                        // 递归检查注解类是否包含目标注解
                        if (hasAnnotationRecursively(annotationClass, annotationFqn, visited)) {
                            return true;
                        }
                    }
                }
            }
            
            return false;
        });
    }
    
    @Override
    public @Nullable PsiAnnotation findAnnotationRecursively(@NotNull PsiClass psiClass, String annotationFqn, Set<String> visited) {
        return ReadAction.compute(() -> {
            // 防止循环引用导致的无限递归
            String classQualifiedName = psiClass.getQualifiedName();
            if (classQualifiedName != null && visited.contains(classQualifiedName)) {
                return null;
            }
            
            // 将当前类添加到已访问集合
            if (classQualifiedName != null) {
                visited.add(classQualifiedName);
            }
            
            PsiAnnotation annotation = psiClass.getAnnotation(annotationFqn);
            if (annotation != null) {
                return annotation;
            }
            
            // 检查元注解
            for (PsiAnnotation classAnnotation : psiClass.getAnnotations()) {
                PsiJavaCodeReferenceElement referenceElement = classAnnotation.getNameReferenceElement();
                if (referenceElement != null) {
                    PsiElement resolved = referenceElement.resolve();
                    if (resolved instanceof PsiClass annotationClass) {
                        PsiAnnotation metaAnnotation = findAnnotationRecursively(annotationClass, annotationFqn, visited);
                        if (metaAnnotation != null) {
                            return classAnnotation;
                        }
                    }
                }
            }
            
            return null;
        });
    }
    
    @Override
    public String getApiNameFromMethod(@NotNull PsiMethod method, @NotNull PsiClass controllerClass) {
        return ReadAction.compute(() -> {
            // 检查方法上的@Operation注解
            PsiAnnotation operationAnnotation = method.getAnnotation("io.swagger.v3.oas.annotations.Operation");
            if (operationAnnotation != null) {
                String summary = getAnnotationAttributeValue(operationAnnotation, "summary");
                if (summary != null && !summary.isEmpty()) {
                    return summary;
                }
                String operationName = getAnnotationAttributeValue(operationAnnotation, "operationId");
                if (operationName != null && !operationName.isEmpty()) {
                    return operationName;
                }
            }
            
            // 检查方法上的@ApiOperation注解（Swagger 2.x）
            PsiAnnotation apiOperationAnnotation = method.getAnnotation("io.swagger.annotations.ApiOperation");
            if (apiOperationAnnotation != null) {
                String value = getAnnotationAttributeValue(apiOperationAnnotation, "value");
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
            
            return null;
        });
    }
    
    @Override
    public String getControllerDescription(@NotNull PsiClass controllerClass) {
        return ReadAction.compute(() -> {
            // 检查类上的@Tag注解
            PsiAnnotation tagAnnotation = controllerClass.getAnnotation("io.swagger.v3.oas.annotations.tags.Tag");
            if (tagAnnotation != null) {
                String name = getAnnotationAttributeValue(tagAnnotation, "name");
                if (name != null && !name.isEmpty()) {
                    return name;
                }
                String description = getAnnotationAttributeValue(tagAnnotation, "description");
                if (description != null && !description.isEmpty()) {
                    return description;
                }
            }
            
            // 检查类上的@Api注解（Swagger 2.x）
            PsiAnnotation apiAnnotation = controllerClass.getAnnotation("io.swagger.annotations.Api");
            if (apiAnnotation != null) {
                String value = getAnnotationAttributeValue(apiAnnotation, "value");
                if (value != null && !value.isEmpty()) {
                    return value;
                }
                String description = getAnnotationAttributeValue(apiAnnotation, "description");
                if (description != null && !description.isEmpty()) {
                    return description;
                }
            }
            
            // 如果没有找到注解描述，返回简单类名
            String className = controllerClass.getName();
            return className != null ? className : "Unknown Controller";
        });
    }
    
    @Override
    public String getAnnotationAttributeValue(@NotNull PsiAnnotation annotation, String... attrNames) {
        return ReadAction.compute(() -> {
            for (String attrName : attrNames) {
                PsiAnnotationMemberValue value = annotation.findAttributeValue(attrName);
                if (value != null) {
                    String text = value.getText();
                    // 正确处理路径参数，保留花括号
                    if (text.startsWith("\"") && text.endsWith("\"") && text.length() > 1) {
                        text = text.substring(1, text.length() - 1);
                    }
                    return text.trim();
                }
            }
            return "";
        });
    }
    
    @Override
    public String[] getRequestMethodFromRequestMapping(@NotNull PsiAnnotation requestMapping) {
        return ReadAction.compute(() -> {
            PsiAnnotationMemberValue methodValue = requestMapping.findAttributeValue("method");
            if (methodValue != null) {
                String text = methodValue.getText();
                // 处理多个方法的情况
                if (text.contains("RequestMethod")) {
                    // 提取枚举值，例如"RequestMethod.GET" -> "GET"
                    List<String> methods = new ArrayList<>();
                    if (text.contains("RequestMethod.GET")) methods.add("GET");
                    if (text.contains("RequestMethod.POST")) methods.add("POST");
                    if (text.contains("RequestMethod.PUT")) methods.add("PUT");
                    if (text.contains("RequestMethod.DELETE")) methods.add("DELETE");
                    if (text.contains("RequestMethod.PATCH")) methods.add("PATCH");
                    return methods.toArray(new String[0]);
                }
                return new String[]{text};
            }
            return new String[]{"GET"}; // 默认GET方法
        });
    }
}