package io.github.ideatools.service.doc;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * 注释比较器接口
 * <p>
 * 用于比较元素是否已存在注释以及注释内容是否相同
 * </p>
 */
public interface DocCommentComparator {

    /**
     * 检查元素是否已存在注释
     *
     * @param element 元素
     * @return 如果已存在注释返回true，否则返回false
     */
    boolean hasComment(@NotNull PsiElement element);


    /**
     * 合并现有注释和新注释
     *
     * @param element 元素
     * @param newComment 新注释内容
     * @return 合并后的注释内容
     */
    PsiElement mergeComments(@NotNull PsiElement element, @NotNull PsiElement newComment);

}
