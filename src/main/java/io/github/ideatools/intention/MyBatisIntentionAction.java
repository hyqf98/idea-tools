package io.github.ideatools.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import io.github.ideatools.action.conversion.PropertyNameConverter;
import io.github.ideatools.entity.doc.ParameterInfo;
import io.github.ideatools.service.doc.velocity.VelocityTemplateRenderer;
import io.github.ideatools.service.mybatis.MapperXmlCacheService;
import io.github.ideatools.utils.MyBatisUtils;
import io.github.ideatools.utils.NotificationUtil;
import org.apache.velocity.VelocityContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis SQL标签生成意图操作基类
 * <p>
 * 将MyBatis Action的功能集成到Ctrl+回车的意图操作菜单中
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class MyBatisIntentionAction extends PsiElementBaseIntentionAction implements IntentionAction {

    /**
     * MyBatis的@Mapper注解完全限定名
     */
    private static final String MAPPER_ANNOTATION = "org.apache.ibatis.annotations.Mapper";

    /**
     * Spring的@Mapper注解完全限定名
     */
    private static final String SPRING_MAPPER_ANNOTATION = "org.mybatis.spring.annotation.Mapper";

    /**
     * Velocity模板渲染器
     */
    private static final VelocityTemplateRenderer VELOCITY_RENDERER = new VelocityTemplateRenderer();

    /**
     * 获取标签类型
     *
     * @return 标签类型 (select, insert, update, delete)
     */
    @NotNull
    protected abstract String getTagType();

    /**
     * 获取显示文本
     *
     * @return 显示文本
     */
    @NotNull
    protected abstract String getDisplayText();

    /**
     * 获取模板路径
     *
     * @return 模板路径
     */
    @NotNull
    protected abstract String getTemplatePath();

    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        PsiFile psiFile = element.getContainingFile();
        PsiMethod method = this.findMethodAtElement(element);

        if (method == null) {
            NotificationUtil.showWarning(project, "未找到Mapper方法");
            return;
        }

        PsiClass mapperClass = method.getContainingClass();
        if (mapperClass == null || !mapperClass.isInterface()) {
            NotificationUtil.showWarning(project, "当前方法不在Mapper接口中");
            return;
        }

        if (!this.hasMapperAnnotation(mapperClass)) {
            NotificationUtil.showWarning(project, "当前接口没有@Mapper注解");
            return;
        }

        XmlFile xmlFile = this.findMapperXmlFile(mapperClass);
        if (xmlFile == null) {
            NotificationUtil.showWarning(project, "未找到对应的Mapper XML文件");
            return;
        }

        if (MyBatisUtils.findSqlTagByMethodName(xmlFile, method.getName()) != null) {
            NotificationUtil.showWarning(project, "该方法的SQL标签已存在");
            return;
        }

        this.generateSqlTag(project, xmlFile, method);
    }

    @Override
    public boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) {
        PsiMethod method = this.findMethodAtElement(element);
        if (method == null) {
            return false;
        }

        PsiClass mapperClass = method.getContainingClass();
        if (mapperClass == null || !mapperClass.isInterface()) {
            return false;
        }

        return this.hasMapperAnnotation(mapperClass);
    }

    @Override
    public @NotNull String getFamilyName() {
        return "MyBatis SQL标签生成";
    }

    @Override
    public @NotNull String getText() {
        return this.getDisplayText();
    }

    /**
     * 获取意图操作图标
     * <p>
     * 根据不同的SQL操作类型返回不同的图标，使菜单更直观
     * </p>
     *
     * @param flags 图标标志
     * @return 图标
     */
    public Icon getIcon(int flags) {
        // 根据不同的SQL操作类型返回不同的图标
        String tagType = this.getTagType();
        switch (tagType) {
            case "select":
                return AllIcons.Actions.Find;
            case "insert":
                return AllIcons.General.Add;
            case "update":
                return AllIcons.Actions.Edit;
            case "delete":
                return AllIcons.Actions.GC;
            default:
                return AllIcons.Actions.Forward;
        }
    }

    /**
     * 查找元素所在的方法
     *
     * @param element PSI元素
     * @return 方法元素，未找到返回null
     */
    private PsiMethod findMethodAtElement(PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof PsiMethod) {
                return (PsiMethod) current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * 检查类是否有@Mapper注解
     *
     * @param psiClass 类
     * @return true如果有@Mapper注解
     */
    private boolean hasMapperAnnotation(@NotNull PsiClass psiClass) {
        return psiClass.hasAnnotation(MAPPER_ANNOTATION) ||
                psiClass.hasAnnotation(SPRING_MAPPER_ANNOTATION);
    }

    /**
     * 查找Mapper接口对应的XML文件
     *
     * @param mapperClass Mapper接口类
     * @return XML文件，未找到返回null
     */
    private XmlFile findMapperXmlFile(@NotNull PsiClass mapperClass) {
        String qualifiedName = mapperClass.getQualifiedName();
        if (qualifiedName == null) {
            return null;
        }

        Project project = mapperClass.getProject();
        MapperXmlCacheService cacheService = MapperXmlCacheService.getInstance(project);
        if (cacheService.isCacheEmpty()) {
            cacheService.scanAndCacheMapperXmlFiles();
        }

        return cacheService.getXmlFileByNamespace(qualifiedName);
    }

    /**
     * 生成SQL标签
     *
     * @param project 项目
     * @param xmlFile XML文件
     * @param method  方法
     */
    private void generateSqlTag(@NotNull Project project,
                                @NotNull XmlFile xmlFile,
                                @NotNull PsiMethod method) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                // 准备模板上下文
                VelocityContext context = new VelocityContext();
                context.put("methodId", method.getName());
                context.put("tableName", this.guessTableName(method.getContainingClass()));

                // 添加参数类型信息
                var params = method.getParameterList().getParameters();
                if (params.length == 1) {
                    context.put("parameterType", params[0].getType().getCanonicalText());
                } else {
                    context.put("parameterType", "map");
                }

                // 添加返回类型信息
                if (method.getReturnType() != null) {
                    context.put("resultType", method.getReturnType().getCanonicalText());
                } else {
                    context.put("resultType", "void");
                }

                // 解析参数
                List<ParameterInfo> parameters = this.parseParameters(method);
                context.put("parameters", parameters);

                // 渲染模板
                String tagContent = VELOCITY_RENDERER.renderFromFile(
                        this.getTemplatePath(),
                        context
                );

                // 添加标签到XML文件
                this.addTagToXml(xmlFile, tagContent);

                NotificationUtil.showInfo(project, "SQL标签生成成功");
            } catch (Exception ex) {
                NotificationUtil.showError(project, "生成SQL标签失败: " + ex.getMessage());
            }
        });
    }

    /**
     * 解析方法参数信息
     *
     * @param method 方法
     * @return 参数信息列表
     */
    private List<ParameterInfo> parseParameters(@NotNull PsiMethod method) {
        List<ParameterInfo> parameters = new ArrayList<>();

        for (var parameter : method.getParameterList().getParameters()) {
            String paramName = parameter.getName();
            String paramType = parameter.getType().getPresentableText();

            // 获取@Param注解的值
            String annotationValue = MyBatisUtils.getParamAnnotationValue(parameter);
            if (StringUtil.isNotEmpty(annotationValue)) {
                paramName = annotationValue;
            }

            // 转换为下划线命名(假设数据库列名为下划线格式)
            String columnName = PropertyNameConverter.toLowerUnderline(paramName);

            ParameterInfo info = ParameterInfo.builder()
                    .originalName(paramName)
                    .shortName(paramName)
                    .columnName(columnName)
                    .qualifiedTypeName(paramType)
                    .simpleTypeName(paramType)
                    .build();

            parameters.add(info);
        }

        return parameters;
    }

    /**
     * 推测表名
     *
     * @param mapperClass Mapper类
     * @return 表名
     */
    private String guessTableName(PsiClass mapperClass) {
        if (mapperClass == null) {
            return "table_name";
        }

        String className = mapperClass.getName();
        if (className == null) {
            return "table_name";
        }

        // 移除Mapper/Repository/Dao等后缀
        String tableName = className.replaceAll("(Mapper|Repository|Dao)$", "");

        // 转换为下划线格式
        return PropertyNameConverter.toLowerUnderline(tableName);
    }

    /**
     * 添加标签到XML文件
     *
     * @param xmlFile    XML文件
     * @param tagContent 标签内容
     */
    private void addTagToXml(@NotNull XmlFile xmlFile, @NotNull String tagContent) {
        var rootTag = xmlFile.getRootTag();
        if (rootTag == null) {
            return;
        }

        // 创建新的标签
        var newTag = rootTag.createChildTag("temp", null, tagContent, false);

        // 添加到根标签末尾
        rootTag.addSubTag(newTag, false);
    }
}
