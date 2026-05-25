package io.github.ideatools.service.doc.processor;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.javadoc.PsiDocComment;
import io.github.ideatools.constants.PromptConstants;
import io.github.ideatools.ui.config.DocConfigService;
import io.github.ideatools.utils.NotificationUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p> AI注释处理器 </p>
 * <p>
 * 统一的AI注释处理器，负责根据元素类型获取提示词、提取AI响应并更新文档注释
 * </p>
 * <p>
 * 主要功能：
 * - 根据元素类型（类/方法/字段）获取对应的提示词模板
 * - 从AI响应中提取标准JavaDoc注释
 * - 更新PSI元素的文档注释
 * - 提供占位符注释
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 12:00
 * @since 1.0.0
 */
public class AICommentProcessor {

    /**
     * 根据元素类型获取对应的提示词模板
     * <p>
     * 优先使用配置中的自定义提示词，如果配置为空则使用默认提示词
     * </p>
     *
     * @param element PSI元素
     * @return 提示词模板
     * @since 1.0.0
     */
    public String getPromptByType(PsiElement element) {
        DocConfigService config = DocConfigService.getInstance(element.getProject());

        if (element instanceof PsiClass) {
            return StringUtil.isNotEmpty(config.classPrompt)
                    ? config.classPrompt
                    : PromptConstants.DEFAULT_CLASS_PROMPT;
        } else if (element instanceof PsiMethod) {
            return StringUtil.isNotEmpty(config.methodPrompt)
                    ? config.methodPrompt
                    : PromptConstants.DEFAULT_METHOD_PROMPT;
        } else if (element instanceof PsiField) {
            return StringUtil.isNotEmpty(config.fieldPrompt)
                    ? config.fieldPrompt
                    : PromptConstants.DEFAULT_FIELD_PROMPT;
        } else if (element instanceof PsiPackageStatement) {
            return StringUtil.isNotEmpty(config.packagePrompt)
                    ? config.packagePrompt
                    : PromptConstants.DEFAULT_PACKAGE_PROMPT;
        }
        return "";
    }

    /**
     * 更新元素的文档注释
     * <p>
     * 在写操作中更新或添加PSI元素的JavaDoc注释
     * </p>
     *
     * @param element 要更新的PSI元素
     * @param commentText 注释文本内容
     * @since 1.0.0
     */
    public void updateDoc(PsiElement element, String commentText) {
        try {
            Project project = element.getProject();

            // 使用WriteCommandAction确保在正确的上下文中修改PSI
            WriteCommandAction.runWriteCommandAction(project, () -> {
                // 创建新的文档注释
                PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
                // 先规范化注释格式，再处理泛型
                String normalized = this.normalizeJavaDocFormat(commentText);
                String sanitized = this.sanitizeGenerics(normalized);

                // 验证注释格式是否正确
                if (!this.isValidJavaDocFormat(sanitized)) {
                    // 如果格式不正确，尝试修复
                    sanitized = this.fixJavaDocFormat(sanitized);
                }

                PsiElement docCommentFromText = elementFactory.createDocCommentFromText(sanitized);

                // 特殊处理 PsiPackageStatement（package-info.java 文件中的包声明）
                if (element instanceof PsiPackageStatement psiPackageStatement) {
                    this.updatePackageDoc(psiPackageStatement, docCommentFromText, project);
                    return;
                }

                // 获取现有的文档注释
                PsiDocComment existingDocComment = null;
                if (element instanceof PsiJavaDocumentedElement documentedElement) {
                    existingDocComment = documentedElement.getDocComment();
                }

                // 更新或添加注释
                if (existingDocComment != null) {
                    existingDocComment.replace(docCommentFromText);
                } else {
                    element.addBefore(docCommentFromText, element.getFirstChild());
                }
            });
        } catch (Exception e) {
            NotificationUtil.showError(element.getProject(), "更新注释失败: " + e.getMessage());
        }
    }

    /**
     * 更新 package-info.java 文件中的包注释
     * <p>
     * PsiPackageStatement 不是 PsiJavaDocumentedElement 的子类，需要特殊处理。
     * 检查文件开头是否有现有的注释，如果有则替换，否则在 package 语句之前添加。
     * </p>
     *
     * @param packageStatement  包声明元素
     * @param docCommentFromText 新的文档注释
     * @param project           项目实例
     * @since 1.0.0
     */
    private void updatePackageDoc(PsiPackageStatement packageStatement, PsiElement docCommentFromText, Project project) {
        PsiFile containingFile = packageStatement.getContainingFile();
        if (containingFile == null) {
            return;
        }

        // 检查文件开头是否有现有的 PsiDocComment
        PsiElement firstChild = containingFile.getFirstChild();
        if (firstChild instanceof PsiDocComment existingDoc) {
            // 替换已存在的注释
            existingDoc.replace(docCommentFromText);
        } else {
            // 在文件开头（package 语句之前）添加新注释
            containingFile.addBefore(docCommentFromText, packageStatement);

            // 在注释和 package 语句之间添加换行符
            PsiElement whiteSpace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");
            containingFile.addBefore(whiteSpace, packageStatement);
        }
    }

