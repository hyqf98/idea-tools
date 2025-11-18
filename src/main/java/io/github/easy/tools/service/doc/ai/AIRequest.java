package io.github.easy.tools.service.doc.ai;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * AI请求对象
 * <p>
 * 封装发送到AI服务的请求参数
 * </p>
 */
@Data
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
    private double temperature = 0.7;
    
    /**
     * Top-p参数，控制生成文本的多样性
     */
    private double topP = 1.0;
    
    /**
     * Top-k参数，控制生成文本的多样性
     */
    private int topK = 40;
    
    /**
     * 是否开启思考模式
     */
    private boolean enableReasoning = false;
    
    /**
     * 最大生成令牌数
     */
    private int maxTokens = 2048;
    
    /**
     * 是否流式输出
     */
    private boolean stream = false;
    
    // Getters and Setters
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public String getPrompt() {
        return prompt;
    }
    
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
    
    public double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
    
    public double getTopP() {
        return topP;
    }
    
    public void setTopP(double topP) {
        this.topP = topP;
    }
    
    public int getTopK() {
        return topK;
    }
    
    public void setTopK(int topK) {
        this.topK = topK;
    }
    
    public boolean isEnableReasoning() {
        return enableReasoning;
    }
    
    public void setEnableReasoning(boolean enableReasoning) {
        this.enableReasoning = enableReasoning;
    }
    
    public int getMaxTokens() {
        return maxTokens;
    }
    
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }
    
    public boolean isStream() {
        return stream;
    }
    
    public void setStream(boolean stream) {
        this.stream = stream;
    }
}