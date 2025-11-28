package io.github.easy.tools.ui.config;

import cn.hutool.core.util.StrUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.easy.tools.action.doc.listener.FileSaveListenerManager;
import io.github.easy.tools.entity.doc.Desc;
import io.github.easy.tools.entity.doc.ParameterInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <p> 配置界面类,实现Configurable接口,用于在IDEA设置中展示和管理文档模板配置 </p>
 *
 * @author haijun
 * @email "mailto:zhonghaijun@zhxx.com"
 * @date 2025-11-27 13:31:01
 * @version 1.0.0
 * @since 1.0.0
 */
public class DocConfig implements Configurable {
    /**
     * 主面板组件
     */
    private JPanel mainPanel;

    /**
     * 类模板文本框
     */
    private JTextArea classTemplate;

    /**
     * 方法模板文本框
     */
    private JTextArea methodTemplate;

    /**
     * 字段模板文本框
     */
    private JTextArea fieldTemplate;

    /**
     * 类AI提示词文本框
     */
    private JTextArea classPrompt;

    /**
     * 方法AI提示词文本框
     */
    private JTextArea methodPrompt;

    /**
     * 字段AI提示词文本框
     */
    private JTextArea fieldPrompt;

    /**
     * 模板类型切换面板
     */
    private JPanel templateCardPanel;

    /**
     * 卡片布局管理器
     */
    private CardLayout templateCardLayout;

    /**
     * Velocity模板单选按钮
     */
    private JRadioButton velocityRadio;

    /**
     * AI提示词单选按钮
     */
    private JRadioButton aiPromptRadio;

    /**
     * 自定义变量表格
     */
    private JBTable customVarTable;

    /**
     * 自定义变量表格模型
     */
    private DefaultTableModel customVarTableModel;

    /**
     * 内置变量树形展示
     */
    private JTree builtInVarsTree;

    /**
     * 保存监听复选框
     */
    private JBCheckBox saveListener;

    /**
     * 是否添加非标标签
     */
    private JBCheckBox nonStandardDoc;

    /**
     * 配置是否被修改的标志
     */
    private boolean isModified = false;


    /**
     * 获取显示名称
     *
     * @return the string
     * @since 1.0.0
     */
    @Override
    public String getDisplayName() {
        return "Java Doc Config";
    }

    /**
     * 获取帮助主题
     *
     * @return the string
     * @since 1.0.0
     */
    @Override
    public @Nullable String getHelpTopic() {
        return null;
    }

    /**
     * 创建配置组件
     *
     * @return the component
     * @since 1.0.0
     */
    @Override
    public @Nullable JComponent createComponent() {
        this.mainPanel = new JPanel(new BorderLayout());
        this.mainPanel.setBorder(JBUI.Borders.empty(10));

        // 创建分割面板
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // 模板配置面板
        contentPanel.add(this.createTemplatesPanel());
        contentPanel.add(Box.createVerticalStrut(10));

        // 自定义变量面板
        contentPanel.add(this.createCustomVarsPanel());
        contentPanel.add(Box.createVerticalStrut(10));

        // 高级特性面板
        contentPanel.add(this.createAdvancedFeaturesPanel());
        contentPanel.add(Box.createVerticalStrut(10));

        // 内置变量面板
        contentPanel.add(this.createBuiltInVarsPanel());

        JBScrollPane scrollPane = new JBScrollPane(contentPanel);
        scrollPane.setBorder(JBUI.Borders.empty());
        this.mainPanel.add(scrollPane, BorderLayout.CENTER);

        this.initEventListeners();
        this.loadConfigData();
        
        return this.mainPanel;
    }

    /**
     * 检查配置是否被修改
     *
     * @return the boolean
     * @since 1.0.0
     */
    @Override
    public boolean isModified() {
        DocConfigService config = DocConfigService.getInstance();
        return this.isModified ||
                !Objects.equals(this.classTemplate.getText(), config.classTemplate) ||
                !Objects.equals(this.methodTemplate.getText(), config.methodTemplate) ||
                !Objects.equals(this.fieldTemplate.getText(), config.fieldTemplate) ||
                !Objects.equals(this.classPrompt.getText(), config.classPrompt) ||
                !Objects.equals(this.methodPrompt.getText(), config.methodPrompt) ||
                !Objects.equals(this.fieldPrompt.getText(), config.fieldPrompt) ||
                this.isCustomVarsModified(config) ||
                this.saveListener.isSelected() != config.saveListener ||
                this.nonStandardDoc.isSelected() != config.nonStandardDoc;
    }

