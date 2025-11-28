package io.github.easy.tools.comment;

import cn.hutool.core.util.StrUtil;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import io.github.easy.tools.ui.config.FeatureToggleService;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * 文件注释装饰器
 * <p>
 * 用于在IDEA项目视图中显示文件和文件夹的注释信息：
 * <ul>
 *     <li>文件夹：读取package-info.java的description描述</li>
 *     <li>Java文件：读取类的JavaDoc描述</li>
 * </ul>
 *
 * @author haijun
 * @since 1.0.0
 */
public class FileCommentDecorator implements ProjectViewNodeDecorator {

    /** 最大显示长度，超过此长度的注释会被截断 */
    private static final int MAX_DISPLAY_LENGTH = 20;
    
    /** 截断后显示的长度 */
    private static final int TRUNCATE_LENGTH = 17;
    
    /** Tooltip每行最大字符数 */
    private static final int TOOLTIP_LINE_LENGTH = 20;

    /**
     * 装饰项目视图节点，在文件/文件夹名称后添加注释信息
     *
     * @param node 项目视图节点
     * @param data 展示数据
     */
    @Override
    public void decorate(ProjectViewNode<?> node, PresentationData data) {
        // 检查功能是否启用
        if (!FeatureToggleService.getInstance().isFileCommentDecoratorEnabled()) {
            return;
        }
        
        // 获取虚拟文件
        VirtualFile virtualFile = node.getVirtualFile();
        if (virtualFile == null) {
            return;
        }

        // 获取项目
        Project project = node.getProject();
        if (project == null) {
            return;
        }

        // 如果是目录，读取package-info.java的注释
        if (virtualFile.isDirectory()) {
            decorateDirectory(virtualFile, project, data);
        } 
        // 如果是Java文件，读取类的注释
        else if ("java".equals(virtualFile.getExtension())) {
            decorateJavaFile(virtualFile, project, data);
        }
    }

    /**
     * 装饰目录节点，显示package-info.java的描述
     *
     * @param directory 目录虚拟文件
     * @param project   项目实例
     * @param data      展示数据
     */
    private void decorateDirectory(@NotNull VirtualFile directory, @NotNull Project project, @NotNull PresentationData data) {
        // 查找package-info.java文件
        VirtualFile packageInfo = directory.findChild("package-info.java");
        if (packageInfo == null) {
            return;
        }

        // 使用ReadAction确保线程安全地访问PSI元素
        ReadAction.run(() -> {
            // 获取PSI文件
            PsiFile psiFile = PsiManager.getInstance(project).findFile(packageInfo);
            if (!(psiFile instanceof PsiJavaFile javaFile)) {
                return;
            }

            // package-info.java的JavaDoc注释通常在文件最前面
            // 遍历文件的所有子元素，找到第一个JavaDoc注释
            for (PsiElement element : javaFile.getChildren()) {
                if (element instanceof PsiDocComment docComment) {
                    // 提取完整注释
                    extractFullCommentText(docComment).ifPresent(fullComment -> {
                        // 只有超过最大显示长度时才设置tooltip
                        if (fullComment.length() > MAX_DISPLAY_LENGTH) {
                            data.setTooltip(formatTooltip(fullComment));
                        }
                    });
                    // 提取截断注释用于显示
                    extractCommentText(docComment).ifPresent(description -> appendComment(data, description));
                    return;
                }
            }
        });
    }

    /**
     * 装饰Java文件节点，显示类的JavaDoc描述
     *
     * @param javaFile Java文件虚拟文件
     * @param project  项目实例
     * @param data     展示数据
     */
    private void decorateJavaFile(@NotNull VirtualFile javaFile, @NotNull Project project, @NotNull PresentationData data) {
        // 使用ReadAction确保线程安全地访问PSI元素
        ReadAction.run(() -> {
            // 获取PSI文件
            PsiFile psiFile = PsiManager.getInstance(project).findFile(javaFile);
            if (!(psiFile instanceof PsiJavaFile psiJavaFile)) {
                return;
            }

            // 获取第一个类
            PsiClass[] classes = psiJavaFile.getClasses();
            if (classes.length == 0) {
                return;
            }

            PsiClass psiClass = classes[0];
            
            // 提取完整注释
            Optional.ofNullable(psiClass.getDocComment())
                    .flatMap(this::extractFullCommentText)
                    .ifPresent(fullComment -> {
                        // 只有超过最大显示长度时才设置tooltip
                        if (fullComment.length() > MAX_DISPLAY_LENGTH) {
                            data.setTooltip(formatTooltip(fullComment));
                        }
                    });
            
            // 提取截断注释用于显示
            Optional.ofNullable(psiClass.getDocComment())
                    .flatMap(this::extractCommentText)
                    .ifPresent(description -> appendComment(data, description));
        });
    }

    /**
     * 从JavaDoc注释中提取描述文本（截断版本，用于项目视图显示）
     *
     * @param docComment JavaDoc注释
     * @return 描述文本（如果存在）
     */
    private Optional<String> extractCommentText(@NotNull PsiDocComment docComment) {
        return extractFullCommentText(docComment)
                .map(fullText -> {
                    // 限制长度，避免显示过长
                    if (fullText.length() > MAX_DISPLAY_LENGTH) {
                        return fullText.substring(0, TRUNCATE_LENGTH) + "...";
                    }
                    return fullText;
                });
    }

    /**
     * 从JavaDoc注释中提取完整的描述文本（不截断，用于tooltip显示）
     *
     * @param docComment JavaDoc注释
     * @return 完整描述文本（如果存在）
     */
    private Optional<String> extractFullCommentText(@NotNull PsiDocComment docComment) {
        // 获取注释的描述元素
        PsiElement[] descriptionElements = docComment.getDescriptionElements();
        if (descriptionElements.length == 0) {
            return Optional.empty();
        }

        // 拼接描述文本
        StringBuilder description = new StringBuilder();
        for (PsiElement element : descriptionElements) {
            String text = element.getText().trim();
            if (StrUtil.isNotBlank(text)) {
                description.append(text).append(" ");
            }
        }

        String result = description.toString().trim();
        if (StrUtil.isBlank(result)) {
            return Optional.empty();
        }

        // 清理HTML标签和多余空格
        result = result.replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", " ")
                .trim();

        return Optional.of(result);
    }

    /**
     * 在展示数据中追加注释文本
     *
     * @param data        展示数据
     * @param commentText 注释文本
     */
    private void appendComment(@NotNull PresentationData data, @NotNull String commentText) {
        if (StrUtil.isBlank(commentText)) {
            return;
        }

        // 在文件名后面添加注释（不添加//前缀）
        String locationString = data.getLocationString();
        String newLocation;
        if (StrUtil.isNotBlank(locationString)) {
            newLocation = locationString + " " + commentText;
        } else {
            newLocation = commentText;
        }
        data.setLocationString(newLocation);
    }

    /**
     * 格式化Tooltip显示，每行最多20个字符，自动换行
     *
     * @param text 原始文本
     * @return 格式化后的文本
     */
    private String formatTooltip(@NotNull String text) {
        if (text.length() <= TOOLTIP_LINE_LENGTH) {
            return text;
        }

        StringBuilder formatted = new StringBuilder();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + TOOLTIP_LINE_LENGTH, text.length());
            
            // 尽量在空格处断开，避免截断单词
            if (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
                // 向前查找最近的空格
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }
            
            formatted.append(text, start, end);
            if (end < text.length()) {
                formatted.append("\n");
            }
            
            start = end;
            // 跳过空格
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }

        return formatted.toString();
    }
}
