package io.github.ideatools.service.mybatis;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.xml.XmlTag;
import io.github.ideatools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * MyBatis 参数引用解析实现类 (最终优化版) <p> 核心目标：实现 Mapper XML 中所有参数引用到 Java 接口参数或 POJO 字段的精确跳转。 解决了泛型方法参数绑定失败的问题，通过优先解析 XML 中的 {@code parameterType} 属性， 确保根参数（如 query）能够正确跳转到其方法参数定义处，并保证链式访问的类型正确性。 </p>
 *
 * @author haijun
 * @version 1.0.7
 * @date 2025-12-16 17:55:00
 * @since 1.0.0
 */
public class MyBatisParamReference extends PsiReferenceBase<PsiElement> {

    /**
     * full expression
     */
    private final String fullExpression;

    /**
     * current part name
     */
    private final String currentPartName;

    /**
     * 构造函数
     *
     * @param element        PSI元素
     * @param fullExpression 完整表达式
     * @param textRange      当前引用部分在元素中的文本范围
     * @since 1.0.0
     */
    public MyBatisParamReference(@NotNull PsiElement element,
                                 @NotNull String fullExpression,
                                 @NotNull TextRange textRange) {
        super(element, textRange);
        this.fullExpression = fullExpression;
        this.currentPartName = element.getText().substring(textRange.getStartOffset(), textRange.getEndOffset());
    }

