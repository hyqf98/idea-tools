package io.github.easy.tools.utils;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * My Batis Utils
 *
 * @author haijun
 * @version 1.0.0
 * @date 2025-12-15 09:35:18
 * @since 1.0.0
 */
public final class MyBatisUtils {

    /**
     * 私有构造函数，防止实例化
     *
     * @since 1.0.0
     */
    private MyBatisUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 检查元素是否在MyBatis Mapper XML文件中
     *
     * @param element PSI元素
     * @return true如果在Mapper文件中
     * @since 1.0.0
     */
    public static boolean isInMapperFile(@NotNull PsiElement element) {
        // 向上查找XML文件
        PsiFile psiFile = element.getContainingFile();
        if (!(psiFile instanceof XmlFile xmlFile)) {
            return false;
        }

        // 检查根标签是否为mapper
        XmlTag rootTag = xmlFile.getRootTag();
        return rootTag != null && "mapper".equals(rootTag.getName());
    }

    /**
     * 检查XML标签是否在Mapper XML文件中
     *
     * @param xmlTag XML标签
     * @return true如果在Mapper文件中
     * @since 1.0.0
     */
    public static boolean isInMapperFile(@NotNull XmlTag xmlTag) {
        // 向上查找根标签
        XmlTag currentTag = xmlTag;
        while (currentTag.getParentTag() != null) {
            currentTag = currentTag.getParentTag();
        }

        // 检查根标签是否为mapper
        return "mapper".equals(currentTag.getName());
    }

    /**
     * 获取Mapper的namespace属性
     *
     * @param xmlTag XML标签
     * @return namespace值，未找到返回null
     * @since 1.0.0
     */
    public static @Nullable String getMapperNamespace(@NotNull XmlTag xmlTag) {
        // 向上查找根标签
        XmlTag rootTag = xmlTag;
        while (rootTag.getParentTag() != null) {
            rootTag = rootTag.getParentTag();
        }

        // 检查根标签是否为mapper
        if (!"mapper".equals(rootTag.getName())) {
            return null;
        }

        // 获取namespace属性
        return rootTag.getAttributeValue("namespace");
    }

    /**
     * 在XML文件中查找对应的SQL标签
     *
     * @param xmlFile    XML文件
     * @param methodName 方法名
     * @return 找到的SQL标签，未找到返回null
     * @since 1.0.0
     */
    public static @Nullable XmlTag findSqlTagByMethodName(@NotNull XmlFile xmlFile, @NotNull String methodName) {
        XmlTag rootTag = xmlFile.getRootTag();
        if (rootTag == null) {
            return null;
        }

        // 遍历所有子标签
        for (XmlTag tag : rootTag.getSubTags()) {
            String id = tag.getAttributeValue("id");
            if (methodName.equals(id)) {
                return tag;
            }
        }

        return null;
    }

    /**
     * 在XML文件中根据ID查找sql标签（用于include标签的refid跳转）
     *
     * @param xmlFile XML文件
     * @param id      SQL标签的ID
     * @return 找到的sql标签，未找到返回null
     * @since 1.0.8
     */
    public static @Nullable XmlTag findSqlTagById(@NotNull XmlFile xmlFile, @NotNull String id) {
        XmlTag rootTag = xmlFile.getRootTag();
        if (rootTag == null) {
            return null;
        }

        // 遍历所有子标签，查找sql标签
        for (XmlTag tag : rootTag.getSubTags()) {
            // 只查找sql标签
            if ("sql".equals(tag.getName())) {
                String tagId = tag.getAttributeValue("id");
                if (id.equals(tagId)) {
                    return tag;
                }
            }
        }

        return null;
    }

    /**
     * 在XML文件中根据ID查找sql标签（用于include标签的refid跳转）- 重载版本，支持从元素上下文查找
     *
     * @param id      SQL标签的ID
     * @param context 上下文元素
     * @return 找到的sql标签，未找到返回null
     * @since 1.0.8
     */
    public static @Nullable XmlTag findSqlTagById(@NotNull String id, @NotNull PsiElement context) {
        PsiFile psiFile = context.getContainingFile();
        if (!(psiFile instanceof XmlFile xmlFile)) {
            return null;
        }
        return findSqlTagById(xmlFile, id);
    }