    /**
     * 规范化JavaDoc注释格式
     * <p>
     * 确保注释以 /** 开始，以 *&#47; 结束
     * 修复常见的格式问题
     * </p>
     *
     * @param commentText 注释文本
     * @return 规范化后的注释文本
     * @since 1.0.0
     */
    private String normalizeJavaDocFormat(String commentText) {
        if (StringUtil.isEmpty(commentText)) {
            return commentText;
        }

        String trimmed = commentText.trim();

        // 如果注释已经以 /** 开始和 */ 结束，直接返回
        if (trimmed.startsWith("/**") && trimmed.endsWith("*/")) {
            return trimmed;
        }

        // 如果只有 /** 开始但没有 */ 结束，添加结束标记
        if (trimmed.startsWith("/**") && !trimmed.endsWith("*/")) {
            // 确保最后一行有 * 前缀
            if (!trimmed.endsWith("\n *")) {
                return trimmed + "\n */";
            }
            return trimmed + "/";
        }

        // 如果没有 /** 开始但有 */ 结束，添加开始标记
        if (!trimmed.startsWith("/**") && trimmed.endsWith("*/")) {
            return "/**\n * " + trimmed.substring(0, trimmed.length() - 2).trim() + "\n */";
        }

        // 如果都没有，包装成JavaDoc格式
        return this.wrapAsJavaDoc(trimmed);
    }

    /**
     * 验证JavaDoc注释格式是否正确
     *
     * @param commentText 注释文本
     * @return 是否是有效的JavaDoc格式
     * @since 1.0.0
     */
    private boolean isValidJavaDocFormat(String commentText) {
        if (StringUtil.isEmpty(commentText)) {
            return false;
        }

        String trimmed = commentText.trim();

        // 必须以 /** 开始
        if (!trimmed.startsWith("/**")) {
            return false;
        }

        // 必须以 */ 结束
        if (!trimmed.endsWith("*/")) {
            return false;
        }

        // 检查是否有未闭合的标签（简单检查）
        // 确保没有单独的 @email 后面没有值导致的问题
        String[] lines = trimmed.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            // 检查是否是空的 @email 或 @param 标签
            if (trimmedLine.matches("^\\*\\s*@(email|author|date|version|since)\\s*$")) {
                return false;
            }
        }

