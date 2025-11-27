package io.github.easy.tools.service.doc;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JavaDoc 非标准标签注册服务
 * <p>
 * 该服务用于在生成doc注释时，从生成的注释内容中提取非标准标签，
 * 并将这些标签注册到 JavadocDeclarationInspection 中，避免IDEA产生警告。
 * </p>
 * <p>
 * 相比静态注册，这种方式更灵活，只在实际生成注释时才注册需要的标签。
 * </p>
 *
 * @author system
 * @version 1.0.0
 * @since 1.0.0
 */
@Service
public final class JavaDocTagRegistrarService {

    /**
     * JavaDoc标准标签集合
     * <p>
     * 这些标签是Java官方支持的标准标签，不需要额外注册
     * </p>
     */
    private static final Set<String> STANDARD_TAGS = Set.of(
            "author", "version", "since", "see", "deprecated",
            "param", "return", "throws", "exception",
            "serial", "serialField", "serialData",
            "link", "linkplain", "value", "code", "literal",
            "docRoot", "inheritDoc"
    );

    /**
     * ADDITIONAL_TAGS 字段名称
     */
    private static final String FIELD_ADDITIONAL_TAGS = "ADDITIONAL_TAGS";

    /**
     * 获取服务实例
     *
     * @return JavaDocTagRegistrarService 实例
     */
    public static JavaDocTagRegistrarService getInstance() {
        return ApplicationManager.getApplication().getService(JavaDocTagRegistrarService.class);
    }

    /**
     * 从生成的注释内容中提取并注册非标准标签
     * <p>
     * 该方法会：
     * <ol>
     *   <li>解析注释内容，提取所有使用的标签</li>
     *   <li>过滤掉标准标签，只保留非标准标签</li>
     *   <li>将非标准标签注册到 JavadocDeclarationInspection</li>
     * </ol>
     * </p>
     *
     * @param project    项目对象
     * @param docComment 生成的注释内容
     */
    public void registerTagsFromComment(@NotNull Project project, @NotNull PsiDocComment docComment) {
        try {
            // 1. 提取注释中的所有标签
            Set<String> tags = extractTagsFromComment(docComment);
            if (tags.isEmpty()) {
                return;
            }

            // 2. 过滤掉标准标签
            Set<String> nonStandardTags = tags.stream()
                    .filter(tag -> !STANDARD_TAGS.contains(tag.toLowerCase()))
                    .collect(Collectors.toSet());

            if (nonStandardTags.isEmpty()) {
                return;
            }

            // 3. 注册非标准标签
            registerTags(project, nonStandardTags);

        } catch (Exception e) {
            // 静默处理异常，避免影响注释生成
        }
    }

    /**
     * 从注释内容中提取所有标签
     * <p>
     * 通过 PSI 元素解析注释，提取所有 @ 标签名称
     * </p>
     *
     * @param docComment 注释内容
     * @return 标签集合（小写、去重）
     */
    private Set<String> extractTagsFromComment(@NotNull PsiDocComment docComment) {
        Set<String> tags = new HashSet<>();

        // 获取所有的标签元素
        PsiDocTag[] docTags = docComment.getTags();
        for (PsiDocTag docTag : docTags) {
            String tagName = docTag.getName();
            if (!tagName.isEmpty()) {
                // 统一转换为小写
                tags.add(tagName.toLowerCase());
            }
        }

        return tags;
    }

    /**
     * 将标签注册到 JavadocDeclarationInspection
     * <p>
     * 该方法通过反射访问和修改 ADDITIONAL_TAGS 字段，
     * 将新标签追加到已有的标签列表中，并通知 IDEA 配置已更改
     * </p>
     *
     * @param project   项目对象
     * @param tagsToAdd 要注册的标签集合
     */
    private void registerTags(@NotNull Project project, @NotNull Set<String> tagsToAdd) {
        try {
            // 获取项目的检查配置管理器
            ProjectInspectionProfileManager profileManager =
                    ProjectInspectionProfileManager.getInstance(project);

            // 获取 JavadocDeclarationInspection 实例
            JavadocDeclarationInspection inspection = getInspectionTool(project);
            if (inspection == null) {
                return;
            }

            // 获取当前已注册的标签
            String currentTags = getAdditionalTags(inspection);
            Set<String> existingTags = parseTagsString(currentTags);

            // 合并标签（去重）
            existingTags.addAll(tagsToAdd);

            // 重新设置标签
            String newTagsString = String.join(",", existingTags);
            setAdditionalTags(inspection, newTagsString);

            // 通知配置已更改
            profileManager.fireProfileChanged();

            // 重启代码分析，使更改立即生效
            DaemonCodeAnalyzer.getInstance(project).restart();

        } catch (Exception e) {
            // 静默处理异常
        }
    }

    /**
     * 获取 JavadocDeclarationInspection 工具实例
     *
     * @param project 项目对象
     * @return JavadocDeclarationInspection 实例，如果获取失败返回 null
     */
    private JavadocDeclarationInspection getInspectionTool(@NotNull Project project) {
        try {
            ProjectInspectionProfileManager profileManager =
                    ProjectInspectionProfileManager.getInstance(project);

            InspectionProfile profile = profileManager.getCurrentProfile();

            InspectionToolWrapper<?, ?> toolWrapper =
                    profile.getInspectionTool("JavadocDeclaration", project);

            if (toolWrapper != null) {
                Object tool = toolWrapper.getTool();
                if (tool instanceof JavadocDeclarationInspection) {
                    return (JavadocDeclarationInspection) tool;
                }
            }
        } catch (Exception e) {
            // 获取失败
        }
        return null;
    }

    /**
     * 获取当前已注册的标签字符串
     * <p>
     * 通过反射访问 ADDITIONAL_TAGS 字段
     * </p>
     *
     * @param inspection JavadocDeclarationInspection 实例
     * @return 当前标签字符串，如果获取失败返回空字符串
     */
    private String getAdditionalTags(@NotNull JavadocDeclarationInspection inspection) {
        try {
            Field field = JavadocDeclarationInspection.class.getDeclaredField(FIELD_ADDITIONAL_TAGS);
            field.setAccessible(true);
            Object value = field.get(inspection);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 设置标签字符串
     * <p>
     * 通过反射修改 ADDITIONAL_TAGS 字段
     * </p>
     *
     * @param inspection JavadocDeclarationInspection 实例
     * @param tagsString 新的标签字符串
     */
    private void setAdditionalTags(@NotNull JavadocDeclarationInspection inspection,
                                   @NotNull String tagsString) {
        try {
            Field field = JavadocDeclarationInspection.class.getDeclaredField(FIELD_ADDITIONAL_TAGS);
            field.setAccessible(true);
            field.set(inspection, tagsString);
        } catch (Exception e) {
            // 设置失败，静默处理
        }
    }

    /**
     * 解析标签字符串为集合
     * <p>
     * 标签字符串格式：逗号分隔，如 "date,email,xxx"
     * </p>
     *
     * @param tagsString 标签字符串
     * @return 标签集合（小写、去重）
     */
    private Set<String> parseTagsString(String tagsString) {
        if (tagsString == null || tagsString.trim().isEmpty()) {
            return new HashSet<>();
        }

        return Arrays.stream(tagsString.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }
}