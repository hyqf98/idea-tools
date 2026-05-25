package io.github.ideatools.service.doc;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;

/**
 * 注释生成策略接口，定义了基本操作
 * <p>
 * 该接口定义了注释生成和删除的基本操作契约，所有具体的策略实现类都必须实现该接口。
 * 通过策略模式，可以根据不同文件类型或需求提供不同的注释处理实现。
 * </p>
 */
public interface CommentGenerationStrategy {
    /**
     * 为文件生成注释
     *
     * @param file 需要生成注释的文件
     */
    void generate(PsiFile file);

    /**
     * 为文件生成注释
     *
     * @param file 需要生成注释的文件
     * @param overwrite 是否覆盖已存在的注释
     */
    default void generate(PsiFile file, boolean overwrite) {
        // 默认实现，忽略overwrite参数
        this.generate(file);
    }

    /**
     * 为元素生成注释
     *
     * @param file    需要生成注释的文件
     * @param element 需要生成注释的元素
     */
    void generate(PsiFile file, PsiElement element);

    /**
     * 为元素生成注释
     *
     * @param file    需要生成注释的文件
     * @param element 需要生成注释的元素
     * @param overwrite 是否覆盖已存在的注释
     */
    default void generate(PsiFile file, PsiElement element, boolean overwrite) {
        // 默认实现，忽略overwrite参数
        this.generate(file, element);
    }

    /**
     * 删除文件中的所有注释
     *
     * @param file 需要删除注释的文件
     */
    void remove(PsiFile file);

    /**
     * 删除元素的注释
     *
     * @param file    需要删除注释的文件
     * @param element 需要删除注释的元素
     */
    void remove(PsiFile file, PsiElement element);
}