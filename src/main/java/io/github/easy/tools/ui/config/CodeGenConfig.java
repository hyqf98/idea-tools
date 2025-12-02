package io.github.easy.tools.ui.config;

import cn.hutool.core.util.StrUtil;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import io.github.easy.tools.constants.LLMConstants;
import io.github.easy.tools.constants.PromptConstants;
import io.github.easy.tools.service.database.DatabaseMetadataService;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.DefaultListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * <p> AI代码生成配置面板 </p>
 * <p>
 * 用于在 IDEA 设置中管理 AI 代码生成模板，支持模板的增删改查，
 * 每个模板可配置参考文件、生成文件名规则、目标路径以及自定义提示词。
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @since 1.0.0
 */
public class CodeGenConfig implements Configurable {

    /** 主面板 */
    private JPanel mainPanel;
    /** 模板表格 */
    private JBTable templateTable;
    /** 表格模型 */
    private DefaultTableModel tableModel;
    /** 模板名称输入框 */
    private JBTextField templateNameField;
    /** 参考文件路径显示框 */
    private JBTextField referenceFileField;
    /** 生成文件名规则输入框 */
    private JBTextField fileNamePatternField;
    /** 目标目录路径显示框 */
    private JBTextField targetDirField;
    /** 自定义提示词输入框 */
    private JTextArea customPromptArea;

    /** 大模型类型下拉框 */
    private JComboBox<String> modelTypeCombo;

    /** 数据源表格 */
    private JBTable dataSourceTable;
    /** 数据源表格模型 */
    private DefaultTableModel dataSourceTableModel;
    /** 表列表 */
    private JList<String> tableList;
    /** DDL 预览文本区 */
    private JTextArea ddlPreviewArea;
    /** 数据源下拉框 */
    private JComboBox<String> dataSourceCombo;

    /** 配置是否被修改标记 */
    private boolean isModified = false;

    @Override
    public String getDisplayName() {
        return "AI 代码生成配置";
    }

    @Override
    public @Nullable JComponent createComponent() {
        this.mainPanel = new JPanel(new BorderLayout());
        this.mainPanel.setBorder(JBUI.Borders.empty(10));

        // 使用选项卡布局：模板配置、数据源配置、代码生成
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("模板配置", this.createTemplateConfigPanel());
        tabbedPane.addTab("数据源配置", this.createDataSourceConfigPanel());
        tabbedPane.addTab("代码生成", this.createCodeGenerationPanel());

        this.mainPanel.add(tabbedPane, BorderLayout.CENTER);

        this.loadConfigData();
        this.initTableSelectionListener();
        this.initFieldChangeListeners();

        return this.mainPanel;
    }

    @Override
    public boolean isModified() {
        return this.isModified;
    }

    @Override
    public void apply() {
        CodeGenConfigState state = CodeGenConfigState.getInstance();

        // 保存当前编辑模板的详细信息
        int selectedRow = this.templateTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < state.templates.size()) {
            CodeGenConfigState.TemplateConfig currentTemplate = state.templates.get(selectedRow);
            currentTemplate.setName(this.templateNameField.getText());
            currentTemplate.setReferenceFilePath(this.referenceFileField.getText());
            currentTemplate.setFileNamePattern(this.fileNamePatternField.getText());
            currentTemplate.setTargetDir(this.targetDirField.getText());

            // 判断提示词是否为自定义：只有当用户修改了默认提示词时才保存为自定义
            String currentPrompt = this.customPromptArea.getText();
            if (StrUtil.isNotBlank(currentPrompt) && !currentPrompt.equals(PromptConstants.CODE_GENERATION_DEFAULT_PROMPT)) {
                currentTemplate.setUseCustomPrompt(true);
                currentTemplate.setCustomPrompt(currentPrompt);
            } else {
                currentTemplate.setUseCustomPrompt(false);
                currentTemplate.setCustomPrompt("");
            }

            // 保存大模型选择
            String selectedDisplay = (String) this.modelTypeCombo.getSelectedItem();
            currentTemplate.setSelectedModelType(this.getModelTypeFromDisplay(selectedDisplay));

            // 更新表格显示
            this.tableModel.setValueAt(currentTemplate.getName(), selectedRow, 0);
            this.tableModel.setValueAt(currentTemplate.getFileNamePattern(), selectedRow, 1);
            this.tableModel.setValueAt(currentTemplate.getTargetDir(), selectedRow, 2);
        }

