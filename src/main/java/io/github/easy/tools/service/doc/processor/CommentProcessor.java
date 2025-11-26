package io.github.easy.tools.service.doc.processor;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * 注释处理器接口
 * <p>
 * 定义注释处理的通用接口，支持生成和删除文件或元素的注释。
 * </p>
 */
public interface CommentProcessor {

    /**
     * 删除整个文件的注释
     *
     * @param file 需要删除注释的文件
     */
    void removeFileComment(PsiFile file);

    /**
     * 删除指定元素的注释
     *
     * @param file    需要删除注释的文件
     * @param element 需要删除注释的元素
     */
    void removeElementComment(PsiFile file, PsiElement element);

    /**
     * 生成整个文件的注释
     *
     * @param file      需要生成注释的文件
     * @param overwrite 是否覆盖已存在的注释
     */
    void generateFileComment(PsiFile file, boolean overwrite);

    /**
     * 生成指定元素的注释
     *
     * @param file      需要生成注释的文件
     * @param element   需要生成注释的元素
     * @param overwrite 是否覆盖已存在的注释
     */
    void generateElementComment(PsiFile file, PsiElement element, boolean overwrite);

    /**
     * 生成整个文件的注释
     *
     * @param file 需要生成注释的文件
     */
    void generateFileComment(PsiFile file);

    /**
     * 生成指定元素的注释
     *
     * @param file    需要生成注释的文件
     * @param element 需要生成注释的元素
     */
    void generateElementComment(PsiFile file, PsiElement element);

    /**
     * 使用AI生成整个文件的注释
     *
     * @param file 需要生成注释的文件
     */
    void generateFileCommentByAi(PsiFile file);

    /**
     * 使用AI生成指定元素的注释
     *
     * @param file    需要生成注释的文件
     * @param element 需要生成注释的元素
     */
    void generateElementCommentByAi(PsiFile file, PsiElement element);
}