package io.github.ideatools.ui.mybatis;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlTag;
import io.github.ideatools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Xml Sql Tag Line Marker Provider
 *
 * @author haijun
 * @date 2025-12-12 14:46:56
 * @version 1.0.0
 * @since 1.0.0
 */
public class XmlSqlTagLineMarkerProvider extends RelatedItemLineMarkerProvider {

    /**
     * MyBatis SQL标签集合
     */
    private static final Set<String> SQL_TAGS = Set.of(
            "select", "insert", "update", "delete",
            "sql", "selectKey"
    );

    /**
     * 收集导航标记信息
     *
     * @param element 当前PSI元素
     * @param result  结果收集器
     * @since 1.0.0
     */
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        // 只处理XML标签
        if (!(element instanceof XmlTag xmlTag)) {
            return;
        }

        // 检查是否为SQL标签
        String tagName = xmlTag.getName();
        if (!SQL_TAGS.contains(tagName)) {
            return;
        }

        // 检查是否在Mapper XML文件中
        if (!MyBatisUtils.isInMapperFile(xmlTag)) {
            return;
        }

        // 获取标签的id属性（对应接口方法名）
        String methodId = xmlTag.getAttributeValue("id");
        if (StringUtil.isEmpty(methodId)) {
            return;
        }

        // 获取namespace（对应接口全限定名）
        String namespace = MyBatisUtils.getMapperNamespace(xmlTag);
        if (StringUtil.isEmpty(namespace)) {
            return;
        }

        // 查找对应的Mapper接口方法
        PsiMethod mapperMethod = this.findMapperMethod(element.getProject(), namespace, methodId);
        if (mapperMethod == null) {
            return;
        }

        // 创建导航标记，使用绿色向左箭头
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(AllIcons.Gutter.ImplementingMethod)  // 使用绿色向左箭头图标
                .setTargets(Collections.singletonList(mapperMethod))
                .setTooltipText("导航到Mapper接口方法: " + methodId)
                .setAlignment(GutterIconRenderer.Alignment.LEFT);

        result.add(builder.createLineMarkerInfo(xmlTag));
    }



    /**
     * 查找Mapper接口的对应方法
     *
     * @param project    项目
     * @param namespace  Mapper接口的完全限定名
     * @param methodName 方法名
     * @return 找到的方法，未找到返回null
     * @since 1.0.0
     */
    private PsiMethod findMapperMethod(com.intellij.openapi.project.Project project,
                                        String namespace,
                                        String methodName) {
        // 查找Mapper接口类
        PsiClass mapperClass = JavaPsiFacade.getInstance(project)
                .findClass(namespace, GlobalSearchScope.allScope(project));

        if (mapperClass == null) {
            return null;
        }

        // 查找对应的方法（包括父接口中的方法）
        PsiMethod method = this.findMethodInClassHierarchy(mapperClass, methodName);
        return method;
    }

    /**
     * 在类层次结构中查找方法（包括父接口）
     *
     * @param psiClass   当前类
     * @param methodName 方法名
     * @return 找到的方法，未找到返回null
     * @since 1.0.0
     */
    private PsiMethod findMethodInClassHierarchy(PsiClass psiClass, String methodName) {
        // 首先在当前类中查找
        PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
        if (methods.length > 0) {
            return methods[0];
        }

        // 递归在父类/接口中查找
        for (PsiClass superClass : psiClass.getSupers()) {
            PsiMethod method = this.findMethodInClassHierarchy(superClass, methodName);
            if (method != null) {
                return method;
            }
        }

        return null;
    }
}
