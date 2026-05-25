package io.github.ideatools.action.mybatis;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import io.github.ideatools.action.conversion.PropertyNameConverter;
import io.github.ideatools.entity.doc.ParameterInfo;
import io.github.ideatools.service.doc.velocity.VelocityTemplateRenderer;
import io.github.ideatools.service.mybatis.MapperXmlCacheService;
import io.github.ideatools.utils.MyBatisUtils;
import io.github.ideatools.utils.NotificationUtil;
import io.github.ideatools.utils.StrConverter;
import org.apache.velocity.VelocityContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis Action 抽象基类
 * <p>
 * 提供通用的Mapper方法查找、XML文件定位、参数解析和标签生成功能。
 * 所有MyBatis相关的Action都应该继承此类以复用通用逻辑。
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @date 2025-12-17 10:00:00
 * @since 1.0.0
 */
public abstract class AbstractMyBatisAction extends AnAction {

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
    protected static final VelocityTemplateRenderer VELOCITY_RENDERER = new VelocityTemplateRenderer();

    /**
     * 执行动作
     *
     * @param e 动作事件
     * @since 1.0.0
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (editor == null || psiFile == null) {
            return;
        }

        // 获取光标位置的方法
        PsiMethod method = this.getMethodAtCaret(psiFile, editor);
        if (method == null) {
            NotificationUtil.showWarning(project, "请将光标放在Mapper接口方法上");
            return;
        }

        // 检查方法所在的类是否为Mapper接口
        PsiClass mapperClass = method.getContainingClass();
        if (mapperClass == null || !mapperClass.isInterface()) {
            NotificationUtil.showWarning(project, "当前方法不在Mapper接口中");
            return;
        }

        if (!this.hasMapperAnnotation(mapperClass)) {
            NotificationUtil.showWarning(project, "当前接口没有@Mapper注解");
            return;
        }

        // 查找对应的XML文件
        XmlFile xmlFile = this.findMapperXmlFile(mapperClass);
        if (xmlFile == null) {
            NotificationUtil.showWarning(project, "未找到对应的Mapper XML文件");
            return;
        }

        // 检查XML文件中是否已存在该方法的标签
        if (MyBatisUtils.findSqlTagByMethodName(xmlFile, method.getName()) != null) {
            NotificationUtil.showWarning(project, "该方法的SQL标签已存在");
            return;
        }

        // 执行具体的标签生成逻辑
        this.generateSqlTag(project, xmlFile, method);
    }

    /**
     * 指定在后台线程更新动作状态
     * <p>
     * 由于update方法需要访问PSI数据，必须在后台线程执行
     * </p>
     *
     * @return 后台线程
     * @since 1.0.0
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * 更新动作可见性
     * <p>
     * 仅在Java文件的Mapper接口方法上可见
     * </p>
     *
     * @param e 动作事件
     * @since 1.0.0
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        boolean visible = false;

        if (project != null && editor != null && psiFile != null) {
            PsiMethod method = this.getMethodAtCaret(psiFile, editor);
            if (method != null) {
                PsiClass mapperClass = method.getContainingClass();
                if (mapperClass != null && mapperClass.isInterface()) {
                    visible = this.hasMapperAnnotation(mapperClass);
                }
            }
        }

        e.getPresentation().setEnabledAndVisible(visible);
    }

    /**
     * 获取光标位置的方法
     *
     * @param psiFile PSI文件
     * @param editor  编辑器
     * @return 方法元素,未找到返回null
     * @since 1.0.0
     */
    @Nullable
    protected PsiMethod getMethodAtCaret(@NotNull PsiFile psiFile, @NotNull Editor editor) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = psiFile.findElementAt(offset);

        while (element != null) {
            if (element instanceof PsiMethod) {
                return (PsiMethod) element;
            }
            element = element.getParent();
        }

        return null;
    }

    /**
     * 检查类是否有@Mapper注解
     *
     * @param psiClass 类
     * @return true如果有@Mapper注解
     * @since 1.0.0
     */
    protected boolean hasMapperAnnotation(@NotNull PsiClass psiClass) {
        return psiClass.hasAnnotation(MAPPER_ANNOTATION) ||
                psiClass.hasAnnotation(SPRING_MAPPER_ANNOTATION);
    }

    /**
     * 查找Mapper接口对应的XML文件
     *
     * @param mapperClass Mapper接口类
     * @return XML文件,未找到返回null
     * @since 1.0.0
     */
    @Nullable
    protected XmlFile findMapperXmlFile(@NotNull PsiClass mapperClass) {
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
     * 解析方法参数信息
     *
     * @param method 方法
     * @return 参数信息列表
     * @since 1.0.0
     */
    @NotNull
    protected List<ParameterInfo> parseParameters(@NotNull PsiMethod method) {
        List<ParameterInfo> parameters = new ArrayList<>();

        for (PsiParameter parameter : method.getParameterList().getParameters()) {
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
     * 获取模板文件路径
     * <p>
     * 子类需要实现此方法来指定使用哪个Velocity模板文件
     * </p>
     *
     * @return 模板文件路径
     * @since 1.0.0
     */
    @NotNull
    protected abstract String getTemplatePath();

    /**
     * 生成SQL标签
     * <p>
     * 子类可以重写此方法来自定义生成逻辑
     * </p>
     *
     * @param project 项目
     * @param xmlFile XML文件
     * @param method  方法
     * @since 1.0.0
     */
    protected void generateSqlTag(@NotNull Project project,
                                   @NotNull XmlFile xmlFile,
                                   @NotNull PsiMethod method) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                // 准备模板上下文
                VelocityContext context = new VelocityContext();
                context.put("methodId", method.getName());
                context.put("tableName", this.guessTableName(method.getContainingClass()));

                // 添加参数类型信息
                PsiParameter[] params = method.getParameterList().getParameters();
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
     * 推测表名
     * <p>
     * 根据Mapper接口名称推测对应的数据库表名
     * 例如: UserMapper -> user
     * </p>
     *
     * @param mapperClass Mapper类
     * @return 表名
     * @since 1.0.0
     */
    @NotNull
    protected String guessTableName(@Nullable PsiClass mapperClass) {
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
     * @since 1.0.0
     */
    protected void addTagToXml(@NotNull XmlFile xmlFile, @NotNull String tagContent) {
        XmlTag rootTag = xmlFile.getRootTag();
        if (rootTag == null) {
            return;
        }

        // 创建新的标签
        XmlTag newTag = rootTag.createChildTag("temp", null, tagContent, false);

        // 添加到根标签末尾
        rootTag.addSubTag(newTag, false);
    }
}