    /**
     * 向上查找XML标签
     *
     * @param element PSI元素
     * @return XML标签，未找到返回null
     * @since 1.0.0
     */
    public static @Nullable XmlTag findParentTag(@NotNull PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof XmlTag) {
                return (XmlTag) current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * 向上查找SQL标签(select/insert/update/delete)
     *
     * @param xmlTag 当前XML标签
     * @return SQL标签，未找到返回null
     * @since 1.0.0
     */
    public static @Nullable XmlTag findSqlTag(@NotNull XmlTag xmlTag) {
        XmlTag current = xmlTag;
        while (current != null) {
            String tagName = current.getName();
            if ("select".equals(tagName) || "insert".equals(tagName) ||
                    "update".equals(tagName) || "delete".equals(tagName)) {
                return current;
            }
            current = current.getParentTag();
        }
        return null;
    }

    /**
     * 【复用自 MyBatisParamReference】解析根参数对应的 Java 类 (PsiClass)。 核心：优先读取 XML 标签上的 'parameterType' 属性，为链式访问提供准确的类型。
     *
     * @param context   context
     * @param paramName param name
     * @return psi class
     * @since 1.0.0
     */
    public static @Nullable PsiClass resolveRootParamClass(@NotNull PsiElement context, @NotNull String paramName) {
        // 1. 优先读取 XML 标签上的 parameterType 属性，解决泛型绑定失败时类型缺失的问题。
        XmlTag sqlTag = findSqlTag(findParentTag(context));
        if (sqlTag != null) {
            String type = sqlTag.getAttributeValue("parameterType");
            if (StringUtil.isNotEmpty(type)) {
                Project project = context.getProject();
                return JavaPsiFacade.getInstance(project)
                        .findClass(type, context.getResolveScope());
            }
        }

        // 2. 如果 parameterType 无法提供类型，回退到从方法参数中查找
        PsiParameter parameter = findPsiParameter(context, paramName);
        if (parameter != null) {
            return PsiTypesUtil.getPsiClass(parameter.getType());
        }

        return null;
    }

    /**
     * 统一解析根参数对应的 Java 类。
     * 优先使用 XML 中的 parameterType，其次使用方法参数的实际推断类型，最后退回边界类型。
     *
     * @param context context
     * @param parameter parameter
     * @param paramName param name
     * @return psi class
     * @since 1.1.0
     */
    public static @Nullable PsiClass resolveRootParamClass(@NotNull PsiElement context,
                                                           @NotNull PsiParameter parameter,
                                                           @NotNull String paramName) {
        PsiClass psiClass = resolveRootParamClass(context, paramName);
        if (psiClass != null) {
            return psiClass;
        }
        return resolveRootParamClass(parameter, paramName);
    }

    /**
     * 解析参数对应的 Java 类 (PsiClass)。
     * 支持泛型方法中的类型参数解析，会尝试从方法声明中推断实际类型。
     *
     * @param parameter parameter
     * @param paramName param name
     * @return psi class
     * @since 1.1.0
     */
    public static @Nullable PsiClass resolveRootParamClass(@NotNull PsiParameter parameter, @NotNull String paramName) {
        PsiClass psiClass = PsiTypesUtil.getPsiClass(parameter.getType());
        if (psiClass == null) {
            return null;
        }

        // 如果是泛型类型参数（如 Q extends BaseQuery<?>），尝试推断其实际类型
        if (psiClass instanceof PsiTypeParameter) {
            // 尝试从 Mapper 接口的泛型参数推断实际类型
            PsiMethod method = (PsiMethod) parameter.getDeclarationScope();
            PsiClass inferredClass = inferGenericTypeFromMapper(method, parameter);
            if (inferredClass != null) {
                return inferredClass;
            }

            // 如果无法推断，则使用边界类型
            return resolveActualClassFromType(psiClass);
        }

        return psiClass;
    }

    /**
     * 从 Mapper 接口的泛型参数推断方法泛型参数的实际类型
     * 例如：AlarmConfigMapper extends BaseDao<AlarmConfig>
     * 对于方法 page(@Param("query") Q query)，其中 Q extends BaseQuery<?>
     * 会推断出实际类型应该是 AlarmConfigQuery
     *
     * @param method    方法
     * @param parameter 参数
     * @return 推断出的类型，如果无法推断返回 null
     * @since 1.1.0
     */
    private static @Nullable PsiClass inferGenericTypeFromMapper(@NotNull PsiMethod method, @NotNull PsiParameter parameter) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return null;
        }

        // 查找定义该方法的接口或父接口
        PsiClass definingClass = findDefiningClass(containingClass, method);
        if (definingClass == null) {
            return null;
        }

