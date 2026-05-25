package io.github.ideatools.intention;

import org.jetbrains.annotations.NotNull;

/**
 * MyBatis Update标签生成意图操作
 * <p>
 * 为Mapper方法生成update标签的意图操作
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @since 1.0.0
 */
public class MyBatisUpdateIntention extends MyBatisIntentionAction {

    @Override
    @NotNull
    protected String getTagType() {
        return "update";
    }

    @Override
    @NotNull
    protected String getDisplayText() {
        return "生成MyBatis Update标签";
    }

    @Override
    @NotNull
    protected String getTemplatePath() {
        return "templates/mybatis-update-template.vm";
    }
}
