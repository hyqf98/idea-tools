package io.github.ideatools.intention;

import org.jetbrains.annotations.NotNull;

/**
 * MyBatis Select标签生成意图操作
 * <p>
 * 为Mapper方法生成select标签的意图操作
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @since 1.0.0
 */
public class MyBatisSelectIntention extends MyBatisIntentionAction {

    @Override
    @NotNull
    protected String getTagType() {
        return "select";
    }

    @Override
    @NotNull
    protected String getDisplayText() {
        return "生成MyBatis Select标签";
    }

    @Override
    @NotNull
    protected String getTemplatePath() {
        return "templates/mybatis-select-template.vm";
    }
}
