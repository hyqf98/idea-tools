package io.github.easy.tools.service.doc.ai;

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
import com.intellij.psi.javadoc.PsiDocComment;
import io.github.easy.tools.utils.NotificationUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java AI文档处理器实现
 * <p>
 * 处理Java代码元素的AI文档生成
 * </p>
 */
public class JavaAiDocProcessor implements AIDocProcessor {

    /** 类提示词模板 */
    private static final String CLASS_PROMPT = """
            请根据注释模板以及上下文参数生成标准的JavaDoc注释。
            
            【重要输出要求】
            1. 必须使用UTF-8编码输出，禁止使用任何Unicode转义字符（如\u003c等）
            2. 直接输出尖括号<>、引号等特殊字符，不要转义
            3. 严格按照JavaDoc标准格式输出，包含/** 开始和 */ 结束
            4. 只返回JavaDoc注释内容，不要包含代码块标记（```java等）
            5. 使用<p>标签分隔不同段落，保持JavaDoc格式规范
            
            【格式规范】
            - 第一段：类的核心功能概述（单独一段）
            - 使用<p>标签开始新段落
            - 功能特性列举时，每个特性使用 "- " 开头，单独一行
            - 使用<p>标签分隔示例代码段
            - 示例代码使用简洁的单行或多行格式
            
            【内容要求】
            请根据代码信息总结归纳并填充到模板的${description}位置，内容包括：
            1. 第一段：用一句话精简总结类的核心功能和用途
            2. 第二段（<p>后）：列举类的主要特性或功能点，每个特性单独一行，使用 "- " 开头
            3. 第三段（<p>后）：提供简单的使用示例代码
            4. 禁止在注释中详细说明类的各个方法和属性
            
            注释模板：
            {template}
            
            上下文参数：
            {context}
            
            代码信息：
            {code}
            """;

    /** 方法提示词模板 */
    private static final String METHOD_PROMPT = """
            请根据注释模板以及上下文参数生成标准的JavaDoc注释。
            
            【重要输出要求】
            1. 必须使用UTF-8编码输出，禁止使用任何Unicode转义字符（如\u003c等）
            2. 直接输出尖括号<>、引号等特殊字符，不要转义
            3. 严格按照JavaDoc标准格式输出，包含/** 开始和 */ 结束
            4. 只返回JavaDoc注释内容，不要包含代码块标记（```java等）或方法体
            5. 使用<p>标签分隔不同段落，保持JavaDoc格式规范
            
            【格式规范】
            - 第一段：方法的核心功能概述（单独一段）
            - 使用<p>标签开始新段落
            - 执行步骤列举时，每个步骤使用 "- " 开头，单独一行
            - 使用<p>标签分隔示例代码段
            - 示例代码使用简洁的单行格式
            
            【内容要求】
            请根据代码信息总结归纳并填充到模板的${description}位置，内容包括：
            1. 第一段：用一句话精简总结方法的核心功能
            2. 第二段（<p>后）：分点列出方法的执行步骤，每个步骤单独一行，使用 "- " 开头
            3. 第三段（<p>后）：提供简单的方法调用示例代码
            4. 如果方法嵌套调用其他方法，无需详细说明嵌套方法的逻辑
            
            注释模板：
            {template}
            
            上下文参数：
            {context}
            
            代码信息：
            {code}
            """;

    /** 字段提示词模板 */
    private static final String FIELD_PROMPT = """
            请根据注释模板以及上下文参数生成标准的JavaDoc注释。
            
            【重要输出要求】
            1. 必须使用UTF-8编码输出，禁止使用任何Unicode转义字符（如\u003c等）
            2. 直接输出尖括号<>、引号等特殊字符，不要转义
            3. 严格按照JavaDoc标准格式输出，包含/** 开始和 */ 结束
            4. 只返回JavaDoc注释内容，不要包含代码块标记（```java等）
            5. 字段注释通常简洁，一般不需要使用<p>分段
            
            【内容要求】
            请根据代码信息总结归纳并填充到模板的${description}位置，内容包括：
            1. 对字段名称进行中文翻译或功能描述
            2. 如有必要，简要说明字段的用途或取值范围
            3. 如果模板有其他占位符，按照上下文参数进行填充
            
            注释模板：
            {template}
            
            上下文参数：
            {context}
            
            代码信息：
            {code}
            """;

    @Override
    public String getPromptByType(PsiElement element) {
        if (element instanceof PsiClass) {
            return CLASS_PROMPT;
        } else if (element instanceof PsiMethod) {
            return METHOD_PROMPT;
        } else if (element instanceof PsiField) {
            return FIELD_PROMPT;
        } else {
            return "";
        }
    }

