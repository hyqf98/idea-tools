package io.github.easy.tools.ui.config;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import io.github.easy.tools.constants.LLMConstants;
import io.github.easy.tools.constants.PromptConstants;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;

/**
 * <p> LLM配置界面类，实现Configurable接口，用于在IDEA设置中展示和管理LLM相关配置 </p>
 *
 * @author haijun
 * @version x.x.x
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.09.12 09:24
 * @since x.x.x
 */
public class LLMConfig implements Configurable {
    /**
     * 主面板组件
     */
    private JPanel mainPanel;

    /**
     * 模型基础URL输入框
     */
    private JBTextField baseUrl;

    /**
     * 模型名称输入框
     */
    private JBTextField modelName;

    /**
     * 模型类型下拉框
     */
    private JComboBox<String> modelType;

    /**
     * API key输入框
     */
    private JBTextField apiKey;

    /**
     * 超时时间输入框(使用Spinner)
     */
    private JSpinner timeout;

    /**
     * 温度参数滑块
     */
    private JSlider temperature;
    
    /**
     * 温度参数标签
     */
    private JLabel temperatureValueLabel;

    /**
     * Top-p参数滑块
     */
    private JSlider topP;
    
    /**
     * Top-p参数标签
     */
    private JLabel topPValueLabel;

    /**
     * Top-k参数输入框(使用Spinner)
     */
    private JSpinner topK;

    /**
     * 最大令牌数输入框(使用Spinner)
     */
    private JSpinner maxTokens;

    /**
     * 开启思考模式复选框
     */
    private JBCheckBox enableReasoning;

    /**
     * 设置为默认按钮
     */
    private JButton setDefaultButton;

    /**
     * 当前编辑的模型类型
     */
    private String currentEditingModelType = LLMConstants.ModelType.OPENAI;

    /**
     * 配置是否被修改的标志
     */
    private boolean isModified = false;


    /**
     * 获取显示名称
     *
     * @return 配置面板的显示名称 display name
     * @since y.y.y
     */
    @Override
    public String getDisplayName() {
        return "LLM Config";
    }

    /**
     * 创建配置组件
     *
     * @return 配置界面的主面板组件 j component
     * @since y.y.y
     */
    @Override
    public @Nullable JComponent createComponent() {
        this.mainPanel = new JPanel(new BorderLayout());
        this.mainPanel.setBorder(JBUI.Borders.empty(10));

        // 直接添加AI配置面板,不使用ScrollPane包装
        JPanel aiConfigPanel = this.createAIConfigPanel();
        this.mainPanel.add(aiConfigPanel, BorderLayout.NORTH);

        this.initEventListeners();
        this.loadConfigData();
        
        return this.mainPanel;
    }