    /**
     * 检查自定义变量是否被修改
     *
     * @param config 配置服务
     * @return 是否修改
     * @since 1.0.0
     */
    private boolean isCustomVarsModified(DocConfigService config) {
        List<Map<String, Object>> tableData = this.getCustomVarsFromTable();
        return !Objects.equals(tableData, config.customParameters);
    }

    /**
     * 应用配置修改 <p> 将界面中的配置保存到配置服务中 </p>
     *
     * @since 1.0.0
     */
    @Override
    public void apply() {
        DocConfigService config = DocConfigService.getInstance();
        config.classTemplate = this.classTemplate.getText();
        config.methodTemplate = this.methodTemplate.getText();
        config.fieldTemplate = this.fieldTemplate.getText();

        // 保存AI提示词
        config.classPrompt = this.classPrompt.getText();
        config.methodPrompt = this.methodPrompt.getText();
        config.fieldPrompt = this.fieldPrompt.getText();

        config.nonStandardDoc = this.nonStandardDoc.isSelected();
        
        // 从表格获取自定义变量
        config.customParameters.clear();
        config.customParameters.addAll(this.getCustomVarsFromTable());
        
        // 重新生成customVar字符串用于兼容
        config.customVar = this.buildCustomVarString(config.customParameters);
        
        config.saveListener = this.saveListener.isSelected();
        // 更新监听器状态
        FileSaveListenerManager.getInstance().updateListenerState();

        this.isModified = false;
        this.repaint();
    }

    /**
     * 从表格中获取自定义变量列表
     *
     * @return 自定义变量列表
     * @since 1.0.0
     */
    private List<Map<String, Object>> getCustomVarsFromTable() {
        List<Map<String, Object>> customVars = new ArrayList<>();
        for (int i = 0; i < this.customVarTableModel.getRowCount(); i++) {
            String name = (String) this.customVarTableModel.getValueAt(i, 0);
            String desc = (String) this.customVarTableModel.getValueAt(i, 1);
            String value = (String) this.customVarTableModel.getValueAt(i, 2);
            
            if (StrUtil.isNotBlank(name)) {
                Map<String, Object> param = new LinkedHashMap<>();
                param.put("name", name.trim());
                param.put("description", StrUtil.blankToDefault(desc, name).trim());
                param.put("value", StrUtil.blankToDefault(value, "").trim());
                customVars.add(param);
            }
        }
        return customVars;
    }

    /**
     * 构建自定义变量字符串
     *
     * @param customParams 自定义参数列表
     * @return 自定义变量字符串
     * @since 1.0.0
     */
    private String buildCustomVarString(List<Map<String, Object>> customParams) {
        return customParams.stream()
                .map(param -> {
                    String name = (String) param.get("name");
                    String desc = (String) param.get("description");
                    String value = (String) param.get("value");
                    
                    if (StrUtil.isNotBlank(desc) && !desc.equals(name)) {
                        return String.format("%s(%s)=%s", name, desc, value);
                    }
                    return String.format("%s=%s", name, value);
                })
                .collect(Collectors.joining(";"));
    }

    /**
     * 重置配置修改 <p> 将界面中的配置恢复到上次保存的状态 </p>
     *
     * @since 1.0.0
     */
    @Override
    public void reset() {
        this.loadConfigData();
        this.isModified = false;
        this.repaint();
    }

