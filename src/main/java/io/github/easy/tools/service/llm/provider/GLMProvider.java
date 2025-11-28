package io.github.easy.tools.service.llm.provider;

/**
 * <p> 智谱清言(GLM)服务提供商实现 </p>
 * <p>
 * GLM使用与OpenAI兼容的API接口
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 10:00
 * @since 1.0.0
 */
public class GLMProvider extends OpenAIProvider {
    
    @Override
    public String getProviderName() {
        return "GLM (智谱清言)";
    }
}
