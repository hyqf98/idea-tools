package io.github.ideatools.service.doc;

import com.intellij.psi.PsiElement;
import org.apache.velocity.context.Context;

/**
 * 模板渲染服务接口
 * <p>
 * 定义模板渲染服务的通用接口，支持不同的渲染实现。
 * 可以根据配置选择不同的渲染方式，如普通Velocity渲染或AI辅助渲染。
 * </p>
 */
public interface TemplateRenderer {
    
    /**
     * 渲染模板内容
     *
     * @param templateContent 模板内容
     * @param context         渲染上下文
     * @param element         相关的Psi元素
     * @return 渲染后的内容
     */
    String render(String templateContent, Context context, PsiElement element);
}