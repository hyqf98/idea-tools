package io.github.ideatools.ui.config;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

/**
 * Easy Config
 *
 * @author haijun
 * @date 2025-12-19 14:25:03
 * @version 1.0.0
 * @since 1.0.0
 */
public class EasyConfig implements Configurable {

    /**
     * 功能开关配置实例（作为默认显示页面）
     *
     */
    private final FeatureToggleConfig featureToggleConfig = new FeatureToggleConfig();

    /**
     * 获取配置页面的显示名称
     *
     * @return 显示名称
     * @since 1.0.0
     */
    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Easy Config";
    }

    /**
     * 创建配置UI组件
     * <p>
     * 默认显示功能开关配置页面
     * </p>
     *
     * @return UI组件
     * @since 1.0.0
     */
    @Nullable
    @Override
    public JComponent createComponent() {
        return this.featureToggleConfig.createComponent();
    }

    /**
     * 检查配置是否被修改
     *
     * @return 如果配置被修改返回true,否则返回false
     * @since 1.0.0
     */
    @Override
    public boolean isModified() {
        return this.featureToggleConfig.isModified();
    }

    /**
     * 应用配置更改
     *
     * @since 1.0.0
     */
    @Override
    public void apply() {
        this.featureToggleConfig.apply();
    }

    /**
     * 重置配置为默认值
     *
     * @since 1.0.0
     */
    @Override
    public void reset() {
        this.featureToggleConfig.reset();
    }
}
