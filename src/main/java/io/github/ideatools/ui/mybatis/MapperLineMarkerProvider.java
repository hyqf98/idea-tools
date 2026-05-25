package io.github.ideatools.ui.mybatis;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import io.github.ideatools.service.mybatis.MapperXmlCacheService;
import io.github.ideatools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * Mapper Line Marker Provider
 *
 * @author haijun
 * @date 2025-12-12 14:07:26
 * @version 1.0.0
 * @since 1.0.0
 */
public class MapperLineMarkerProvider extends RelatedItemLineMarkerProvider {

    /**
     * MyBatis的@Mapper注解完全限定名
     */
    private static final String MAPPER_ANNOTATION = "org.apache.ibatis.annotations.Mapper";

    /**
     * Spring的@Mapper注解完全限定名（MyBatis-Spring Boot）
     */
    private static final String SPRING_MAPPER_ANNOTATION = "org.mybatis.spring.annotation.Mapper";

    /**
     * 收集导航标记信息
     *
     * @param element  当前PSI元素
     * @param result   结果收集器
     * @since 1.0.0
     */
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        // 只处理标识符元素（类名或方法名）
        if (!(element instanceof PsiIdentifier)) {
            return;
        }

        // 获取父元素
        PsiElement parent = element.getParent();

        // 处理类级别的导航（接口名 -> XML文件）
        if (parent instanceof PsiClass psiClass) {
            this.handleClassNavigation(element, psiClass, result);
        }
        // 处理方法级别的导航（方法名 -> XML标签）
        else if (parent instanceof PsiMethod psiMethod) {
            this.handleMethodNavigation(element, psiMethod, result);
        }
    }

    /**
     * 检查类是否有@Mapper注解
     *
     * @param psiClass 要检查的类
     * @return true如果有@Mapper注解
     * @since 1.0.0
     */
    private boolean hasMapperAnnotation(PsiClass psiClass) {
        return psiClass.hasAnnotation(MAPPER_ANNOTATION) ||
                psiClass.hasAnnotation(SPRING_MAPPER_ANNOTATION);
    }

    /**
     * 处理类级别的导航（Mapper接口 -> XML文件）
     *
     * @param element 标识符元素
     * @param psiClass 类
     * @param result 结果收集器
     * @since 1.0.0
     */
    private void handleClassNavigation(@NotNull PsiElement element,
                                         @NotNull PsiClass psiClass,
                                         @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        // 检查是否为接口
        if (!psiClass.isInterface()) {
            return;
        }

        // 检查是否有@Mapper注解
        if (!this.hasMapperAnnotation(psiClass)) {
            return;
        }

        // 获取接口的完全限定名
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // 从缓存中查找对应的XML文件
        MapperXmlCacheService cacheService = MapperXmlCacheService.getInstance(element.getProject());

        // 如果缓存为空，先扫描
        if (cacheService.isCacheEmpty()) {
            cacheService.scanAndCacheMapperXmlFiles();
        }

        XmlFile xmlFile = cacheService.getXmlFileByNamespace(qualifiedName);
        if (xmlFile == null) {
            return;
        }

        // 创建导航标记
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(AllIcons.Gutter.RecursiveMethod)
                .setTargets(Collections.singletonList(xmlFile))
                .setTooltipText("导航到MyBatis Mapper XML配置文件")
                .setAlignment(GutterIconRenderer.Alignment.LEFT);

        result.add(builder.createLineMarkerInfo(element));
    }

    /**
     * 处理方法级别的导航（Mapper方法 -> XML SQL标签）
     *
     * @param element 标识符元素
     * @param psiMethod 方法
     * @param result 结果收集器
     * @since 1.0.0
     */
    private void handleMethodNavigation(@NotNull PsiElement element,
                                          @NotNull PsiMethod psiMethod,
                                          @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        // 获取方法所在的类
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null || !containingClass.isInterface()) {
            return;
        }

        // 检查是否有@Mapper注解
        if (!this.hasMapperAnnotation(containingClass)) {
            return;
        }

        // 获取接口的完全限定名
        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // 从缓存中查找对应的XML文件
        MapperXmlCacheService cacheService = MapperXmlCacheService.getInstance(element.getProject());
        if (cacheService.isCacheEmpty()) {
            cacheService.scanAndCacheMapperXmlFiles();
        }

        XmlFile xmlFile = cacheService.getXmlFileByNamespace(qualifiedName);
        if (xmlFile == null) {
            // 如果当前接口没有对应的XML文件，尝试在父接口中查找
            XmlTag sqlTag = this.findSqlTagInParentInterfaces(containingClass, psiMethod.getName(), cacheService);
            if (sqlTag != null) {
                // 创建导航标记
                NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                        .create(AllIcons.Gutter.RecursiveMethod)
                        .setTargets(Collections.singletonList(sqlTag))
                        .setTooltipText("导航到MyBatis XML SQL标签（来自父接口）")
                        .setAlignment(GutterIconRenderer.Alignment.LEFT);

                result.add(builder.createLineMarkerInfo(element));
            }
            return;
        }

        // 在XML文件中查找对应的SQL标签
        XmlTag sqlTag = MyBatisUtils.findSqlTagByMethodName(xmlFile, psiMethod.getName());
        if (sqlTag == null) {
            // 如果当前XML文件中没有找到，尝试在父接口的XML文件中查找
            sqlTag = this.findSqlTagInParentInterfaces(containingClass, psiMethod.getName(), cacheService);
            if (sqlTag == null) {
                return;
            }
            // 创建导航标记（来自父接口）
            NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                    .create(AllIcons.Gutter.RecursiveMethod)
                    .setTargets(Collections.singletonList(sqlTag))
                    .setTooltipText("导航到MyBatis XML SQL标签（来自父接口）")
                    .setAlignment(GutterIconRenderer.Alignment.LEFT);

            result.add(builder.createLineMarkerInfo(element));
            return;
        }

        // 创建导航标记
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(AllIcons.Gutter.RecursiveMethod)
                .setTargets(Collections.singletonList(sqlTag))
                .setTooltipText("导航到MyBatis XML SQL标签")
                .setAlignment(GutterIconRenderer.Alignment.LEFT);

        result.add(builder.createLineMarkerInfo(element));
    }

    /**
     * 在父接口中查找SQL标签
     *
     * @param psiClass 当前接口类
     * @param methodName 方法名
     * @param cacheService 缓存服务
     * @return 找到的SQL标签，未找到返回null
     * @since 1.0.0
     */
    private XmlTag findSqlTagInParentInterfaces(@NotNull PsiClass psiClass, @NotNull String methodName,
                                               @NotNull MapperXmlCacheService cacheService) {
        // 获取所有父接口
        PsiClass[] supers = psiClass.getSupers();
        for (PsiClass superClass : supers) {
            if (!superClass.isInterface()) {
                continue;
            }

            String superQualifiedName = superClass.getQualifiedName();
            if (superQualifiedName == null) {
                continue;
            }

            // 查找父接口对应的XML文件
            XmlFile superXmlFile = cacheService.getXmlFileByNamespace(superQualifiedName);
            if (superXmlFile != null) {
                // 在父接口的XML文件中查找SQL标签
                XmlTag sqlTag = MyBatisUtils.findSqlTagByMethodName(superXmlFile, methodName);
                if (sqlTag != null) {
                    return sqlTag;
                }
            }

            // 递归查找更上层的父接口
            XmlTag sqlTag = this.findSqlTagInParentInterfaces(superClass, methodName, cacheService);
            if (sqlTag != null) {
                return sqlTag;
            }
        }

        return null;
    }
}