    /**
     * 创建AI配置面板
     *
     * @return AI配置面板
     * @since y.y.y
     */
    private JPanel createAIConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("AI 配置"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(3, 5, 3, 5);  // 统一使用JBUI设置间距
        gbc.anchor = GridBagConstraints.WEST;
        
        int row = 0;
        
        // 模型类型
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        JBLabel modelTypeLabel = new JBLabel("模型类型:", AllIcons.Nodes.DataTables, JLabel.LEFT);
        panel.add(modelTypeLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        String[] modelTypes = {
            LLMConstants.ModelDisplayName.OPENAI,
            LLMConstants.ModelDisplayName.OLLAMA, 
            LLMConstants.ModelDisplayName.AZURE,
            LLMConstants.ModelDisplayName.GEMINI,
            LLMConstants.ModelDisplayName.CLAUDE,
            LLMConstants.ModelDisplayName.DEEPSEEK,
            LLMConstants.ModelDisplayName.QWEN,
            LLMConstants.ModelDisplayName.GLM,
            LLMConstants.ModelDisplayName.WENXIN
        };
        this.modelType = new JComboBox<>(modelTypes);
        panel.add(this.modelType, gbc);
        row++;
        
        // 模型地址
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        JBLabel baseUrlLabel = new JBLabel("模型地址:", AllIcons.General.Web, JLabel.LEFT);
        panel.add(baseUrlLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        this.baseUrl = new JBTextField();
        this.baseUrl.setToolTipText("请输入模型的API地址，例如: https://api.openai.com/v1");
        panel.add(this.baseUrl, gbc);
        row++;
        
        // 模型名称
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        JBLabel modelNameLabel = new JBLabel("模型名称:", AllIcons.Actions.ModuleDirectory, JLabel.LEFT);
        panel.add(modelNameLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        this.modelName = new JBTextField();
        this.modelName.setToolTipText("请输入模型名称，例如: gpt-4, claude-3-opus, qwen-max");
        panel.add(this.modelName, gbc);
        row++;
        
        // API Key
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        JBLabel apiKeyLabel = new JBLabel("API Key:", AllIcons.Nodes.SecurityRole, JLabel.LEFT);
        panel.add(apiKeyLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        this.apiKey = new JBTextField();
        this.apiKey.setToolTipText("请输入API密钥");
        panel.add(this.apiKey, gbc);
        row++;
        
        // 超时时间 (Spinner)
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        JBLabel timeoutLabel = new JBLabel("超时时间(毫秒):", AllIcons.General.BalloonInformation, JLabel.LEFT);
        panel.add(timeoutLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        SpinnerNumberModel timeoutModel = new SpinnerNumberModel(300000, 1000, 600000, 1000);
        this.timeout = new JSpinner(timeoutModel);
        this.timeout.setPreferredSize(new Dimension(150, -1));
        panel.add(this.timeout, gbc);
        row++;
        
        // 温度参数 (Slider)
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        JBLabel tempLabel = new JBLabel("温度参数:", AllIcons.General.Settings, JLabel.LEFT);
        panel.add(tempLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JPanel tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        this.temperature = new JSlider(0, 20, 7); // 0.0 to 2.0, default 0.7
        this.temperature.setMajorTickSpacing(5);
        this.temperature.setMinorTickSpacing(1);
        this.temperature.setPaintTicks(true);
        this.temperature.setPreferredSize(new Dimension(200, 40));
        this.temperatureValueLabel = new JLabel("0.7");
        this.temperature.addChangeListener(e -> {
            double value = this.temperature.getValue() / 10.0;
            this.temperatureValueLabel.setText(String.format("%.1f", value));
        });
        tempPanel.add(this.temperature);
        tempPanel.add(this.temperatureValueLabel);
        panel.add(tempPanel, gbc);
        row++;
        
        // Top-p参数 (Slider)
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        JBLabel topPLabel = new JBLabel("Top-p参数:", AllIcons.General.Settings, JLabel.LEFT);
        panel.add(topPLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JPanel topPPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        this.topP = new JSlider(0, 100, 90); // 0.0 to 1.0, default 0.9
        this.topP.setMajorTickSpacing(25);
        this.topP.setMinorTickSpacing(5);
        this.topP.setPaintTicks(true);
        this.topP.setPreferredSize(new Dimension(200, 40));
        this.topPValueLabel = new JLabel("0.90");
        this.topP.addChangeListener(e -> {
            double value = this.topP.getValue() / 100.0;
            this.topPValueLabel.setText(String.format("%.2f", value));
        });
        topPPanel.add(this.topP);
        topPPanel.add(this.topPValueLabel);
        panel.add(topPPanel, gbc);
        row++;
        
        // Top-k参数 (Spinner)
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        JBLabel topKLabel = new JBLabel("Top-k参数:", AllIcons.General.Settings, JLabel.LEFT);
        panel.add(topKLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JPanel topKPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        SpinnerNumberModel topKModel = new SpinnerNumberModel(50, 1, 100, 1);
        this.topK = new JSpinner(topKModel);
        this.topK.setPreferredSize(new Dimension(150, 30));
        topKPanel.add(this.topK);
        panel.add(topKPanel, gbc);
        row++;
        
        // 最大令牌数 (Spinner) - 支持更大的值，允许输入200000
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        JBLabel maxTokensLabel = new JBLabel("最大令牌数:", AllIcons.General.Settings, JLabel.LEFT);
        panel.add(maxTokensLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JPanel maxTokensPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        SpinnerNumberModel maxTokensModel = new SpinnerNumberModel(4096, 1, 1000000, 128);
        this.maxTokens = new JSpinner(maxTokensModel);
        this.maxTokens.setPreferredSize(new Dimension(150, 30));
        maxTokensPanel.add(this.maxTokens);
        panel.add(maxTokensPanel, gbc);
        row++;
        
        // 开启思考模式
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        this.enableReasoning = new JBCheckBox("开启思考模式 (Chain of Thought)");
        this.enableReasoning.setToolTipText("启用后，模型将显示思考过程");
        panel.add(this.enableReasoning, gbc);
        row++;
        
        // 设置为默认按钮
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.insets = JBUI.insets(10, 5, 3, 5);
        this.setDefaultButton = new JButton("设置为默认模型");
        this.setDefaultButton.setToolTipText("将当前模型设置为默认使用的模型");
        this.setDefaultButton.addActionListener(e -> this.setAsDefaultModel());
        panel.add(this.setDefaultButton, gbc);
        
        return panel;
    }

    /**
     * 加载配置数据
     *
     * @since y.y.y
     */
    private void loadConfigData() {
        LLMConfigState configState = LLMConfigState.getInstance();
        
        // 设置当前默认模型类型
        String displayType = this.mapModelTypeToDisplay(configState.defaultModelType);
        this.modelType.setSelectedItem(displayType);
        this.currentEditingModelType = configState.defaultModelType;
        
        // 加载默认模型的配置
        this.loadModelConfig(configState.defaultModelType);
        
        // 更新按钮状态
        this.updateDefaultButtonState();
    }
    
    /**
     * 加载指定模型类型的配置
     *
     * @param modelType 模型类型
     * @since 1.0.0
     */
    private void loadModelConfig(String modelType) {
        LLMConfigState configState = LLMConfigState.getInstance();
        LLMConfigState.ModelConfig config = configState.getModelConfig(modelType);
        
        this.apiKey.setText(config.apiKey);
        this.baseUrl.setText(config.baseUrl);
        this.modelName.setText(config.modelName);
        this.timeout.setValue(config.timeout);
        
        // 设置温度滑块值 (0.0-2.0 映射到 0-20)
        int tempValue = (int) (config.temperature * 10);
        this.temperature.setValue(tempValue);
        this.temperatureValueLabel.setText(String.format("%.1f", config.temperature));
        
        // 设置Top-p滑块值 (0.0-1.0 映射到 0-100)
        int topPValue = (int) (config.topP * 100);
        this.topP.setValue(topPValue);
        this.topPValueLabel.setText(String.format("%.2f", config.topP));
        
        this.topK.setValue(config.topK);
        this.maxTokens.setValue(config.maxTokens);
        this.enableReasoning.setSelected(config.enableReasoning);
    }
    
    /**
     * 将存储的模型类型映射到显示名称
     *
     * @param modelType 存储的模型类型
     * @return 显示名称
     * @since y.y.y
     */
    private String mapModelTypeToDisplay(String modelType) {
        if (modelType == null) {
            return LLMConstants.ModelDisplayName.OPENAI;
        }
        return switch (modelType.toLowerCase()) {
            case LLMConstants.ModelType.OPENAI -> LLMConstants.ModelDisplayName.OPENAI;
            case LLMConstants.ModelType.OLLAMA -> LLMConstants.ModelDisplayName.OLLAMA;
            case LLMConstants.ModelType.AZURE -> LLMConstants.ModelDisplayName.AZURE;
            case LLMConstants.ModelType.GEMINI -> LLMConstants.ModelDisplayName.GEMINI;
            case LLMConstants.ModelType.CLAUDE -> LLMConstants.ModelDisplayName.CLAUDE;
            case LLMConstants.ModelType.DEEPSEEK -> LLMConstants.ModelDisplayName.DEEPSEEK;
            case LLMConstants.ModelType.QWEN -> LLMConstants.ModelDisplayName.QWEN;
            case LLMConstants.ModelType.GLM -> LLMConstants.ModelDisplayName.GLM;
            case LLMConstants.ModelType.WENXIN -> LLMConstants.ModelDisplayName.WENXIN;
            default -> LLMConstants.ModelDisplayName.OPENAI;
        };
    }
    
    /**
     * 将显示名称映射到存储的模型类型
     *
     * @param displayType 显示名称
     * @return 存储的模型类型
     * @since y.y.y
     */
    private String mapDisplayToModelType(String displayType) {
        if (displayType == null) {
            return LLMConstants.ModelType.OPENAI;
        }
        return switch (displayType) {
            case LLMConstants.ModelDisplayName.OPENAI -> LLMConstants.ModelType.OPENAI;
            case LLMConstants.ModelDisplayName.OLLAMA -> LLMConstants.ModelType.OLLAMA;
            case LLMConstants.ModelDisplayName.AZURE -> LLMConstants.ModelType.AZURE;
            case LLMConstants.ModelDisplayName.GEMINI -> LLMConstants.ModelType.GEMINI;
            case LLMConstants.ModelDisplayName.CLAUDE -> LLMConstants.ModelType.CLAUDE;
            case LLMConstants.ModelDisplayName.DEEPSEEK -> LLMConstants.ModelType.DEEPSEEK;
            case LLMConstants.ModelDisplayName.QWEN -> LLMConstants.ModelType.QWEN;
            case LLMConstants.ModelDisplayName.GLM -> LLMConstants.ModelType.GLM;
            case LLMConstants.ModelDisplayName.WENXIN -> LLMConstants.ModelType.WENXIN;
            default -> LLMConstants.ModelType.OPENAI;
        };
    }

    /**
     * 初始化事件监听器
     * <p>
     * 为界面组件添加事件监听器，用于检测配置修改
     * </p>
     *
     * @since y.y.y
     */
    private void initEventListeners() {
        DocumentListener documentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                LLMConfig.this.isModified = true;
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                LLMConfig.this.isModified = true;
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                LLMConfig.this.isModified = true;
            }
        };
        
        // 模型类型切换监听器 - 切换时保存当前配置并加载新配置
        this.modelType.addActionListener(e -> {
            String selectedType = this.mapDisplayToModelType((String) this.modelType.getSelectedItem());
            if (!selectedType.equals(this.currentEditingModelType)) {
                // 保存当前模型的配置
                this.saveCurrentModelConfig();
                // 加载新模型的配置
                this.currentEditingModelType = selectedType;
                this.loadModelConfig(selectedType);
                // 更新按钮状态
                this.updateDefaultButtonState();
            }
            this.isModified = true;
        });
        
        this.baseUrl.getDocument().addDocumentListener(documentListener);
        this.modelName.getDocument().addDocumentListener(documentListener);
        this.apiKey.getDocument().addDocumentListener(documentListener);
        
        // Spinner监听器
        this.timeout.addChangeListener(e -> this.isModified = true);
        this.topK.addChangeListener(e -> this.isModified = true);
        this.maxTokens.addChangeListener(e -> this.isModified = true);
        
        // Slider监听器
        this.temperature.addChangeListener(e -> this.isModified = true);
        this.topP.addChangeListener(e -> this.isModified = true);
        
        this.enableReasoning.addActionListener(e -> this.isModified = true);
    }

    /**
     * 检查配置是否被修改
     *
     * @return 如果配置被修改返回true ，否则返回false
     * @since y.y.y
     */
    @Override
    public boolean isModified() {
        return this.isModified;
    }

    /**
     * 应用配置修改
     * <p>
     * 将界面中的配置保存到配置服务中
     * </p>
     *
     * @since y.y.y
     */
    @Override
    public void apply() {
        // 保存当前编辑的模型配置
        this.saveCurrentModelConfig();
        
        this.isModified = false;
    }
    
    /**
     * 保存当前正在编辑的模型配置
     *
     * @since 1.0.0
     */
    private void saveCurrentModelConfig() {
        LLMConfigState configState = LLMConfigState.getInstance();
        LLMConfigState.ModelConfig config = new LLMConfigState.ModelConfig();
        
        config.apiKey = this.apiKey.getText();
        config.baseUrl = this.baseUrl.getText();
        config.modelName = this.modelName.getText();
        config.timeout = (Integer) this.timeout.getValue();
        
        // 从滑块获取值
        config.temperature = this.temperature.getValue() / 10.0;
        config.topP = this.topP.getValue() / 100.0;
        
        config.topK = (Integer) this.topK.getValue();
        config.maxTokens = (Integer) this.maxTokens.getValue();
        config.enableReasoning = this.enableReasoning.isSelected();
        
        // 保存到当前编辑的模型类型
        configState.setModelConfig(this.currentEditingModelType, config);
    }
    
    /**
     * 设置为默认模型
     *
     * @since 1.0.0
     */
    private void setAsDefaultModel() {
        LLMConfigState configState = LLMConfigState.getInstance();
        configState.setDefaultModelType(this.currentEditingModelType);
        
        // 更新按钮状态
        this.updateDefaultButtonState();
        
        // 提示用户
        String displayName = this.mapModelTypeToDisplay(this.currentEditingModelType);
        Messages.showInfoMessage(
                "已将 " + displayName + " 设置为默认模型", 
                "设置成功"
        );
        
        this.isModified = true;
    }
    
    /**
     * 更新默认按钮状态
     *
     * @since 1.0.0
     */
    private void updateDefaultButtonState() {
        LLMConfigState configState = LLMConfigState.getInstance();
        boolean isDefault = this.currentEditingModelType.equals(configState.defaultModelType);
        
        if (isDefault) {
            this.setDefaultButton.setText("当前为默认模型");
            this.setDefaultButton.setEnabled(false);
        } else {
            this.setDefaultButton.setText("设置为默认模型");
            this.setDefaultButton.setEnabled(true);
        }
    }

    /**
     * 重置配置修改
     * <p>
     * 将界面中的配置恢复到上次保存的状态
     * </p>
     *
     * @since y.y.y
     */
    @Override
    public void reset() {
        this.loadConfigData();
        this.isModified = false;
    }
}
