package io.github.ideatools.service.mybatis.completion;

import com.intellij.codeInsight.completion.CompletionResultSet;
import io.github.ideatools.service.mybatis.completion.strategy.FieldCompletionStrategy;
import io.github.ideatools.service.mybatis.completion.strategy.ParameterCompletionStrategy;
import io.github.ideatools.service.mybatis.completion.strategy.TagTemplateCompletionStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Completion Strategy Manager
 *
 * @author haijun
 * @date 2025-12-18 14:31:11
 * @version 1.0.0
 * @since 1.0.0
 */
public class CompletionStrategyManager {

    /**
     * 单例实例
     *
     */
    private static final CompletionStrategyManager INSTANCE = new CompletionStrategyManager();

    /**
     * 策略列表
     *
     */
    private final List<CompletionStrategy> strategies;

    /**
     * 私有构造函数
     *
     * @since 1.0.0
     */
    private CompletionStrategyManager() {
        this.strategies = new ArrayList<>();
        this.registerStrategies();
    }

    /**
     * 获取单例实例
     *
     * @return 单例实例
     * @since 1.0.0
     */
    public static CompletionStrategyManager getInstance() {
        return INSTANCE;
    }

    /**
     * 注册所有策略
     *
     * @since 1.0.0
     */
    private void registerStrategies() {
        // 注意顺序:标签模板 > 字段 > 参数
        // 因为标签模板最特殊,需要优先匹配
        this.strategies.add(new TagTemplateCompletionStrategy());
        this.strategies.add(new FieldCompletionStrategy());
        this.strategies.add(new ParameterCompletionStrategy());
    }

    /**
     * 提供代码补全
     *
     * @param context 补全上下文
     * @param result  补全结果集
     * @since 1.0.0
     */
    public void provideCompletions(@NotNull CompletionContext context, @NotNull CompletionResultSet result) {
        for (CompletionStrategy strategy : this.strategies) {
            if (strategy.supports(context)) {
                strategy.provideCompletions(context, result);
            }
        }
    }
}
