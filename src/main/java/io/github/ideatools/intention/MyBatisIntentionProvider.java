package io.github.ideatools.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * MyBatis意图操作提供者
 * <p>
 * 提供MyBatis相关的意图操作列表
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @since 1.0.0
 */
@Service(Service.Level.PROJECT)
public final class MyBatisIntentionProvider {

    private final IntentionAction[] intentions;

    /**
     * 构造函数
     */
    public MyBatisIntentionProvider() {
        this.intentions = new IntentionAction[]{
                new MyBatisSelectIntention(),
                new MyBatisInsertIntention(),
                new MyBatisUpdateIntention(),
                new MyBatisDeleteIntention()
        };
    }

    /**
     * 获取实例
     *
     * @param project 项目
     * @return 提供者实例
     */
    public static MyBatisIntentionProvider getInstance(@NotNull Project project) {
        return project.getService(MyBatisIntentionProvider.class);
    }

    /**
     * 获取所有意图操作
     *
     * @return 意图操作数组
     */
    public IntentionAction[] getIntentions() {
        return this.intentions;
    }
}
