package io.github.easy.tools.service.doc.processor;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.easy.tools.service.doc.JavaCommentGenerationStrategy;

/**
 * Java注释处理器，实现删除与生成逻辑
 * <p>
 * 该类是Java文件注释处理的具体实现，直接使用JavaCommentGenerationStrategy执行注释生成。
 * </p>
 *
 * iamxiaohaijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.09.17 14:44
 * @since y.y.y
 */
public class JavaCommentProcessor implements CommentProcessor {

    /** 注释生成策略 */
    private final JavaCommentGenerationStrategy strategy;

    /**
     * Java comment processor
     *
     * @since y.y.y
     */
    public JavaCommentProcessor() {
        this.strategy = new JavaCommentGenerationStrategy();
    }

    /**
     * 删除整个文件的注释
     *
     * @param file 需要删除注释的文件
     * @since y.y.y
     */
    @Override
    public void removeFileComment(PsiFile file) {
        this.strategy.remove(file);
    }

    /**
     * 删除指定元素的注释
     *
     * @param file    需要删除注释的文件
     * @param element 需要删除注释的元素
     * @since y.y.y
     */
    @Override
    public void removeElementComment(PsiFile file, PsiElement element) {
        this.strategy.remove(file, element);
    }

    /**
     * 生成整个文件的注释
     *
     * @param file      需要生成注释的文件
     * @param overwrite 是否覆盖已存在的注释
     * @since y.y.y
     */
    @Override
    public void generateFileComment(PsiFile file, boolean overwrite) {
        this.strategy.generate(file, overwrite);
    }

    /**
     * 生成指定元素的注释
     *
     * @param file      需要生成注释的文件
     * @param element   需要生成注释的元素
     * @param overwrite 是否覆盖已存在的注释
     * @since y.y.y
     */
    @Override
    public void generateElementComment(PsiFile file, PsiElement element, boolean overwrite) {
        this.strategy.generate(file, element, overwrite);
    }

    /**
     * 生成整个文件的注释
     *
     * @param file 需要生成注释的文件
     * @since y.y.y
     */
    @Override
    public void generateFileComment(PsiFile file) {
        this.generateFileComment(file, true);
    }

    /**
     * 生成指定元素的注释
     *
     * @param file    需要生成注释的文件
     * @param element 需要生成注释的元素
     * @since y.y.y
     */
    @Override
    public void generateElementComment(PsiFile file, PsiElement element) {
        this.generateElementComment(file, element, true);
    }

    /**
     * 使用AI生成整个文件的注释
     *
     * @param file 需要生成注释的文件
     * @since y.y.y
     */
    @Override
    public void generateFileCommentByAi(PsiFile file) {
        this.strategy.generateByAi(file);
    }

    /**
     * 使用AI生成指定元素的注释
     *
     * @param file    需要生成注释的文件
     * @param element 需要生成注释的元素
     * @since y.y.y
     */
    @Override
    public void generateElementCommentByAi(PsiFile file, PsiElement element) {
        this.strategy.generateByAi(file, element);
    }
}
