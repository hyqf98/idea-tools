package io.github.easy.tools.service.doc.velocity;

import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import java.io.StringWriter;
import java.util.Properties;

/**
 * Velocity模板服务类，用于渲染注释模板
 * <p>
 * 该类封装了Velocity模板引擎的使用，提供渲染字符串模板和文件模板的功能。
 * 通过Velocity引擎将模板和上下文数据合并生成最终的注释内容。
 * </p>
 */
public class VelocityTemplateService {

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
    public VelocityTemplateService() {
        // 初始化Velocity引擎
        Properties properties = new Properties();
        // 使用新的配置键替换已弃用的键
        properties.setProperty("resource.loaders", "classpath");
        properties.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
        this.velocityEngine = new VelocityEngine();
        this.velocityEngine.init(properties);
    }

    /**
     * 渲染模板
     * <p>
     * 使用Velocity引擎将字符串模板和上下文数据合并，生成最终的文本内容。
     * </p>
     *
     * @param templateContent 模板内容
     * @param context         Velocity上下文，包含模板中使用的变量
     * @return 渲染后的字符串
     */
    public String render(String templateContent, Context context) {
        try {
            // 使用eval方法直接渲染字符串模板
            StringWriter writer = new StringWriter();
            velocityEngine.evaluate(context, writer, "VelocityTemplateService.render", templateContent);
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