        return true;
    }

    /**
     * 修复JavaDoc注释格式
     *
     * @param commentText 注释文本
     * @return 修复后的注释文本
     * @since 1.0.0
     */
    private String fixJavaDocFormat(String commentText) {
        if (StringUtil.isEmpty(commentText)) {
            return "/**\n *\n */";
        }

        StringBuilder result = new StringBuilder();
        result.append("/**\n");

        String[] lines = commentText.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim();

            // 跳过 /** 和 */ 行
            if (trimmedLine.equals("/**") || trimmedLine.equals("*/")) {
                continue;
            }

            // 移除开头的 *
            if (trimmedLine.startsWith("*")) {
                trimmedLine = trimmedLine.substring(1).trim();
            }

            // 修复空的标签值
            if (trimmedLine.matches("^@(email|author|date|version|since)\\s*$")) {
                // 为空标签添加默认值
                if (trimmedLine.startsWith("@email")) {
                    trimmedLine = "@email \"mailto:unknown@email.com\"";
                } else if (trimmedLine.startsWith("@author")) {
                    trimmedLine = "@author unknown";
                } else if (trimmedLine.startsWith("@date")) {
                    trimmedLine = "@date " + java.time.LocalDate.now().toString();
                } else if (trimmedLine.startsWith("@version")) {
                    trimmedLine = "@version 1.0.0";
                } else if (trimmedLine.startsWith("@since")) {
                    trimmedLine = "@since 1.0.0";
                }
            }

            result.append(" * ").append(trimmedLine).append("\n");
        }

        result.append(" */");
        return result.toString();
    }

    /**
     * 从AI响应中提取标准JavaDoc注释内容
     * <p>
     * 处理流程：
     * - 清理JSON转义字符和Unicode编码
     * - 从JSON响应中提取content字段
     * - 移除代码块标记
     * - 提取JavaDoc格式注释
     * - 如果没有标准格式则包装为JavaDoc注释
     * - 规范化并验证注释格式
     * </p>
     *
     * @param responseContent AI响应内容
     * @return 提取的标准JavaDoc注释
     * @since 1.0.0
     */
    public String extractCommentFromResponse(String responseContent) {
        if (StringUtil.isEmpty(responseContent)) {
            return "";
        }

        // 处理JSON转义字符和Unicode编码
        responseContent = this.cleanEscapeCharacters(responseContent);

        // 尝试从JSON响应中提取内容
        if (responseContent.trim().startsWith("{")) {
            responseContent = this.extractContentFromJson(responseContent);
        }

        // 移除代码块标记
        responseContent = this.removeCodeBlockMarkers(responseContent);

        // 提取JavaDoc注释
        String javaDocComment = this.extractJavaDocComment(responseContent);
        if (StringUtil.isNotEmpty(javaDocComment)) {
            // 规范化注释格式
            return this.normalizeJavaDocFormat(javaDocComment);
        }

        // 如果没有找到标准格式，包装成JavaDoc注释
        return this.wrapAsJavaDoc(responseContent);
    }

    /**
     * 获取占位符注释
     * <p>
     * 用于在AI生成注释期间显示的临时占位符
     * </p>
     *
     * @param message 消息内容
     * @return 占位符JavaDoc注释
     * @since 1.0.0
     */
    public String getPlaceholderDoc(String message) {
        return """
                /**
                 * %s
                 */
                """.formatted(message);
    }

    /**
     * 清理转义字符和Unicode编码
     *
     * @param content 原始内容
     * @return 清理后的内容
     * @since 1.0.0
     */
    private String cleanEscapeCharacters(String content) {
        return content
                // 处理换行符
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                // 处理引号和反斜杠
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\\\", "\\")
                // 处理Unicode编码
                .replace("\\u003c", "<").replace("\\u003C", "<")
                .replace("\\u003e", ">").replace("\\u003E", ">")
                .replace("\\u0026", "&")
                .replace("\\u003d", "=").replace("\\u003D", "=")
                .replace("\\u0027", "'")
                // 处理已解码的Unicode
                .replace("\u003c", "<").replace("\u003C", "<")
                .replace("\u003e", ">").replace("\u003E", ">")
                .replace("\u0026", "&")
                .replace("\u003d", "=").replace("\u003D", "=")
                .replace("\u0027", "'");
    }

    /**
     * 从JSON响应中提取content字段内容
     *
     * @param jsonResponse JSON格式的响应
     * @return 提取的内容
     * @since 1.0.0
     */
    private String extractContentFromJson(String jsonResponse) {
        try {
            Pattern pattern = Pattern.compile("\"content\"\\s*:\\s*\"([^\"]*)\"");
            Matcher matcher = pattern.matcher(jsonResponse);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            // 匹配失败则返回原始内容
        }
        return jsonResponse;
    }

    /**
     * 移除代码块标记
     *
     * @param content 内容
     * @return 移除标记后的内容
     * @since 1.0.0
     */
    private String removeCodeBlockMarkers(String content) {
        if (!content.contains("```")) {
            return content;
        }

        int start = content.indexOf("```");
        int end = content.indexOf("```", start + 3);
        if (start >= 0 && end > start) {
            String codeBlock = content.substring(start + 3, end).trim();
            // 移除语言标识（如java）
            if (codeBlock.startsWith("java")) {
                codeBlock = codeBlock.substring(4).trim();
            }
            return codeBlock;
        }
        return content;
    }

    /**
     * 提取JavaDoc注释
     *
     * @param content 内容
     * @return JavaDoc注释，如果找不到则返回空字符串
     * @since 1.0.0
     */
    private String extractJavaDocComment(String content) {
        int docStart = content.indexOf("/**");
        int docEnd = content.indexOf("*/", docStart);

        if (docStart >= 0 && docEnd > docStart) {
            return content.substring(docStart, docEnd + 2);
        } else if (docStart >= 0) {
            return content.substring(docStart);
        }

        // 如果内容看起来像注释，直接返回
        if (content.trim().startsWith("/**") || content.contains("*")) {
            return content;
        }

        return "";
    }

    /**
     * 将内容包装为JavaDoc格式
     *
     * @param content 内容
     * @return JavaDoc格式注释
     * @since 1.0.0
     */
    /**
     * 规范化JavaDoc中泛型与尖括号的显示，避免IDE将其识别为HTML标签
     * 处理策略：
     * - 在非标签行（非以"* @"开头）中，将形如 List<A> / Map<K,V> / Foo<Bar> 包裹为 {@code ...}
     * - 在残留的裸尖括号（如 <T>、<E extends X>）且非标准HTML标签时，包裹为 {@literal <...>}
     * - 保留合法JavaDoc/HTML标签（如 <p>、<code>、<pre> 等）不改动
     */
    private String wrapAsJavaDoc(String content) {
        return """
                /**
                 * %s
                 */
                """.formatted(content.replace("\n", "\n * "));
    }

    private String sanitizeGenerics(String commentText) {
        if (StringUtil.isEmpty(commentText)) {
            return commentText;
        }
        String[] lines = commentText.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            // 标签行保持原样（例如 * @param）
            if (trimmed.startsWith("* @") || trimmed.startsWith("*@")) {
                out.append(line).append("\n");
                continue;
            }
            String processed = line;
            // 1) 包裹通用泛型表达式 token<...>
            processed = processed.replaceAll("([A-Za-z0-9_.$]+<[^>]+>)", "{@code $1}");
            // 2) 对非标准HTML尖括号进行{@literal}处理（排除常见标签）
            processed = processed.replaceAll("<(?!/?(?:p|code|pre|br|li|ul|ol|i|b|em|strong|a)\b)([^>]+)>", "{@literal <$1>}");
            out.append(processed).append("\n");
        }
        // 移除最后一个多余的换行，保持原始格式
        if (!out.isEmpty()) {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }
}