        this.isModified = false;
    }

    @Override
    public void reset() {
        this.loadConfigData();
        this.isModified = false;
    }

    @Override
    public void disposeUIResources() {
        this.mainPanel = null;
    }

    /**
     * 创建模板配置面板
     * <p>
     * 将原来的左右分割布局保持，左侧是模板列表，右侧是模板详情
     * </p>
     *
     * @return 模板配置面板
     */
    private JPanel createTemplateConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setBorder(JBUI.Borders.empty());
        
        JPanel leftPanel = this.createTemplateListPanel();
        JPanel rightPanel = this.createTemplateInfoPanel();
        
        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);
        splitPane.setDividerLocation(300);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createTemplateListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 0, 0, 0, 1));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        header.add(new JBLabel("模板列表"));

        JButton addButton = new JButton("新增模板");
        JButton deleteButton = new JButton("删除模板");

        addButton.addActionListener(e -> this.addTemplate());
        deleteButton.addActionListener(e -> this.deleteSelectedTemplate());

        header.add(addButton);
        header.add(deleteButton);

        this.tableModel = new DefaultTableModel(new Object[]{"名称", "生成文件名规则", "生成路径"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.templateTable = new JBTable(this.tableModel);
        this.templateTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.templateTable.setPreferredScrollableViewportSize(new Dimension(240, 400));

        JBScrollPane scrollPane = new JBScrollPane(this.templateTable);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 创建数据源配置面板
     * <p>
     * 展示数据源列表，支持增删改查
     * </p>
     *
     * @return 数据源配置面板
     */
    private JPanel createDataSourceConfigPanel() {
        return this.createDataSourcePanel();
    }

    /**
     * 创建代码生成面板
     * <p>
     * 支持批量选择模板和数据库表，然后批量生成代码
     * </p>
     *
     * @return 代码生成面板
     */
    private JPanel createCodeGenerationPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(JBUI.Borders.empty(15));

        // 创建主内容区域
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));

        // 左侧：模板选择区域
        JPanel templateSelectionPanel = this.createTemplateSelectionPanel();
        
        // 中间：数据源和表选择区域
        JPanel tableSelectionPanel = this.createTableSelectionPanelForGeneration();
        
        // 使用水平分割面板
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(templateSelectionPanel);
        splitPane.setRightComponent(tableSelectionPanel);
        splitPane.setDividerLocation(400);
        
        contentPanel.add(splitPane, BorderLayout.CENTER);
        
        // 底部：生成按钮区域
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton generateButton = new JButton("开始生成代码");
        generateButton.setPreferredSize(new Dimension(150, 35));
        generateButton.addActionListener(e -> this.generateCode());
        buttonPanel.add(generateButton);
        
        panel.add(contentPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createTemplateInfoPanel() {

        JPanel formPanel = new JPanel();
        formPanel.setLayout(new com.intellij.util.ui.FormBuilder().getPanel().getLayout());

        this.templateNameField = new JBTextField();
        this.referenceFileField = new JBTextField();
        this.referenceFileField.setEditable(false);
        this.fileNamePatternField = new JBTextField("${tableName}Generated.java");
        this.targetDirField = new JBTextField();
        this.targetDirField.setEditable(false);
        this.customPromptArea = new JTextArea(8, 40);
        this.customPromptArea.setLineWrap(true);
        this.customPromptArea.setWrapStyleWord(true);

        // 大模型选择下拉框
        this.modelTypeCombo = new JComboBox<>();
        this.loadModelTypes();

        JButton chooseReferenceButton = new JButton("选择参考文件");
        chooseReferenceButton.addActionListener(e -> this.chooseReferenceFile());

        JButton chooseTargetDirButton = new JButton("选择生成路径");
        chooseTargetDirButton.addActionListener(e -> this.chooseTargetDir());

        JPanel referencePanel = new JPanel(new BorderLayout(5, 0));
        referencePanel.add(this.referenceFileField, BorderLayout.CENTER);
        referencePanel.add(chooseReferenceButton, BorderLayout.EAST);

        JPanel targetDirPanel = new JPanel(new BorderLayout(5, 0));
        targetDirPanel.add(this.targetDirField, BorderLayout.CENTER);
        targetDirPanel.add(chooseTargetDirButton, BorderLayout.EAST);

        com.intellij.util.ui.FormBuilder builder = com.intellij.util.ui.FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("模板名称:"), this.templateNameField)
                .addVerticalGap(5)
                .addLabeledComponent(new JBLabel("模型配置:"), this.modelTypeCombo)
                .addVerticalGap(5)
                .addLabeledComponent(new JBLabel("参考文件:"), referencePanel)
                .addComponentToRightColumn(new JBLabel("（大模型将参考此文件的代码风格）"), 0)
                .addVerticalGap(5)
                .addLabeledComponent(new JBLabel("生成文件名规则:"), this.fileNamePatternField)
                .addComponentToRightColumn(new JBLabel("支持占位符: ${tableName}, ${camelTableName}"), 0)
                .addVerticalGap(5)
                .addLabeledComponent(new JBLabel("生成路径:"), targetDirPanel)
                .addVerticalGap(10)
                .addLabeledComponent(new JBLabel("自定义提示词:"), new JBScrollPane(this.customPromptArea))
                .addComponentToRightColumn(new JBLabel("（为空则使用默认提示词）"), 0);

        formPanel = builder.getPanel();

        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(formPanel, BorderLayout.NORTH);
        wrapperPanel.setBorder(JBUI.Borders.empty(10));

        return wrapperPanel;
    }

    /**
     * 加载大模型类型列表
     * <p>
     * 直接使用LLMConstants中定义的所有模型厂商，而不是从 modelConfigs 动态读取
     * 这样可以确保启动时就能看到所有可用的模型选项
     * </p>
     */
    private void loadModelTypes() {
        this.modelTypeCombo.removeAllItems();
        this.modelTypeCombo.addItem("使用默认模型");
            
        // 直接使用固定的模型厂商列表，与LLMConfig中的一致
        this.modelTypeCombo.addItem(LLMConstants.ModelDisplayName.OPENAI);
        this.modelTypeCombo.addItem(LLMConstants.ModelDisplayName.OLLAMA);
        this.modelTypeCombo.addItem(LLMConstants.ModelDisplayName.AZURE);
        this.modelTypeCombo.addItem(LLMConstants.ModelDisplayName.GEMINI);
        this.modelTypeCombo.addItem(LLMConstants.ModelDisplayName.CLAUDE);
        this.modelTypeCombo.addItem(LLMConstants.ModelDisplayName.DEEPSEEK);
        this.modelTypeCombo.addItem(LLMConstants.ModelDisplayName.QWEN);
        this.modelTypeCombo.addItem(LLMConstants.ModelDisplayName.GLM);
        this.modelTypeCombo.addItem(LLMConstants.ModelDisplayName.WENXIN);
    }

    /**
     * 获取模型显示名称
     *
     * @param modelType 模型类型
     * @return 显示名称
     */
    private String getModelDisplayName(String modelType) {
        return switch (modelType) {
            case LLMConstants.ModelType.OPENAI -> LLMConstants.ModelDisplayName.OPENAI;
            case LLMConstants.ModelType.OLLAMA -> LLMConstants.ModelDisplayName.OLLAMA;
            case LLMConstants.ModelType.AZURE -> LLMConstants.ModelDisplayName.AZURE;
            case LLMConstants.ModelType.GEMINI -> LLMConstants.ModelDisplayName.GEMINI;
            case LLMConstants.ModelType.CLAUDE -> LLMConstants.ModelDisplayName.CLAUDE;
            case LLMConstants.ModelType.DEEPSEEK -> LLMConstants.ModelDisplayName.DEEPSEEK;
            case LLMConstants.ModelType.QWEN -> LLMConstants.ModelDisplayName.QWEN;
            case LLMConstants.ModelType.GLM -> LLMConstants.ModelDisplayName.GLM;
            case LLMConstants.ModelType.WENXIN -> LLMConstants.ModelDisplayName.WENXIN;
            default -> modelType;
        };
    }

    /**
     * 将显示名称转换为模型类型
     *
     * @param displayName 显示名称
     * @return 模型类型
     */
    private String getModelTypeFromDisplay(String displayName) {
        if ("使用默认模型".equals(displayName)) {
            return "";
        }
        return switch (displayName) {
            case LLMConstants.ModelDisplayName.OPENAI -> LLMConstants.ModelType.OPENAI;
            case LLMConstants.ModelDisplayName.OLLAMA -> LLMConstants.ModelType.OLLAMA;
            case LLMConstants.ModelDisplayName.AZURE -> LLMConstants.ModelType.AZURE;
            case LLMConstants.ModelDisplayName.GEMINI -> LLMConstants.ModelType.GEMINI;
            case LLMConstants.ModelDisplayName.CLAUDE -> LLMConstants.ModelType.CLAUDE;
            case LLMConstants.ModelDisplayName.DEEPSEEK -> LLMConstants.ModelType.DEEPSEEK;
            case LLMConstants.ModelDisplayName.QWEN -> LLMConstants.ModelType.QWEN;
            case LLMConstants.ModelDisplayName.GLM -> LLMConstants.ModelType.GLM;
            case LLMConstants.ModelDisplayName.WENXIN -> LLMConstants.ModelType.WENXIN;
            default -> "";
        };
    }

    private void loadConfigData() {
        CodeGenConfigState state = CodeGenConfigState.getInstance();
        this.tableModel.setRowCount(0);

        // 在加载数据时，为每个模板初始化默认提示词（如果没有自定义提示词）
        for (CodeGenConfigState.TemplateConfig template : state.templates) {
            // 如果模板没有自定义提示词，且存储的提示词为空，则设置为使用默认提示词
            if (!template.isUseCustomPrompt() && StrUtil.isBlank(template.getCustomPrompt())) {
                template.setUseCustomPrompt(false);
                template.setCustomPrompt("");
            }
            this.tableModel.addRow(new Object[]{template.getName(), template.getFileNamePattern(), template.getTargetDir()});
        }

        if (this.tableModel.getRowCount() > 0) {
            this.templateTable.setRowSelectionInterval(0, 0);
            this.loadTemplateDetail(0);
        } else {
            // 如果没有任何模板，也显示默认提示词
            this.customPromptArea.setText(PromptConstants.CODE_GENERATION_DEFAULT_PROMPT);
        }
    }

    private void initTableSelectionListener() {
        this.templateTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = this.templateTable.getSelectedRow();
                if (selectedRow >= 0) {
                    this.loadTemplateDetail(selectedRow);
                }
            }
        });
    }

    /**
     * 初始化字段变更监听器
     * <p>
     * 为所有输入字段添加变更监听，当字段内容变化时设置 isModified = true
     * </p>
     */
    private void initFieldChangeListeners() {
        // 模板名称变更监听
        this.templateNameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                CodeGenConfig.this.isModified = true;
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                CodeGenConfig.this.isModified = true;
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                CodeGenConfig.this.isModified = true;
            }
        });

        // 生成文件名规则变更监听
        this.fileNamePatternField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                CodeGenConfig.this.isModified = true;
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                CodeGenConfig.this.isModified = true;
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                CodeGenConfig.this.isModified = true;
            }
        });

        // 自定义提示词变更监听
        this.customPromptArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                CodeGenConfig.this.isModified = true;
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                CodeGenConfig.this.isModified = true;
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                CodeGenConfig.this.isModified = true;
            }
        });

        // 大模型选择变更监听
        this.modelTypeCombo.addActionListener(e -> CodeGenConfig.this.isModified = true);
    }

    private void loadTemplateDetail(int rowIndex) {
        CodeGenConfigState state = CodeGenConfigState.getInstance();
        if (rowIndex < 0 || rowIndex >= state.templates.size()) {
            return;
        }
            
        CodeGenConfigState.TemplateConfig template = state.templates.get(rowIndex);
            
        // 加载模板详情时，不触发 isModified，因为这只是加载数据而不是修改
        // 需要在设置完所有字段后再将 isModified 设为 false
        this.templateNameField.setText(template.getName());
        this.referenceFileField.setText(template.getReferenceFilePath());
        this.fileNamePatternField.setText(template.getFileNamePattern());
        this.targetDirField.setText(template.getTargetDir());
            
        // 加载提示词：如果有自定义提示词则显示，否则显示默认提示词
        if (template.isUseCustomPrompt() && StrUtil.isNotBlank(template.getCustomPrompt())) {
            this.customPromptArea.setText(template.getCustomPrompt());
        } else {
            this.customPromptArea.setText(PromptConstants.CODE_GENERATION_DEFAULT_PROMPT);
        }
            
        // 加载大模型选择
        if (StrUtil.isNotBlank(template.getSelectedModelType())) {
            String displayName = this.getModelDisplayName(template.getSelectedModelType());
            this.modelTypeCombo.setSelectedItem(displayName);
        } else {
            this.modelTypeCombo.setSelectedItem("使用默认模型");
        }
            
        // 加载完成后，重置 isModified 为 false
        // 使用 SwingUtilities.invokeLater 确保在所有事件处理完毕后再设置
        SwingUtilities.invokeLater(() -> CodeGenConfig.this.isModified = false);
    }

    private void addTemplate() {
        CodeGenConfigState state = CodeGenConfigState.getInstance();
        CodeGenConfigState.TemplateConfig template = new CodeGenConfigState.TemplateConfig();
        template.setName("新模板" + (state.templates.size() + 1));
        state.templates.add(template);

        this.tableModel.addRow(new Object[]{template.getName(), template.getFileNamePattern(), template.getTargetDir()});
        int newIndex = this.tableModel.getRowCount() - 1;
        this.templateTable.setRowSelectionInterval(newIndex, newIndex);

        // 加载新模板详情，显示默认提示词
        this.templateNameField.setText(template.getName());
        this.referenceFileField.setText("");
        this.fileNamePatternField.setText(template.getFileNamePattern());
        this.targetDirField.setText("");
        this.customPromptArea.setText(PromptConstants.CODE_GENERATION_DEFAULT_PROMPT);
        this.modelTypeCombo.setSelectedItem("使用默认模型");

        this.isModified = true;
    }

    private void deleteSelectedTemplate() {
        int selectedRow = this.templateTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }

        int result = Messages.showYesNoDialog("确定要删除选中的模板吗？", "删除模板", null);
        if (result != Messages.YES) {
            return;
        }

        CodeGenConfigState state = CodeGenConfigState.getInstance();
        if (selectedRow < state.templates.size()) {
            state.templates.remove(selectedRow);
        }
        this.tableModel.removeRow(selectedRow);

        if (this.tableModel.getRowCount() > 0) {
            int newIndex = Math.max(0, selectedRow - 1);
            this.templateTable.setRowSelectionInterval(newIndex, newIndex);
            this.loadTemplateDetail(newIndex);
        } else {
            this.templateNameField.setText("");
            this.referenceFileField.setText("");
            this.fileNamePatternField.setText("${tableName}Generated.java");
            this.targetDirField.setText("");
            this.customPromptArea.setText(PromptConstants.CODE_GENERATION_DEFAULT_PROMPT);
            this.modelTypeCombo.setSelectedItem("使用默认模型");
        }
        this.isModified = true;
    }

    private void chooseReferenceFile() {
        Project project = this.getCurrentProject();
        if (project == null) {
            return;
        }
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false);
        FileChooser.chooseFile(descriptor, project, null, file -> {
            String path = file.getPath();
            this.referenceFileField.setText(path);
            this.isModified = true;
        });
    }

    private void chooseTargetDir() {
        Project project = this.getCurrentProject();
        if (project == null) {
            return;
        }
        FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
        FileChooser.chooseFile(descriptor, project, null, file -> {
            String path = file.getPath();
            this.targetDirField.setText(path);
            this.isModified = true;
        });
    }

    @Nullable
    private Project getCurrentProject() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        if (projects.length == 0) {
            return null;
        }
        return projects[0];
    }

    /**
     * 创建数据源配置面板
     *
     * @return 数据源配置面板
     */
    private JPanel createDataSourcePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(JBUI.Borders.empty(10));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        header.add(new JBLabel("数据源列表"));

        JButton addButton = new JButton("新增数据源");
        JButton editButton = new JButton("编辑数据源");
        JButton deleteButton = new JButton("删除数据源");

        addButton.addActionListener(e -> this.addDataSource());
        editButton.addActionListener(e -> this.editDataSource());
        deleteButton.addActionListener(e -> this.deleteDataSource());

        header.add(addButton);
        header.add(editButton);
        header.add(deleteButton);

        this.dataSourceTableModel = new DefaultTableModel(new Object[]{"名称", "JDBC URL", "用户名"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.dataSourceTable = new JBTable(this.dataSourceTableModel);
        this.dataSourceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JBScrollPane scrollPane = new JBScrollPane(this.dataSourceTable);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        this.loadDataSourceList();

        return panel;
    }

    /**
     * 创建表选择与 DDL 预览面板
     *
     * @return 表选择面板
     */
    private JPanel createTableSelectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(JBUI.Borders.empty(10));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        topPanel.add(new JBLabel("数据源:"));

        this.dataSourceCombo = new JComboBox<>();
        this.dataSourceCombo.setPreferredSize(new Dimension(200, 30));
        this.dataSourceCombo.addActionListener(e -> this.loadTablesForDataSource());
        topPanel.add(this.dataSourceCombo);

        JButton refreshButton = new JButton("刷新表列表");
        refreshButton.addActionListener(e -> this.loadTablesForDataSource());
        topPanel.add(refreshButton);

        panel.add(topPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setBorder(JBUI.Borders.empty());

        this.tableList = new JList<>();
        this.tableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JBScrollPane tableScrollPane = new JBScrollPane(this.tableList);
        tableScrollPane.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 0, 0, 0, 1));

        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBorder(JBUI.Borders.emptyLeft(10));

        JPanel ddlHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        ddlHeader.add(new JBLabel("DDL 预览"));
        JButton viewDdlButton = new JButton("查看 DDL");
        viewDdlButton.addActionListener(e -> this.viewTableDDL());
        ddlHeader.add(viewDdlButton);

        this.ddlPreviewArea = new JTextArea();
        this.ddlPreviewArea.setEditable(false);
        this.ddlPreviewArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        JBScrollPane ddlScrollPane = new JBScrollPane(this.ddlPreviewArea);

        rightPanel.add(ddlHeader, BorderLayout.NORTH);
        rightPanel.add(ddlScrollPane, BorderLayout.CENTER);

        splitPane.setLeftComponent(tableScrollPane);
        splitPane.setRightComponent(rightPanel);
        splitPane.setDividerLocation(200);

        panel.add(splitPane, BorderLayout.CENTER);

        this.loadDataSourceCombo();

        return panel;
    }

    /**
     * 加载数据源列表
     */
    private void loadDataSourceList() {
        CodeGenConfigState state = CodeGenConfigState.getInstance();
        this.dataSourceTableModel.setRowCount(0);

        for (CodeGenConfigState.DataSourceConfig ds : state.dataSources) {
            this.dataSourceTableModel.addRow(new Object[]{ds.getName(), ds.getJdbcUrl(), ds.getUsername()});
        }
    }

    /**
     * 加载数据源下拉框
     */
    private void loadDataSourceCombo() {
        CodeGenConfigState state = CodeGenConfigState.getInstance();
        this.dataSourceCombo.removeAllItems();

        for (CodeGenConfigState.DataSourceConfig ds : state.dataSources) {
            this.dataSourceCombo.addItem(ds.getName());
        }
    }

    /**
     * 新增数据源
     */
    private void addDataSource() {
        DataSourceConfigDialog dialog = new DataSourceConfigDialog();
        if (dialog.showAndGet()) {
            CodeGenConfigState state = CodeGenConfigState.getInstance();
            state.dataSources.add(dialog.getDataSource());
            this.loadDataSourceList();
            this.loadDataSourceCombo();
            this.isModified = true;
        }
    }

    /**
     * 编辑数据源
     */
    private void editDataSource() {
        int selectedRow = this.dataSourceTable.getSelectedRow();
        if (selectedRow < 0) {
            Messages.showWarningDialog("请先选择要编辑的数据源", "提示");
            return;
        }

        CodeGenConfigState state = CodeGenConfigState.getInstance();
        if (selectedRow >= state.dataSources.size()) {
            return;
        }

        CodeGenConfigState.DataSourceConfig ds = state.dataSources.get(selectedRow);
        DataSourceConfigDialog dialog = new DataSourceConfigDialog(ds);
        if (dialog.showAndGet()) {
            this.loadDataSourceList();
            this.loadDataSourceCombo();
            this.isModified = true;
        }
    }

    /**
     * 删除数据源
     */
    private void deleteDataSource() {
        int selectedRow = this.dataSourceTable.getSelectedRow();
        if (selectedRow < 0) {
            Messages.showWarningDialog("请先选择要删除的数据源", "提示");
            return;
        }

        int result = Messages.showYesNoDialog("确定要删除选中的数据源吗？", "删除数据源", null);
        if (result != Messages.YES) {
            return;
        }

        CodeGenConfigState state = CodeGenConfigState.getInstance();
        if (selectedRow < state.dataSources.size()) {
            state.dataSources.remove(selectedRow);
        }
        this.loadDataSourceList();
        this.loadDataSourceCombo();
        this.isModified = true;
    }

    /**
     * 加载选中数据源的表列表
     */
    private void loadTablesForDataSource() {
        String selectedDsName = (String) this.dataSourceCombo.getSelectedItem();
        if (StrUtil.isBlank(selectedDsName)) {
            return;
        }

        CodeGenConfigState state = CodeGenConfigState.getInstance();
        CodeGenConfigState.DataSourceConfig ds = state.dataSources.stream()
                .filter(d -> d.getName().equals(selectedDsName))
                .findFirst()
                .orElse(null);

        if (ds == null) {
            return;
        }

        try {
            DatabaseMetadataService service = new DatabaseMetadataService();
            java.util.List<String> tables = service.listTables(ds);
            this.tableList.setListData(tables.toArray(new String[0]));
        } catch (Exception e) {
            Messages.showErrorDialog("获取表列表失败：" + e.getMessage(), "错误");
        }
    }

    /**
     * 查看选中表的 DDL
     */
    private void viewTableDDL() {
        String selectedTable = this.tableList.getSelectedValue();
        if (StrUtil.isBlank(selectedTable)) {
            Messages.showWarningDialog("请先选择一个表", "提示");
            return;
        }

        String selectedDsName = (String) this.dataSourceCombo.getSelectedItem();
        if (StrUtil.isBlank(selectedDsName)) {
            return;
        }

        CodeGenConfigState state = CodeGenConfigState.getInstance();
        CodeGenConfigState.DataSourceConfig ds = state.dataSources.stream()
                .filter(d -> d.getName().equals(selectedDsName))
                .findFirst()
                .orElse(null);

        if (ds == null) {
            return;
        }

        DatabaseMetadataService service = new DatabaseMetadataService();
        String ddl = service.getTableDDL(ds, selectedTable);
        this.ddlPreviewArea.setText(ddl);
    }

    /**
     * 创建模板选择面板（用于代码生成）
     * <p>
     * 显示所有可用模板，支持多选
     * </p>
     *
     * @return 模板选择面板
     */
    private JPanel createTemplateSelectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(JBUI.Borders.empty(10));

        JBLabel titleLabel = new JBLabel("选择生成模板（可多选）");
        titleLabel.setFont(titleLabel.getFont().deriveFont(14f));
        panel.add(titleLabel, BorderLayout.NORTH);

        // 创建模板列表
        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> templateList = new JList<>(listModel);
        templateList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        // 加载所有模板
        CodeGenConfigState state = CodeGenConfigState.getInstance();
        for (CodeGenConfigState.TemplateConfig template : state.templates) {
            listModel.addElement(template.getName());
        }

        JBScrollPane scrollPane = new JBScrollPane(templateList);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 创建表选择面板（用于代码生成）
     * <p>
     * 显示数据源和表列表，支持多选
     * </p>
     *
     * @return 表选择面板
     */
    private JPanel createTableSelectionPanelForGeneration() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(JBUI.Borders.empty(10));

        // 顶部：数据源选择
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        topPanel.add(new JBLabel("选择数据源:"));

        JComboBox<String> genDataSourceCombo = new JComboBox<>();
        genDataSourceCombo.setPreferredSize(new Dimension(200, 30));
        topPanel.add(genDataSourceCombo);

        JButton refreshButton = new JButton("刷新表列表");
        refreshButton.addActionListener(e -> {
            String selectedDs = (String) genDataSourceCombo.getSelectedItem();
            if (StrUtil.isNotBlank(selectedDs)) {
                this.loadTablesForGeneration(genDataSourceCombo, genDataSourceCombo);
            }
        });
        topPanel.add(refreshButton);

        panel.add(topPanel, BorderLayout.NORTH);

        // 中间：表列表
        JBLabel tableLabel = new JBLabel("选择数据库表（可多选）");
        tableLabel.setFont(tableLabel.getFont().deriveFont(14f));
        tableLabel.setBorder(JBUI.Borders.emptyTop(10));

        DefaultListModel<String> tableListModel = new DefaultListModel<>();
        JList<String> genTableList = new JList<>(tableListModel);
        genTableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(tableLabel, BorderLayout.NORTH);
        centerPanel.add(new JBScrollPane(genTableList), BorderLayout.CENTER);

        panel.add(centerPanel, BorderLayout.CENTER);

        // 加载数据源列表
        CodeGenConfigState state = CodeGenConfigState.getInstance();
        for (CodeGenConfigState.DataSourceConfig ds : state.dataSources) {
            genDataSourceCombo.addItem(ds.getName());
        }

        // 数据源变更监听
        genDataSourceCombo.addActionListener(e -> {
            String selectedDs = (String) genDataSourceCombo.getSelectedItem();
            if (StrUtil.isNotBlank(selectedDs)) {
                tableListModel.clear();
                CodeGenConfigState.DataSourceConfig ds = state.dataSources.stream()
                        .filter(d -> d.getName().equals(selectedDs))
                        .findFirst()
                        .orElse(null);

                if (ds != null) {
                    DatabaseMetadataService service = new DatabaseMetadataService();
                    try {
                        java.util.List<String> tables = service.listTables(ds);
                        for (String table : tables) {
                            tableListModel.addElement(table);
                        }
                    } catch (Exception ex) {
                        Messages.showErrorDialog("加载表列表失败: " + ex.getMessage(), "错误");
                    }
                }
            }
        });

        return panel;
    }

    /**
     * 加载表列表（用于代码生成）
     *
     * @param dataSourceCombo 数据源下拉框
     * @param tableListCombo  表列表下拉框
     */
    private void loadTablesForGeneration(JComboBox<String> dataSourceCombo, JComboBox<String> tableListCombo) {
        // 这里可以添加刷新逻辑，如果需要
    }

    /**
     * 执行代码生成
     * <p>
     * 遍历选中的每个模板和每张表，生成对应的代码文件
     * </p>
     */
    private void generateCode() {
        Messages.showInfoMessage("代码生成功能正在开发中...", "提示");
        // TODO: 实现批量生成逻辑
    }
}
