package io.github.easy.tools.service.doc.ai;

import com.intellij.psi.PsiElement;

/**
 * AI文档处理器接口
 * <p>
 * 定义AI文档处理器的通用接口，用于处理不同类型的代码元素
 * </p>
 */
public interface AIDocProcessor {
    
    /**
     * 根据元素类型获取提示词
     *
     * @param element 元素
     * @return 提示词
     */
    String getPromptByType(PsiElement element);
    
    /**
     * 更新文档
     *
     * @param element 元素
     * @param commentText 注释文本
     */
    void updateDoc(PsiElement element, String commentText);
    
    /**
     * 从AI响应中提取标准注释内容
     *
     * @param responseContent AI响应内容
     * @return 提取的标准注释
     */
    String extractCommentFromResponse(String responseContent);
    
    /**
     * 获取占位符注释（包括错误和默认情况）
     *
     * @param message 消息内容（错误信息或默认提示）
     * @return 占位符注释
     */
    String getPlaceholderDoc(String message);
}