    /**
     * 解析引用目标，实现跳转逻辑。
     *
     * @return 解析到的 Java 元素 (PsiParameter, PsiField, PsiMethod, PsiClass)，未找到返回 null
     * @since 1.0.0
     */
    @Override
    public @Nullable PsiElement resolve() {
        // 特殊处理：如果是include标签的refid引用，直接跳转到对应的sql标签
        XmlTag elementTag = MyBatisUtils.findParentTag(this.getElement());
        if (elementTag != null && "include".equals(elementTag.getName())) {
            return this.resolveIncludeRefId();
        }

        String[] parts = this.fullExpression.split("\\.");
        if (parts.length < 1) {
            return null;
        }

        String rootParamName = parts[0];
        PsiElement element = this.getElement();
        // --- 1. 根节点直接跳转 (例如：点击 'query') ---
        if (parts.length == 1 && rootParamName.equals(this.currentPartName)) {
            // 目标：跳转到 PsiParameter
            return MyBatisUtils.resolveRootParamClass(element, rootParamName);
        }

        // --- 2. 链式访问或单参数解包的后续字段跳转 ---
        // 目标：获取根参数的类型 (PsiClass)
        PsiClass currentClass = MyBatisUtils.resolveRootParamClass(element, rootParamName);

        // 如果找不到根类，尝试“单参数解包”场景 (逻辑保持不变)
        if (currentClass == null) {
            PsiClass singleParamClass = this.findSingleParamClass();
            if (singleParamClass != null) {
                PsiElement resolvedRootField = MyBatisUtils.findFieldOrPropertyInClass(singleParamClass, rootParamName);
                if (resolvedRootField != null) {
                    if (parts.length == 1) {
                        return resolvedRootField;
                    }
                    currentClass = MyBatisUtils.getTypeOfResolvedElement(resolvedRootField);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        int startIndex = 1;
        // 递归查找链条的剩余部分
        for (int i = startIndex; i < parts.length; i++) {
            if (currentClass == null) {
                return null;
            }

            // 核心泛型修正：在查找字段之前，尝试解析泛型类型
            PsiClass actualClass = MyBatisUtils.resolveActualClassFromType(currentClass);
            currentClass = actualClass != null ? actualClass : currentClass;

            String fieldName = parts[i];

            // 在当前类中查找字段/属性 (支持继承)
            PsiElement resolvedElement = MyBatisUtils.findFieldOrPropertyInClass(currentClass, fieldName);

            // 如果当前遍历到的部分就是本引用对象代表的部分，则返回找到的元素
            if (i == parts.length - 1 && fieldName.equals(this.currentPartName)) {
                return resolvedElement;
            }

            // 准备下一次迭代：将类型切换为当前字段/属性的类型
            if (resolvedElement != null) {
                currentClass = MyBatisUtils.getTypeOfResolvedElement(resolvedElement);
            } else {
                return null; // 链条中断
            }
        }

        return null;
    }

    /**
     * 辅助方法：尝试将泛型类型参数 (PsiTypeParameter) 解析为其具体绑定的 PsiClass。 解决了 PsiTypeParameter:Q 导致字段查找失败的问题。
     *
     * @param psiClass psi class
     * @return psi class
     * @since 1.0.0
     */
    private @Nullable PsiClass resolveActualClassFromType(@NotNull PsiClass psiClass) {
        // 如果已经是具体的类，直接返回
        if (!(psiClass instanceof PsiTypeParameter typeParameter)) {
            return psiClass;
        }

        // 尝试获取类型参数的第一个边界（Upper Bound）
        PsiReferenceList extendsList = typeParameter.getExtendsList();

        PsiClassType[] boundTypes = extendsList.getReferencedTypes();
        if (boundTypes.length > 0) {
            // 取第一个边界类型作为实际解析的类
            return boundTypes[0].resolve();
        }

        // 如果没有明确的边界，默认返回 java.lang.Object
        Project project = this.getElement().getProject();
        return JavaPsiFacade.getInstance(project).findClass("java.lang.Object", this.getElement().getResolveScope());
    }

    /**
     * 根据已解析的元素获取其类型对应的 PsiClass。
     *
     * @param element element
     * @return psi class
     * @since 1.0.0
     */
    private @Nullable PsiClass getTypeOfResolvedElement(@NotNull PsiElement element) {
        // 如果是字段，获取字段类型
        if (element instanceof PsiField field) {
            return PsiTypesUtil.getPsiClass(field.getType());
        }
        // 如果是 Getter 方法，获取其返回类型
        else if (element instanceof PsiMethod method) {
            return PsiTypesUtil.getPsiClass(method.getReturnType());
        }
        return null;
    }

    /**
     * 解析根参数对应的 Java 类 (PsiClass)。 核心修正：优先读取 XML 标签上的 'parameterType' 属性，为链式访问提供准确的类型。
     *
     * @param paramName 参数名
     * @return 类定义
     * @since 1.0.0
     */
    private @Nullable PsiClass resolveRootParamClass(String paramName) {
        // 1. 修正核心：优先读取 XML 标签上的 parameterType 属性，解决泛型绑定失败时类型缺失的问题。
        XmlTag sqlTag = MyBatisUtils.findSqlTag(MyBatisUtils.findParentTag(this.getElement()));
        if (sqlTag != null) {
            String type = sqlTag.getAttributeValue("parameterType");
            if (StringUtil.isNotEmpty(type)) {
                Project project = this.getElement().getProject();
                return JavaPsiFacade.getInstance(project)
                        .findClass(type, this.getElement().getResolveScope());
            }
        }

        // 2. 如果 parameterType 无法提供类型，回退到从方法参数中查找
        PsiParameter parameter = this.findPsiParameter(paramName);
        if (parameter != null) {
            return PsiTypesUtil.getPsiClass(parameter.getType());
        }

        return null;
    }

    /**
     * 查找 Mapper 方法中对应的参数对象，支持 @Param 注解解析。
     *
     * @param paramName param name
     * @return psi parameter
     * @since 1.0.0
     */
    private @Nullable PsiParameter findPsiParameter(String paramName) {
        PsiMethod method = this.findMethod();
        if (method == null) {
            return null;
        }

        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            String paramAnnotationValue = this.getParamAnnotationValue(parameter);
            if (StringUtil.equals(paramAnnotationValue, paramName)) {
                return parameter;
            }
            if (parameter.getName().equals(paramName)) {
                return parameter;
            }
        }
        return null;
    }

    /**
     * 查找"单参数解包"场景下的 POJO 类。
     *
     * @return psi class
     * @since 1.0.0
     */
    private @Nullable PsiClass findSingleParamClass() {
        PsiMethod method = this.findMethod();
        if (method == null) {
            return null;
        }

        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length == 1) {
            PsiParameter param = parameters[0];
            // 且必须没有 @Param 注解
            if (StringUtil.isEmpty(this.getParamAnnotationValue(param))) {
                return PsiTypesUtil.getPsiClass(param.getType());
            }
        }
        return null;
    }

    /**
     * 提取 @Param 注解的值。
     *
     * @param parameter parameter
     * @return string
     * @since 1.0.0
     */
    private String getParamAnnotationValue(PsiParameter parameter) {
        PsiAnnotation annotation = parameter.getAnnotation("org.apache.ibatis.annotations.Param");
        if (annotation != null) {
            PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
            if (value instanceof PsiLiteralExpression literalExpression) {
                Object val = literalExpression.getValue();
                return val != null ? val.toString() : null;
            }
        }
        return null;
    }

    /**
     * 获取当前 XML 对应的 Mapper 接口方法。 支持向上查找父接口/类中定义的方法。
     *
     * @return psi method
     * @since 1.0.0
     */
    private @Nullable PsiMethod findMethod() {
        XmlTag elementTag = MyBatisUtils.findParentTag(this.getElement());
        if (elementTag == null) {
            return null;
        }

        XmlTag sqlTag = MyBatisUtils.findSqlTag(elementTag);
        if (sqlTag == null) {
            return null;
        }

        String id = sqlTag.getAttributeValue("id");
        String namespace = MyBatisUtils.getMapperNamespace(sqlTag);

        if (StringUtil.isEmpty(id) || StringUtil.isEmpty(namespace)) {
            return null;
        }

        PsiClass mapperClass = JavaPsiFacade.getInstance(this.getElement().getProject())
                .findClass(namespace, this.getElement().getResolveScope());
        if (mapperClass == null) {
            return null;
        }

        // 查找所有方法，包括继承的方法 (实现向上查找)
        for (PsiMethod method : mapperClass.getAllMethods()) {
            if (method.getName().equals(id)) {
                return method;
            }
        }

        return null;
    }

    /**
     * 在类中查找字段或属性 (支持继承和 Getter/Setter 属性)。
     *
     * @param psiClass  目标类
     * @param fieldName 字段名
     * @return 找到的 PsiField 或 Getter PsiMethod，未找到返回 null
     * @since 1.0.0
     */
    private @Nullable PsiElement findFieldOrPropertyInClass(@NotNull PsiClass psiClass, @NotNull String fieldName) {

        // 1. 尝试使用 IntelliJ API，通过类和属性名查找字段 (处理继承)
        PsiField field = PropertyUtilBase.findPropertyField(psiClass, fieldName, false);
        if (field != null) {
            return field;
        }

        // 2. 如果字段找不到，尝试查找 JavaBean 属性对应的 Getter 方法 (兼容 Lombok)
        PsiMethod getter = PropertyUtilBase.findPropertyGetter(psiClass, fieldName, true, false);
        if (getter != null) {
            return getter;
        }

        // 3. 额外尝试：模糊查找 (处理 user_name 映射到 userName 字段的情况)
        String cleanName = fieldName.replace("_", "");
        for (PsiField f : psiClass.getAllFields()) {
            if (f.getName().replace("_", "").equalsIgnoreCase(cleanName)) {
                return f;
            }
        }

        return null;
    }

    /**
     * 解析include标签的refid引用，跳转到对应的sql标签
     *
     * @return psi element
     * @since 1.0.8
     */
    private @Nullable PsiElement resolveIncludeRefId() {
        String refId = this.fullExpression;
        if (StringUtil.isNotEmpty(refId)) {
            return MyBatisUtils.findSqlTagById(refId, this.getElement());
        }
        return null;
    }
}
