package io.github.easy.tools.service.doc;

import cn.hutool.core.util.StrUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NotNull;

/**
 * Java注释比较器实现
 * <p>
 * 用于比较Java元素是否已存在注释以及注释内容是否相同
 * </p>
 */
public class JavaDocCommentComparator implements DocCommentComparator {

    /**
     * 检查Java元素是否已存在注释
     *
     * @param element Java元素
     * @return 如果已存在注释返回true，否则返回false
     */
    @Override
    public boolean hasComment(@NotNull PsiElement element) {
        if (element instanceof PsiJavaDocumentedElement documentedElement) {
            return documentedElement.getDocComment() != null;
        }
        return false;
    }

    /**
     * 合并现有注释和新注释
     * <p>
     * 保持原有注释格式，只对参数、返回值、异常等标签进行增加、修改、删除操作
     * </p>
     *
     * @param element    Java元素
     * @param newComment 新注释内容
     * @return 合并后的注释内容
     */
    @Override
    public PsiElement mergeComments(@NotNull PsiElement element, @NotNull PsiElement newComment) {
        if (!(element instanceof PsiJavaDocumentedElement documentedElement)
                || !(newComment instanceof PsiDocComment newDocComment)) {
            return newComment;
        }

        PsiDocComment oldDocComment = documentedElement.getDocComment();
        if (oldDocComment == null) {
            return newComment;
        }

        // 使用新注释作为基础，但保留用户已有的描述内容
        String mergedComment = this.preserveUserDescription(oldDocComment, newDocComment);
        
        // 使用合并后的内容创建新的PsiDocComment
        try {
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
            return factory.createDocCommentFromText(mergedComment);
        } catch (Exception e) {
            // 如果创建失败，返回新的注释
            return newComment;
        }
    }

    /**
     * 保留用户的描述内容
     *
     * @param oldDocComment 原始注释
     * @param newDocComment 新注释
     * @return 合并后的注释文本
     */
    private String preserveUserDescription(PsiDocComment oldDocComment, PsiDocComment newDocComment) {
        // 提取用户已有的描述
        String userDescription = this.extractUserDescription(oldDocComment);
        
        // 如果用户没有手动描述，则直接使用新注释
        if (StrUtil.isBlank(userDescription)) {
            return newDocComment.getText();
        }
        
        // 获取新注释的文本
        String newCommentText = newDocComment.getText();
        
        // 替换新注释中的描述部分为用户的手动描述
        return this.replaceDescription(newCommentText, userDescription);
    }
    
    /**
     * 提取用户手动编写的描述内容
     *
     * @param docComment 文档注释
     * @return 用户的描述内容
     */
    private String extractUserDescription(PsiDocComment docComment) {
        StringBuilder description = new StringBuilder();
        String commentText = docComment.getText();
        String[] lines = commentText.split("\n");

        for (String line : lines) {
            String trimmedLine = line.trim();
            // 跳过注释开始和结束标记
            if (trimmedLine.equals("/**") || trimmedLine.equals("*/")) {
                continue;
            }
            
            // 如果遇到标签行，则停止提取
            if (trimmedLine.startsWith("* @") || trimmedLine.startsWith("*@")) {
                break;
            }
            
            // 提取描述内容
            if (trimmedLine.startsWith("*")) {
                String content = trimmedLine.substring(1).trim();
                // 使用空格而不是换行符连接描述内容
                if (description.length() > 0 && !StrUtil.isBlank(content)) {
                    description.append(" ");
                }
                if (!StrUtil.isBlank(content)) {
                    description.append(content);
                }
            }
        }

        return description.toString();
    }
    
    /**
     * 替换注释中的描述部分
     *
     * @param newCommentText 新注释文本
     * @param userDescription 用户的描述内容
     * @return 替换后的注释文本
     */
    private String replaceDescription(String newCommentText, String userDescription) {
        // 简单的替换策略：将新注释中的第一行描述替换为用户的描述
        String[] lines = newCommentText.split("\n");
        StringBuilder result = new StringBuilder();
        
        boolean descriptionReplaced = false;
        boolean inTagSection = false;
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            // 直接添加开始标记
            if (trimmedLine.equals("/**")) {
                result.append(line).append("\n");
                continue;
            }
            
            // 直接添加结束标记
            if (trimmedLine.equals("*/")) {
                result.append(line);
                break;
            }
            
            // 检查是否进入标签区域
            if (trimmedLine.startsWith("* @") || trimmedLine.startsWith("*@")) {
                inTagSection = true;
            }
            
            // 如果还未替换描述且遇到描述行
            if (!descriptionReplaced && !inTagSection && trimmedLine.startsWith("*")) {
                // 添加用户的描述
                result.append(" * ").append(userDescription).append("\n");
                descriptionReplaced = true;
                continue;
            }
            
            // 如果已经替换过描述或者在标签区域，直接添加原行
            result.append(line).append("\n");
        }
        
        return result.toString();
    }
}