        // 如果方法是在当前类定义的，无法推断
        if (definingClass.equals(containingClass)) {
            return null;
        }

        // 获取 containingClass 中对应 definingClass 的泛型参数
        PsiClass entityClass = findEntityTypeFromMapper(containingClass, definingClass);
        if (entityClass == null) {
            return null;
        }

        // 根据参数的泛型边界和实体类推断实际类型
        return inferQueryTypeFromEntity(entityClass, parameter, method.getProject());
    }

    /**
     * 查找定义该方法的类或接口
     *
     * @param startClass 起始类
     * @param method     方法
     * @return 定义该方法的类，如果未找到返回 null
     * @since 1.1.0
     */
    private static @Nullable PsiClass findDefiningClass(@NotNull PsiClass startClass, @NotNull PsiMethod method) {
        // 先检查当前类
        for (PsiMethod m : startClass.getMethods()) {
            if (m.equals(method)) {
                return startClass;
            }
        }

        // 检查父类和接口
        for (PsiClass superClass : startClass.getSupers()) {
            PsiClass found = findDefiningClass(superClass, method);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    /**
     * 从 Mapper 接口中查找实体类型
     * 例如：AlarmConfigMapper extends BaseDao<AlarmConfig>
     * 返回 AlarmConfig
     *
     * @param mapperClass   Mapper 类
     * @param definingClass 定义方法的类（如 BaseDao）
     * @return 实体类型，如果未找到返回 null
     * @since 1.1.0
     */
    private static @Nullable PsiClass findEntityTypeFromMapper(@NotNull PsiClass mapperClass, @NotNull PsiClass definingClass) {
        // 遍历 mapperClass 的父类和接口
        for (PsiClassType superType : mapperClass.getSuperTypes()) {
            PsiClass superClass = superType.resolve();
            if (superClass == null) {
                continue;
            }

            // 如果找到了 definingClass
            if (superClass.equals(definingClass) || superClass.isInheritor(definingClass, true)) {
                // 获取泛型参数
                PsiType[] typeParameters = superType.getParameters();
                if (typeParameters.length > 0) {
                    // 假设第一个泛型参数是实体类型（如 BaseDao<T> 中的 T）
                    return PsiTypesUtil.getPsiClass(typeParameters[0]);
                }
            }

            // 递归查找
            PsiClass found = findEntityTypeFromMapper(superClass, definingClass);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    /**
     * 根据实体类推断查询类型
     * 命名约定：AlarmConfig -> AlarmConfigQuery
     *
     * @param entityClass 实体类
     * @param parameter   参数
     * @param project     项目
     * @return 查询类型，如果未找到返回 null
     * @since 1.1.0
     */
    private static @Nullable PsiClass inferQueryTypeFromEntity(@NotNull PsiClass entityClass,
                                                               @NotNull PsiParameter parameter,
                                                               @NotNull Project project) {
        String entityClassName = entityClass.getName();
        if (entityClassName == null) {
            return null;
        }

        // 获取实体类的包名
        String qualifiedName = entityClass.getQualifiedName();
        if (qualifiedName == null) {
            return null;
        }

        String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));

        // 根据参数类型的边界推断后缀
        PsiType paramType = parameter.getType();
        PsiClass paramClass = PsiTypesUtil.getPsiClass(paramType);
        if (paramClass instanceof PsiTypeParameter typeParam) {
            // 获取泛型边界
            PsiReferenceList extendsList = typeParam.getExtendsList();
            PsiClassType[] boundTypes = extendsList.getReferencedTypes();

            if (boundTypes.length > 0) {
                PsiClass boundClass = boundTypes[0].resolve();
                if (boundClass != null) {
                    String boundClassName = boundClass.getName();

                    // 根据边界类型推断后缀
                    String suffix = null;
                    if (boundClassName != null) {
                        if (boundClassName.endsWith("Query")) {
                            suffix = "Query";
                        } else if (boundClassName.endsWith("DTO")) {
                            suffix = "DTO";
                        } else if (boundClassName.endsWith("VO")) {
                            suffix = "VO";
                        } else if (boundClassName.endsWith("Request")) {
                            suffix = "Request";
                        }
                    }

                    if (suffix != null) {
                        // 尝试在相同包和常见包中查找
                        String[] candidatePackages = {
                            packageName,
                            packageName + ".query",
                            packageName + ".dto",
                            packageName + ".vo",
                            packageName + ".request",
                            packageName.replace(".entity", ".query"),
                            packageName.replace(".entity", ".dto"),
                            packageName.replace(".model", ".query"),
                            packageName.replace(".model", ".dto")
                        };

                        String targetClassName = entityClassName + suffix;
                        GlobalSearchScope scope = GlobalSearchScope.allScope(project);

                        for (String pkg : candidatePackages) {
                            String fqn = pkg + "." + targetClassName;
                            PsiClass found = JavaPsiFacade.getInstance(project).findClass(fqn, scope);
                            if (found != null) {
                                return found;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * 【复用自 MyBatisParamReference】获取当前 XML 对应的 Mapper 接口方法中的参数。
     *
     * @param context   context
     * @param paramName param name
     * @return psi parameter
     * @since 1.0.0
     */
    public static @Nullable PsiParameter findPsiParameter(@NotNull PsiElement context, @NotNull String paramName) {
        PsiMethod method = findMethod(context);
        if (method == null) {
            return null;
        }

        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            String paramAnnotationValue = getParamAnnotationValue(parameter);
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
     * 【复用自 MyBatisParamReference】获取当前 XML 对应的 Mapper 接口方法。 支持向上查找父接口/类中定义的方法。
     *
     * @param context context
     * @return psi method
     * @since 1.0.0
     */
    public static @Nullable PsiMethod findMethod(@NotNull PsiElement context) {
        XmlTag elementTag = findParentTag(context);
        if (elementTag == null) {
            return null;
        }

        XmlTag sqlTag = findSqlTag(elementTag);
        if (sqlTag == null) {
            return null;
        }

        String id = sqlTag.getAttributeValue("id");
        String namespace = getMapperNamespace(sqlTag);

        if (StringUtil.isEmpty(id) || StringUtil.isEmpty(namespace)) {
            return null;
        }

        PsiClass mapperClass = JavaPsiFacade.getInstance(context.getProject())
                .findClass(namespace, context.getResolveScope());
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
     * 【复用自 MyBatisParamReference】提取 @Param 注解的值。
     *
     * @param parameter parameter
     * @return string
     * @since 1.0.0
     */
    public static String getParamAnnotationValue(PsiParameter parameter) {
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
     * 【复用自 MyBatisParamReference】根据已解析的元素获取其类型对应的 PsiClass。
     *
     * @param element element
     * @return psi class
     * @since 1.0.0
     */
    public static @Nullable PsiClass getTypeOfResolvedElement(@NotNull PsiElement element) {
        if (element instanceof PsiField field) {
            return PsiTypesUtil.getPsiClass(field.getType());
        } else if (element instanceof PsiMethod method) {
            return PsiTypesUtil.getPsiClass(method.getReturnType());
        }
        return null;
    }

    /**
     * 【复用自 MyBatisParamReference】辅助方法：尝试将泛型类型参数 (PsiTypeParameter) 解析为其具体绑定的 PsiClass。
     *
     * @param psiClass psi class
     * @return psi class
     * @since 1.0.0
     */
    public static @Nullable PsiClass resolveActualClassFromType(@NotNull PsiClass psiClass) {
        if (!(psiClass instanceof PsiTypeParameter typeParameter)) {
            return psiClass;
        }

        PsiReferenceList extendsList = typeParameter.getExtendsList();

        PsiClassType[] boundTypes = extendsList.getReferencedTypes();
        if (boundTypes.length > 0) {
            return boundTypes[0].resolve();
        }

        Project project = typeParameter.getProject();
        return JavaPsiFacade.getInstance(project).findClass("java.lang.Object", typeParameter.getResolveScope());
    }

    /**
     * 【复用自 MyBatisParamReference】在类中查找字段或属性 (支持继承和 Getter/Setter 属性)。
     *
     * @param psiClass  psi class
     * @param fieldName field name
     * @return psi element
     * @since 1.0.0
     */
    public static @Nullable PsiElement findFieldOrPropertyInClass(@NotNull PsiClass psiClass, @NotNull String fieldName) {
        PsiField field = PropertyUtilBase.findPropertyField(psiClass, fieldName, false);
        if (field != null) {
            return field;
        }

        PsiMethod getter = PropertyUtilBase.findPropertyGetter(psiClass, fieldName, true, false);
        if (getter != null) {
            return getter;
        }

        String cleanName = fieldName.replace("_", "");
        for (PsiField f : psiClass.getAllFields()) {
            if (f.getName().replace("_", "").equalsIgnoreCase(cleanName)) {
                return f;
            }
        }
        return null;
    }
}
