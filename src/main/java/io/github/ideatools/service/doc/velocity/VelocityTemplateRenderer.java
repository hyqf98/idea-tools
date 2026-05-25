package io.github.ideatools.service.doc.velocity;

import com.intellij.psi.PsiElement;
import io.github.ideatools.service.doc.TemplateRenderer;
import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.apache.velocity.runtime.resource.util.StringResourceRepositoryImpl;
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
        // 同时支持Velocity 1.x和2.x的配置键
        // Velocity 2.x 配置
        properties.setProperty("resource.loaders", "classpath");
        properties.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
        properties.setProperty("resource.loader.classpath.path", "templates");
        // Velocity 1.x 兼容配置
        properties.setProperty("resource.loader", "classpath");
        properties.setProperty("class.resource.loader.class", ClasspathResourceLoader.class.getName());
        // 配置安全的内省器，更好地处理空值
        properties.setProperty("runtime.introspector.uberspect", SecureUberspector.class.getName());
        // 配置空值处理
        properties.setProperty("directive.if.emptycheck", "true");
        // 配置严格模式，更好地处理null值
        properties.setProperty("runtime.strict_mode", "false");
        // 配置引用处理，使null值显示为空字符串
        properties.setProperty("runtime.reference.null.string", "");
        // 配置编码
        properties.setProperty("input.encoding", "UTF-8");
        properties.setProperty("output.encoding", "UTF-8");
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
     * 使用备用加载方式，通过类加载器直接读取资源文件。
     * </p>
     *
     * @param templatePath 模板文件路径
     * @param context      Velocity上下文，包含模板中使用的变量
     * @return 渲染后的字符串
     */
    public String renderFromFile(String templatePath, Context context) {
        try {
            // 首先尝试使用Velocity的模板加载
            Template template = velocityEngine.getTemplate(templatePath);
            StringWriter writer = new StringWriter();
            template.merge(context, writer);
            return writer.toString();
        } catch (Exception e) {
            // 如果Velocity加载失败，使用备用方式：通过类加载器读取资源
            try {
                String templateContent = this.loadResourceAsString(templatePath);
                if (templateContent != null && !templateContent.isEmpty()) {
                    StringWriter writer = new StringWriter();
                    velocityEngine.evaluate(context, writer, "TemplateRenderer", templateContent);
                    return writer.toString();
                }
                throw new RuntimeException("模板文件为空或不存在: " + templatePath);
            } catch (Exception ex) {
                throw new RuntimeException("模板文件渲染失败: " + templatePath + ", 错误: " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * 通过类加载器加载资源文件为字符串
     *
     * @param resourcePath 资源文件路径
     * @return 资源文件内容
     */
    private String loadResourceAsString(String resourcePath) {
        java.io.InputStream inputStream = null;
        try {
            // 尝试使用当前类的类加载器
            inputStream = this.getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (inputStream == null) {
                // 如果失败，尝试使用上下文类加载器
                inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
            }
            if (inputStream == null) {
                return null;
            }
            // 使用缓冲读取器读取内容
            StringBuilder content = new StringBuilder();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                content.append(new String(buffer, 0, bytesRead, java.nio.charset.StandardCharsets.UTF_8));
            }
            return content.toString();
        } catch (Exception e) {
            throw new RuntimeException("加载资源文件失败: " + resourcePath + ", 错误: " + e.getMessage(), e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
