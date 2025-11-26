package io.github.easy.tools.ui.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Dimension;
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
     * AI配置面板
     */
    private JPanel aiContent;

    /**
     * 模型基础URL输入框
     */
    private JTextField baseUrl;

    /**
     * 模型名称输入框
     */
    private JTextField modelName;

    /**
     * 模型地址标签
     */
    private JLabel baseUrlTitle;

    /**
     * 模型名称标签
     */
    private JLabel modelNameTitle;

    /**
     * 模型类型下拉框
     */
    private JComboBox modelType;

    /**
     * 模型类型标签
     */
    private JLabel modelTypeTitle;
    /** Api key */
    private JTextField apiKey;
    /** Api key title */
    private JLabel apiKeyTitle;

    /** 超时时间输入框 */
    private JTextField timeout;
    /** 超时时间标签 */
    private JLabel timeoutTitle;

    /** 温度参数输入框 */
    private JTextField temperature;
    /** 温度参数标签 */
    private JLabel temperatureTitle;

    /** Top-p参数输入框 */
    private JTextField topP;
    /** Top-p参数标签 */
    private JLabel topPTitle;

    /** Top-k参数输入框 */
    private JTextField topK;
    /** Top-k参数标签 */
    private JLabel topKTitle;

    /** 最大令牌数输入框 */
    private JTextField maxTokens;
    /** 最大令牌数标签 */
    private JLabel maxTokensTitle;

    /** 开启思考模式复选框 */
    private JCheckBox enableReasoning;

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
        this.initEventListeners();
        return this.mainPanel;
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
        this.modelType.addActionListener(e -> this.isModified = true);
        this.baseUrl.getDocument().addDocumentListener(documentListener);
        this.modelName.getDocument().addDocumentListener(documentListener);
        this.apiKey.getDocument().addDocumentListener(documentListener);
        this.timeout.getDocument().addDocumentListener(documentListener);
        this.temperature.getDocument().addDocumentListener(documentListener);
        this.topP.getDocument().addDocumentListener(documentListener);
        this.topK.getDocument().addDocumentListener(documentListener);
        this.maxTokens.getDocument().addDocumentListener(documentListener);
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
        DocConfigService config = DocConfigService.getInstance();
        return this.isModified
                || !Objects.equals(config.baseUrl, this.baseUrl.getText())
                || !Objects.equals(config.modelName, this.modelName.getText())
                || !Objects.equals(config.apiKey, this.apiKey.getText())
                || !Objects.equals(config.modelType, this.modelType.getSelectedItem())
                || config.timeout != Integer.parseInt(this.timeout.getText())
                || config.temperature != Double.parseDouble(this.temperature.getText())
                || config.topP != Double.parseDouble(this.topP.getText())
                || config.topK != Integer.parseInt(this.topK.getText())
                || config.maxTokens != Integer.parseInt(this.maxTokens.getText())
                || config.enableReasoning != this.enableReasoning.isSelected();
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
        DocConfigService config = DocConfigService.getInstance();
        config.apiKey = this.apiKey.getText();
        config.baseUrl = this.baseUrl.getText();
        config.modelName = this.modelName.getText();
        config.modelType = (String) this.modelType.getSelectedItem();
        config.timeout = Integer.parseInt(this.timeout.getText());
        config.temperature = Double.parseDouble(this.temperature.getText());
        config.topP = Double.parseDouble(this.topP.getText());
        config.topK = Integer.parseInt(this.topK.getText());
        config.maxTokens = Integer.parseInt(this.maxTokens.getText());
        config.enableReasoning = this.enableReasoning.isSelected();

        this.isModified = false;
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
        DocConfigService config = DocConfigService.getInstance();
        this.apiKey.setText(config.apiKey);
        this.baseUrl.setText(config.baseUrl);
        this.modelName.setText(config.modelName);
        this.modelType.setSelectedItem(config.modelType);
        this.timeout.setText(String.valueOf(config.timeout));
        this.temperature.setText(String.valueOf(config.temperature));
        this.topP.setText(String.valueOf(config.topP));
        this.topK.setText(String.valueOf(config.topK));
        this.maxTokens.setText(String.valueOf(config.maxTokens));
        this.enableReasoning.setSelected(config.enableReasoning);
        this.isModified = false;
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        this.$$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        aiContent = new JPanel();
        aiContent.setLayout(new GridLayoutManager(10, 3, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(aiContent, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        aiContent.setBorder(BorderFactory.createTitledBorder(null, "AI配置", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        modelTypeTitle = new JLabel();
        modelTypeTitle.setText("模型类型");
        aiContent.add(modelTypeTitle, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        modelType = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        defaultComboBoxModel1.addElement("openai");
        defaultComboBoxModel1.addElement("ollama");
        modelType.setModel(defaultComboBoxModel1);
        aiContent.add(modelType, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        baseUrlTitle = new JLabel();
        baseUrlTitle.setText("模型地址");
        aiContent.add(baseUrlTitle, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        baseUrl = new JTextField();
        aiContent.add(baseUrl, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(300, -1), null, 0, false));
        modelNameTitle = new JLabel();
        modelNameTitle.setText("模型名称");
        aiContent.add(modelNameTitle, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        modelName = new JTextField();
        aiContent.add(modelName, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        apiKeyTitle = new JLabel();
        apiKeyTitle.setText("API Key");
        aiContent.add(apiKeyTitle, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        apiKey = new JTextField();
        aiContent.add(apiKey, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        timeoutTitle = new JLabel();
        timeoutTitle.setText("超时时间(毫秒)");
        aiContent.add(timeoutTitle, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        timeout = new JTextField();
        aiContent.add(timeout, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        temperatureTitle = new JLabel();
        temperatureTitle.setText("温度参数");
        aiContent.add(temperatureTitle, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        temperature = new JTextField();
        aiContent.add(temperature, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        topPTitle = new JLabel();
        topPTitle.setText("Top-p参数");
        aiContent.add(topPTitle, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        topP = new JTextField();
        aiContent.add(topP, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        topKTitle = new JLabel();
        topKTitle.setText("Top-k参数");
        aiContent.add(topKTitle, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        topK = new JTextField();
        aiContent.add(topK, new GridConstraints(7, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        maxTokensTitle = new JLabel();
        maxTokensTitle.setText("最大令牌数");
        aiContent.add(maxTokensTitle, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        maxTokens = new JTextField();
        aiContent.add(maxTokens, new GridConstraints(8, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        enableReasoning = new JCheckBox();
        enableReasoning.setText("开启思考模式");
        aiContent.add(enableReasoning, new GridConstraints(9, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /** @noinspection ALL */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
