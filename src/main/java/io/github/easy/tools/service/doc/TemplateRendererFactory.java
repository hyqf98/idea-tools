package io.github.easy.tools.service.doc;

import io.github.easy.tools.service.doc.ai.AITemplateRenderer;
import io.github.easy.tools.service.doc.velocity.VelocityTemplateRenderer;
import io.github.easy.tools.ui.config.DocConfigService;

/**
 * 模板渲染器工厂类
 * <p>
 * 根据配置决定创建哪种模板渲染器实例。
 * 如果启用AI则返回AI模板渲染器，否则返回Velocity模板渲染器。
 * </p>
 */
public class TemplateRendererFactory {

    /**
     * 获取模板渲染器实例
     * <p>
     * 根据DocConfigService中的配置决定使用哪种渲染器
     * </p>
     *
     * @return 模板渲染器实例
     */
    public static TemplateRenderer getTemplateRenderer() {
        DocConfigService config = DocConfigService.getInstance();
        if (config.enableAi) {
            return new AITemplateRenderer();
        } else {
            return new VelocityTemplateRenderer();
        }
    }
}