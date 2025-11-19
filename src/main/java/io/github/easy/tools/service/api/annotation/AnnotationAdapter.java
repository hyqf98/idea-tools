package io.github.easy.tools.service.api.annotation;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import io.github.easy.tools.entity.api.ApiInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 注解适配器接口
 * 用于处理不同类型的注解，提供统一的注解处理接口
 */
public interface AnnotationAdapter {
    
    /**
     * 检查类是否具有指定注解（直接或间接通过元注解）
     *
     * @param psiClass      类
     * @param annotationFqn 注解全限定名
     * @param visited       已访问的类集合，用于防止循环引用
     * @return 是否具有该注解
     */
    boolean hasAnnotationRecursively(@NotNull PsiClass psiClass, String annotationFqn, Set<String> visited);
    
    /**
     * 递归查找注解，包括元注解
     *
     * @param psiClass      类
     * @param annotationFqn 注解全限定名
     * @param visited       已访问的类集合，用于防止循环引用
     * @return 注解
     */
    @Nullable
    PsiAnnotation findAnnotationRecursively(@NotNull PsiClass psiClass, String annotationFqn, Set<String> visited);
    
    /**
     * 从方法中获取API名称
     *
     * @param method         方法
     * @param controllerClass Controller类
     * @return API名称
     */
    String getApiNameFromMethod(@NotNull PsiMethod method, @NotNull PsiClass controllerClass);
    
    /**
     * 从类中获取Controller描述
     *
     * @param controllerClass Controller类
     * @return Controller描述
     */
    String getControllerDescription(@NotNull PsiClass controllerClass);
    
    /**
     * 获取注解属性值
     *
     * @param annotation 注解
     * @param attrNames  属性名（可以有多个备选）
     * @return 属性值
     */
    String getAnnotationAttributeValue(@NotNull PsiAnnotation annotation, String... attrNames);
    
    /**
     * 从@RequestMapping注解中获取HTTP方法
     *
     * @param requestMapping RequestMapping注解
     * @return HTTP方法数组
     */
    String[] getRequestMethodFromRequestMapping(@NotNull PsiAnnotation requestMapping);
}