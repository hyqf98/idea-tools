package io.github.easy.tools.service.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p> AI请求对象 </p>
 * <p>
 * 封装发送到AI服务的请求参数，包括模型名称、提示词、温度等参数
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 10:00
 * @since 1.0.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AIRequest {
    
    /**
     * 模型名称
     */
    private String model;
    
    /**
     * 提示词内容
     */
    private String prompt;
    
    /**
     * 温度参数，控制生成文本的随机性
     */
    @Builder.Default
    private double temperature = 0.7;
    
    /**
     * Top-p参数，控制生成文本的多样性
     */
    @Builder.Default
    private double topP = 1.0;
    
    /**
     * Top-k参数，控制生成文本的多样性
     */
    @Builder.Default
    private int topK = 40;
    
    /**
     * 是否开启思考模式
     */
    @Builder.Default
    private boolean enableReasoning = false;
    
    /**
     * 最大生成令牌数
     */
    @Builder.Default
    private int maxTokens = 2048;
    
    /**
     * 是否流式输出
     */
    @Builder.Default
    private boolean stream = false;
}