    /**
     * 创建模板配置面板
     *
     * @return 模板配置面板
     * @since 1.0.0
     */
    private JPanel createTemplatesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("模板配置"));
        
        // 创建模板类型切换面板
        JPanel switchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        this.velocityRadio = new JRadioButton("Velocity模板", true);
        this.aiPromptRadio = new JRadioButton("AI提示词模板");
        
        ButtonGroup group = new ButtonGroup();
        group.add(this.velocityRadio);
        group.add(this.aiPromptRadio);
        
        switchPanel.add(this.velocityRadio);
        switchPanel.add(this.aiPromptRadio);
        panel.add(switchPanel, BorderLayout.NORTH);
        
        // 创建卡片布局面板
        this.templateCardLayout = new CardLayout();
        this.templateCardPanel = new JPanel(this.templateCardLayout);
        
        // 添加Velocity模板面板
        this.templateCardPanel.add(this.createVelocityTemplatesPanel(), "VELOCITY");
        
        // 添加AI提示词面板
        this.templateCardPanel.add(this.createAIPromptsPanel(), "AI_PROMPT");
        
        panel.add(this.templateCardPanel, BorderLayout.CENTER);
        
        // 添加切换监听器
        this.velocityRadio.addActionListener(e -> this.templateCardLayout.show(this.templateCardPanel, "VELOCITY"));
        this.aiPromptRadio.addActionListener(e -> this.templateCardLayout.show(this.templateCardPanel, "AI_PROMPT"));
        
        return panel;
    }

    /**
     * 创建Velocity模板面板
     *
     * @return Velocity模板面板
     * @since 1.0.0
     */
    private JPanel createVelocityTemplatesPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = JBUI.insets(5);
        gbc.weightx = 1.0;
        
        // 类模板
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 0.2;
        JPanel classPanel = this.createTemplateSection("类模板", 150);
        this.classTemplate = (JTextArea) ((JBScrollPane) classPanel.getComponent(0)).getViewport().getView();
        panel.add(classPanel, gbc);
        
        // 方法模板
        gbc.gridy = 1;
        gbc.weighty = 0.3;
        JPanel methodPanel = this.createTemplateSection("方法模板", 200);
        this.methodTemplate = (JTextArea) ((JBScrollPane) methodPanel.getComponent(0)).getViewport().getView();
        panel.add(methodPanel, gbc);
        
        // 字段模板
        gbc.gridy = 2;
        gbc.weighty = 0.15;
        JPanel fieldPanel = this.createTemplateSection("字段模板", 100);
        this.fieldTemplate = (JTextArea) ((JBScrollPane) fieldPanel.getComponent(0)).getViewport().getView();
        panel.add(fieldPanel, gbc);
        
        return panel;
    }

    /**
     * 创建AI提示词面板
     *
     * @return AI提示词面板
     * @since 1.0.0
     */
    private JPanel createAIPromptsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = JBUI.insets(5);
        gbc.weightx = 1.0;
        
        // 类注释提示词
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 0.33;
        JPanel classPromptPanel = this.createTemplateSection("类注释提示词", 150);
        this.classPrompt = (JTextArea) ((JBScrollPane) classPromptPanel.getComponent(0)).getViewport().getView();
        panel.add(classPromptPanel, gbc);
        
        // 方法注释提示词
        gbc.gridy = 1;
        gbc.weighty = 0.33;
        JPanel methodPromptPanel = this.createTemplateSection("方法注释提示词", 150);
        this.methodPrompt = (JTextArea) ((JBScrollPane) methodPromptPanel.getComponent(0)).getViewport().getView();
        panel.add(methodPromptPanel, gbc);
        
        // 字段注释提示词
        gbc.gridy = 2;
        gbc.weighty = 0.34;
        JPanel fieldPromptPanel = this.createTemplateSection("字段注释提示词", 150);
        this.fieldPrompt = (JTextArea) ((JBScrollPane) fieldPromptPanel.getComponent(0)).getViewport().getView();
        panel.add(fieldPromptPanel, gbc);
        
        return panel;
    }

    /**
     * 创建单个模板编辑区域
     *
     * @param title 标题
     * @param height 高度
     * @return 模板面板
     * @since 1.0.0
     */
    private JPanel createTemplateSection(String title, int height) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        
        JTextArea textArea = new JTextArea();
        textArea.setLineWrap(false);
        textArea.setWrapStyleWord(false);
        // 使用默认等宽字体
        textArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        
        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(-1, height));
        
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 创建自定义变量面板
     *
     * @return 自定义变量面板
     * @since 1.0.0
     */
    private JPanel createCustomVarsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("自定义变量"));
        
        // 创建表格
        String[] columnNames = {"变量名", "描述", "默认值"};
        this.customVarTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };
        
        this.customVarTable = new JBTable(this.customVarTableModel);
        this.customVarTable.setPreferredScrollableViewportSize(new Dimension(-1, 150));
        this.customVarTable.setFillsViewportHeight(true);
        
        JBScrollPane scrollPane = new JBScrollPane(this.customVarTable);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // 创建按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JButton addButton = new JButton("添加");
        addButton.addActionListener(e -> {
            this.customVarTableModel.addRow(new Object[]{"", "", ""});
            this.isModified = true;
        });
        
        JButton removeButton = new JButton("删除");
        removeButton.addActionListener(e -> {
            int selectedRow = this.customVarTable.getSelectedRow();
            if (selectedRow >= 0) {
                this.customVarTableModel.removeRow(selectedRow);
                this.isModified = true;
            }
        });
        
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }

    /**
     * 创建高级特性面板
     *
     * @return 高级特性面板
     * @since 1.0.0
     */
    private JPanel createAdvancedFeaturesPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder("高级特性"));
        
        this.saveListener = new JBCheckBox("开启保存监听");
        this.nonStandardDoc = new JBCheckBox("添加非标注释");
        this.nonStandardDoc.setSelected(true);
        
        panel.add(this.saveListener);
        panel.add(this.nonStandardDoc);
        
        return panel;
    }

    /**
     * 创建内置变量面板
     *
     * @return 内置变量面板
     * @since 1.0.0
     */
    private JPanel createBuiltInVarsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("内置变量说明"));
        
        // 创建树形结构
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("内置变量");
        
        // 基础参数
        DefaultMutableTreeNode baseNode = new DefaultMutableTreeNode("基础内置参数");
        DocConfigService config = DocConfigService.getInstance();
        Map<String, String> baseParams = config.getBaseTemplateParameters();
        for (Map.Entry<String, String> entry : baseParams.entrySet()) {
            baseNode.add(new DefaultMutableTreeNode(entry.getKey() + ": " + entry.getValue()));
        }
        root.add(baseNode);
        
        // Hutool工具类
        DefaultMutableTreeNode hutoolNode = new DefaultMutableTreeNode("Hutool 工具类 (str)");
        this.addHutoolMethods(hutoolNode);
        root.add(hutoolNode);
        
        // 类特有参数
        DefaultMutableTreeNode classNode = new DefaultMutableTreeNode("类特有参数");
        classNode.add(new DefaultMutableTreeNode("description: 类描述（默认为类名）"));
        classNode.add(new DefaultMutableTreeNode("parameters: 泛型参数列表 (ParameterInfo)"));
        root.add(classNode);
        
        // 方法特有参数
        DefaultMutableTreeNode methodNode = new DefaultMutableTreeNode("方法特有参数");
        this.addParameterInfoFields(methodNode);
        root.add(methodNode);
        
        // 字段特有参数
        DefaultMutableTreeNode fieldNode = new DefaultMutableTreeNode("字段特有参数");
        fieldNode.add(new DefaultMutableTreeNode("fieldName: 字段名称"));
        fieldNode.add(new DefaultMutableTreeNode("fieldType: 字段类型"));
        root.add(fieldNode);
        
        this.builtInVarsTree = new JTree(root);
        this.builtInVarsTree.setRootVisible(true);
        
        // 设置树单元格渲染器
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        this.builtInVarsTree.setCellRenderer(renderer);
        
        // 展开所有节点
        for (int i = 0; i < this.builtInVarsTree.getRowCount(); i++) {
            this.builtInVarsTree.expandRow(i);
        }
        
        JBScrollPane scrollPane = new JBScrollPane(this.builtInVarsTree);
        scrollPane.setPreferredSize(new Dimension(-1, 250));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    /**
     * 添加Hutool工具类方法到树节点
     *
     * @param hutoolNode Hutool节点
     * @since 1.0.0
     */
    private void addHutoolMethods(DefaultMutableTreeNode hutoolNode) {
        hutoolNode.add(new DefaultMutableTreeNode("isEmpty(str): 判断字符串是否为空"));
        hutoolNode.add(new DefaultMutableTreeNode("isNotEmpty(str): 判断字符串是否非空"));
        hutoolNode.add(new DefaultMutableTreeNode("isBlank(str): 判断字符串是否为空白"));
        hutoolNode.add(new DefaultMutableTreeNode("isNotBlank(str): 判断字符串是否非空白"));
        hutoolNode.add(new DefaultMutableTreeNode("upperFirst(str): 首字符大写"));
        hutoolNode.add(new DefaultMutableTreeNode("lowerFirst(str): 首字符小写"));
        hutoolNode.add(new DefaultMutableTreeNode("sub(str, start, end): 截取字符串"));
        hutoolNode.add(new DefaultMutableTreeNode("subBefore(str, separator, isLastSeparator): 截取分隔符之前的字符串"));
        hutoolNode.add(new DefaultMutableTreeNode("subAfter(str, separator, isLastSeparator): 截取分隔符之后的字符串"));
        hutoolNode.add(new DefaultMutableTreeNode("subBetween(str, before, after): 截取两个字符串之间的字符串"));
        hutoolNode.add(new DefaultMutableTreeNode("split(str, separator): 分割字符串"));
        hutoolNode.add(new DefaultMutableTreeNode("join(iterable, conjunction): 连接字符串"));
        hutoolNode.add(new DefaultMutableTreeNode("format(template, params): 格式化字符串"));
        hutoolNode.add(new DefaultMutableTreeNode("replace(str, searchStr, replacement): 替换字符串"));
        hutoolNode.add(new DefaultMutableTreeNode("toCamelCase(str): 转换为驼峰命名"));
        hutoolNode.add(new DefaultMutableTreeNode("toUnderlineCase(str): 转换为下划线命名"));
    }

    /**
     * 添加ParameterInfo字段到树节点
     *
     * @param node 树节点
     * @since 1.0.0
     */
    private void addParameterInfoFields(DefaultMutableTreeNode node) {
        Field[] fields = ParameterInfo.class.getDeclaredFields();
        for (Field field : fields) {
            try {
                String fieldName = field.getName();
                String fieldDescription = fieldName;
                
                Desc descAnnotation = field.getAnnotation(Desc.class);
                if (descAnnotation != null) {
                    fieldDescription = descAnnotation.value();
                }
                
                node.add(new DefaultMutableTreeNode(fieldName + ": " + fieldDescription));
            } catch (Exception e) {
                // 忽略无法访问的字段
            }
        }
    }

    /**
     * 加载配置数据
     *
     * @since 1.0.0
     */
    private void loadConfigData() {
        DocConfigService config = DocConfigService.getInstance();
        this.classTemplate.setText(config.classTemplate);
        this.methodTemplate.setText(config.methodTemplate);
        this.fieldTemplate.setText(config.fieldTemplate);
        
        // 加载AI提示词
        this.classPrompt.setText(config.classPrompt);
        this.methodPrompt.setText(config.methodPrompt);
        this.fieldPrompt.setText(config.fieldPrompt);
        
        // 加载自定义变量到表格
        this.customVarTableModel.setRowCount(0);
        if (config.customParameters != null) {
            for (Map<String, Object> param : config.customParameters) {
                if (param != null) {
                    String name = (String) param.get("name");
                    String desc = (String) param.get("description");
                    String value = (String) param.get("value");
                    this.customVarTableModel.addRow(new Object[]{name, desc, value});
                }
            }
        }
        
        this.saveListener.setSelected(config.saveListener);
        this.nonStandardDoc.setSelected(config.nonStandardDoc);
    }

    /**
     * 初始化事件监听器 <p> 为界面组件添加事件监听器，用于检测配置修改 </p>
     *
     * @since 1.0.0
     */
    private void initEventListeners() {
        DocumentListener documentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                DocConfig.this.isModified = true;
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                DocConfig.this.isModified = true;
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                DocConfig.this.isModified = true;
            }
        };
        
        this.classTemplate.getDocument().addDocumentListener(documentListener);
        this.methodTemplate.getDocument().addDocumentListener(documentListener);
        this.fieldTemplate.getDocument().addDocumentListener(documentListener);
        
        this.customVarTableModel.addTableModelListener(e -> this.isModified = true);
        this.saveListener.addActionListener(e -> this.isModified = true);
        this.nonStandardDoc.addActionListener(e -> this.isModified = true);
    }

    /**
     * 重新绘制界面 <p> 触发界面的重新绘制，确保组件状态正确更新 </p>
     *
     * @since 1.0.0
     */
    private void repaint() {
        SwingUtilities.invokeLater(() -> {
            if (this.mainPanel != null) {
                this.mainPanel.repaint();
            }
        });
    }
}
