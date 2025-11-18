package io.github.easy.tools.service.doc.ai;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.javadoc.PsiDocComment;
import io.github.easy.tools.utils.NotificationUtil;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Java AI文档处理器实现
 * <p>
 * 处理Java代码元素的AI文档生成
 * </p>
 */
public class JavaAiDocProcessor implements AIDocProcessor {
    
    /** 类提示词模板 */
    private static final String CLASS_PROMPT = """
            请根据注释模板以及上下文参数生成标准的JavaDoc注释，并且你将根据代码信息进行总结归纳并且将归纳的
            内容填充到注释的描述部分出，例如模板中的${description}出，如果注释模板没有${description}
            占位符则默认填充在JavaDoc注释的描述部分，归纳总结的内容包含如下：
            1. 精简的语句总结整个类提供的功能
            2. 提取整个类的核心功能
            3. 提供一个简单的类调用的示例
            4. 禁止生成类中各个方法和属性的说明，只需要生成Java类的注释信息即可
            
            注释模板
            {template}
            
            上下文参数
            {context}
            
            代码信息
            {code}
            
            """;
    
    /** 方法提示词模板 */
    private static final String METHOD_PROMPT = """
            请根据注释模板以及上下文参数生成标准的JavaDoc注释，并且你将根据代码信息进行总结归纳并且将归纳的
            内容填充到注释的描述部分出，例如模板中的${description}出，如果注释模板没有${description}
            占位符则默认填充在JavaDoc注释的描述部分，归纳总结的内容包含如下：
            1. 精简的语句总结整个方法所提供的功能
            2. 一点一点的罗列出方法步骤，如果存在嵌套方法则不需要总结嵌套方法
            3. 提供一个简单的方法调用示例
            4. 只需要生成JavaDoc注释，禁止返回方法体等信息
            
            注释模板
            {template}
            
            上下文参数
            {context}
            
            代码信息
            {code}
            """;
    
    /** 字段提示词模板 */
    private static final String FIELD_PROMPT = """
            请根据注释模板以及上下文参数生成标准的JavaDoc注释，并且你将根据代码信息进行总结归纳并且将归纳的
            内容填充到注释的描述部分出，例如模板中的${description}出，如果注释模板没有${description}
            占位符则默认填充在JavaDoc注释的描述部分，归纳总结的内容包含如下：
            1. 按照注释模板的格式生成JavaDoc，并且为对属性名称进行翻译描述
            2. 如果注释模板还有其他的占位符说明，则按照上下文参数进行填充
            
            注释模板
            {template}
            
            上下文参数
            {context}
            
            代码信息
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
            if (element instanceof PsiClass || element instanceof PsiMethod || element instanceof PsiField) {
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
                        if (element instanceof PsiClass psiClass) {
                            existingDocComment = psiClass.getDocComment();
                        } else if (element instanceof PsiMethod psiMethod) {
                            existingDocComment = psiMethod.getDocComment();
                        } else if (element instanceof PsiField psiField) {
                            existingDocComment = psiField.getDocComment();
                        }
                        
                        // 更新或添加注释
                        if (existingDocComment != null) {
                            existingDocComment.replace(docCommentFromText);
                        } else {
                            element.addBefore(docCommentFromText, element.getFirstChild());
                        }
                    });
                }
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
        
        // 尝试从代码块中提取注释
        if (responseContent.contains("```")) {
            int start = responseContent.indexOf("```");
            int end = responseContent.lastIndexOf("```");
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
        
        // 如果没有找到标准JavaDoc格式，返回原始内容
        return responseContent;
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