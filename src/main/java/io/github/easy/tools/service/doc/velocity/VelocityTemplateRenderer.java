package io.github.easy.tools.service.doc.velocity;

import com.intellij.psi.PsiElement;
import io.github.easy.tools.service.doc.TemplateRenderer;
import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.util.introspection.SecureUberspector;

import java.io.StringWriter;
import java.util.Properties;

/**
 * Velocity模板渲染器
 * <p>
 * 使用Velocity模板引擎进行模板渲染的实现类。
 * 这是默认的模板渲染方式。
 * </p>
 */
public class VelocityTemplateRenderer implements TemplateRenderer {
    
    /**
     * Velocity引擎实例，用于模板渲染
     */
    private final VelocityEngine velocityEngine;
    
    /**
     * 构造函数，初始化Velocity引擎
     * <p>
     * 配置Velocity引擎使用classpath资源加载器，以便能够从类路径加载模板文件。
     * </p>
     */
    public VelocityTemplateRenderer() {
        // 初始化Velocity引擎
        Properties properties = new Properties();
        // 使用新的配置键替换已弃用的键
        properties.setProperty("resource.loaders", "classpath");
        properties.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
        // 配置安全的内省器，更好地处理空值
        properties.setProperty("runtime.introspector.uberspect", SecureUberspector.class.getName());
        // 配置空值处理
        properties.setProperty("directive.if.emptycheck", "true");
        // 配置严格模式，更好地处理null值
        properties.setProperty("runtime.strict_mode", "false");
        // 配置引用处理，使null值显示为空字符串
        properties.setProperty("runtime.reference.null.string", "");
        this.velocityEngine = new VelocityEngine();
        this.velocityEngine.init(properties);
    }
    
    /**
     * 使用Velocity引擎渲染模板内容
     *
     * @param templateContent 模板内容
     * @param context         渲染上下文
     * @param element         相关的Psi元素（Velocity渲染不需要此参数）
     * @return 渲染后的内容
     */
    @Override
    public String render(String templateContent, Context context, PsiElement element) {
        try {
            // 使用eval方法直接渲染字符串模板
            StringWriter writer = new StringWriter();
            velocityEngine.evaluate(context, writer, "VelocityTemplateRenderer.render", templateContent);
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException("模板渲染失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 渲染模板文件
     * <p>
     * 从文件加载模板并将其与上下文数据合并，生成最终的文本内容。
     * </p>
     *
     * @param templatePath 模板文件路径
     * @param context      Velocity上下文，包含模板中使用的变量
     * @return 渲染后的字符串
     */
    public String renderFromFile(String templatePath, Context context) {
        try {
            // 从文件加载并渲染模板
            Template template = velocityEngine.getTemplate(templatePath);
            // 合并模板和上下文
            StringWriter writer = new StringWriter();
            template.merge(context, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException("模板文件渲染失败: " + e.getMessage(), e);
        }
    }
}