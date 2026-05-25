package io.github.ideatools.service.mybatis.completion;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

/**
 * MyBatis代码补全策略接口
 * <p>
 * 定义了为不同MyBatis标签和场景提供代码补全的策略接口。
 * 实现类可以为特定的标签(如if、foreach)提供定制化的补全逻辑。
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @date 2025-12-18
 * @since 1.0.0
 */
public interface CompletionStrategy {

    /**
     * 提供代码补全
     *
     * @param context 补全上下文
     * @param result  补全结果集
     * @since 1.0.0
     */
    void provideCompletions(@NotNull CompletionContext context, @NotNull CompletionResultSet result);

    /**
     * 判断是否支持该上下文
     *
     * @param context 补全上下文
     * @return true如果支持
     * @since 1.0.0
     */
    boolean supports(@NotNull CompletionContext context);
}
