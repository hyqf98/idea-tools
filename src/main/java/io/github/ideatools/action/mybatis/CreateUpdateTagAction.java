package io.github.ideatools.action.mybatis;

import org.jetbrains.annotations.NotNull;

/**
 * Create Update Tag Action
 *
 * @author haijun
 * @date 2025-12-17 11:41:20
 * @version 1.0.0
 * @since 1.0.0
 */
public class CreateUpdateTagAction extends AbstractMyBatisAction {

    /**
     * 获取Update标签的Velocity模板路径
     *
     * @return 模板路径
     * @since 1.0.0
     */
    @Override
    @NotNull
    protected String getTemplatePath() {
        return "templates/mybatis-update-template.vm";
    }
}