    @Override
    public void updateDoc(PsiElement element, String commentText) {
        try {
            // 获取元素所在的文件和项目
            PsiFile file = element.getContainingFile();
            if (file != null) {
                Project project = file.getProject();

                // 使用WriteCommandAction确保在正确的上下文中修改PSI
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    // 创建新的文档注释
                    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
                    PsiElement docCommentFromText = elementFactory.createDocCommentFromText(commentText);

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
            }
        } catch (Exception e) {
            // 使用IDEA的通知系统显示错误信息
            NotificationUtil.showError(element.getProject(), "更新注释失败: " + e.getMessage());
        }
    }

    @Override
    public String extractCommentFromResponse(String responseContent) {
        if (responseContent == null || responseContent.isEmpty()) {
            return "";
        }

        // 处理JSON转义字符和Unicode编码
        responseContent = responseContent
                // 处理换行符
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                // 处理引号和反斜杠
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\\\", "\\")
                // 处理Unicode编码 - 小写形式
                .replace("\\u003c", "<")
                .replace("\\u003e", ">")
                .replace("\\u0026", "&")
                .replace("\\u003d", "=")
                .replace("\\u0027", "'")
                // 处理Unicode编码 - 大写形式
                .replace("\\u003C", "<")
                .replace("\\u003E", ">")
                .replace("\\u0026", "&")
                .replace("\\u003D", "=")
                .replace("\\u0027", "'");

        // 处理已经解码但仍保留的Unicode编码（不带反斜杠）
        responseContent = responseContent
                .replace("\u003c", "<")
                .replace("\u003e", ">")
                .replace("\u003C", "<")
                .replace("\u003E", ">")
                .replace("\u0026", "&")
                .replace("\u003d", "=")
                .replace("\u003D", "=")
                .replace("\u0027", "'");

        // 尝试从JSON响应中提取内容（如果响应是JSON格式）
        try {
            // 检查是否是JSON格式的响应
            if (responseContent.trim().startsWith("{")) {
                // 尝试解析JSON并提取choices[0].message.content
                responseContent = this.extractContentFromJsonResponse(responseContent);
            }
        } catch (Exception e) {
            // 如果解析失败，继续使用原始内容
        }

        // 尝试从代码块中提取注释
        if (responseContent.contains("```") || responseContent.contains("```java")) {
            int start = responseContent.indexOf("```");
            // 查找下一个```的位置
            int end = responseContent.indexOf("```", start + 3);
            if (start >= 0 && end > start) {
                String codeBlock = responseContent.substring(start + 3, end).trim();
                // 如果代码块以java开头，则移除
                if (codeBlock.startsWith("java")) {
                    codeBlock = codeBlock.substring(4).trim();
                }
                responseContent = codeBlock;
            }
        }

        // 按照JavaDoc标准提取注释部分
        // 查找 /** 和 */ 之间的内容
        int docStart = responseContent.indexOf("/**");
        int docEnd = responseContent.indexOf("*/", docStart);

        if (docStart >= 0 && docEnd > docStart) {
            // 提取完整的JavaDoc注释
            return responseContent.substring(docStart, docEnd + 2);
        } else if (docStart >= 0) {
            // 只找到了开始标记，提取从开始到字符串末尾的部分
            return responseContent.substring(docStart);
        }

        // 如果没有找到标准JavaDoc格式，但内容看起来像注释，则直接返回
        if (responseContent.trim().startsWith("/**") || responseContent.contains("*")) {
            return responseContent;
        }

        // 如果内容不包含注释格式，包装成标准注释格式
        return """
                /**
                 * %s
                 */
                """.formatted(responseContent.replace("\n", "\n * "));
    }

    /**
     * 从JSON响应中提取内容
     *
     * @param jsonResponse JSON格式的响应
     * @return 提取的内容
     */
    private String extractContentFromJsonResponse(String jsonResponse) {
        try {
            // 使用正则表达式提取content字段的内容
            // 匹配 "content": "..." 格式
            Pattern pattern = Pattern.compile("\"content\"\\s*:\\s*\"([^\"]*)\"");
            Matcher matcher = pattern.matcher(jsonResponse);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            // 如果正则表达式匹配失败，返回原始内容
        }
        return jsonResponse;
    }

    @Override
    public String getPlaceholderDoc(String message) {
        return """
                /**
                 * %s
                 */
                """.formatted(message);
    }
}
