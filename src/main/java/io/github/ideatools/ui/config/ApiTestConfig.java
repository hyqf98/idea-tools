package io.github.ideatools.ui.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP接口测试配置
 * 在 Settings -> Easy Config 中配置
 *
 * @author haijun
 * @since 2.0.0
 */
public class ApiTestConfig implements Configurable {

    private final Project project;

    // 基础配置
    private JTextField baseUrlField;

    // 全局请求头
    private JBTable headersTable;
    private DefaultTableModel headersModel;

    // 前置请求配置
    private JCheckBox preRequestEnabled;
    private JTextField preRequestUrlField;
    private JComboBox<String> preRequestMethodCombo;
    private JTextArea preRequestBodyArea;
    private JTextField tokenJsonPathField;
    private JTextField tokenHeaderNameField;
    private JCheckBox useBearerCheckBox;
    private JBTable preRequestHeadersTable;
    private DefaultTableModel preRequestHeadersModel;

    // 原始配置备份（用于检测修改）
    private String originalBaseUrl;
    private boolean originalPreRequestEnabled;
    private List<ApiTestConfigState.HeaderItem> originalCommonHeaders;
    private ApiTestConfigState.PreRequestConfig originalPreRequest;

    public ApiTestConfig(Project project) {
        this.project = project;
        this.loadOriginalConfig();
    }

    private void loadOriginalConfig() {
        ApiTestConfigState state = ApiTestConfigState.getInstance(this.project);
        this.originalBaseUrl = state.baseUrl;
        this.originalPreRequestEnabled = state.preRequestEnabled;
        this.originalCommonHeaders = new ArrayList<>(state.commonHeaders);
        this.originalPreRequest = state.preRequest;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "HTTP接口测试";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 顶部：基础配置
        JPanel topPanel = this.createBaseUrlPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // 中部：左右分割
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(null);

        // 左侧：全局请求头
        JPanel leftPanel = this.createHeadersPanel();
        splitPane.setLeftComponent(leftPanel);

        // 右侧：前置请求配置
        JPanel rightPanel = this.createPreRequestPanel();
        splitPane.setRightComponent(rightPanel);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        // 加载配置
        this.loadConfig();

        return mainPanel;
    }

    private JPanel createBaseUrlPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        TitledBorder border = BorderFactory.createTitledBorder("服务基础地址");
        border.setTitleFont(border.getTitleFont().deriveFont(Font.PLAIN, 13f));
        panel.setBorder(border);

        JPanel content = new JPanel(new BorderLayout(10, 0));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        content.add(new JLabel("Base URL:"), BorderLayout.WEST);

        this.baseUrlField = new JTextField();
        this.baseUrlField.setPreferredSize(new Dimension(400, 28));
        content.add(this.baseUrlField, BorderLayout.CENTER);

        JLabel hintLabel = new JLabel("示例: http://localhost:8080");
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 11f));
        content.add(hintLabel, BorderLayout.EAST);

        panel.add(content, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createHeadersPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        TitledBorder border = BorderFactory.createTitledBorder("全局请求头");
        border.setTitleFont(border.getTitleFont().deriveFont(Font.PLAIN, 13f));
        panel.setBorder(border);

        // 按钮面板
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        JButton addBtn = new JButton("添加");
        addBtn.setMargin(new Insets(3, 10, 3, 10));
        addBtn.addActionListener(e -> this.addHeader());
        JButton removeBtn = new JButton("删除");
        removeBtn.setMargin(new Insets(3, 10, 3, 10));
        removeBtn.addActionListener(e -> this.removeHeader());
        btnPanel.add(addBtn);
        btnPanel.add(removeBtn);
        panel.add(btnPanel, BorderLayout.NORTH);

        // 表格
        String[] columns = {"名称", "类型", "值/表达式", "来源URL", "方法", "Body"};
        this.headersModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };
        this.headersTable = new JBTable(this.headersModel);
        this.headersTable.setRowHeight(26);
        this.headersTable.getTableHeader().setReorderingAllowed(false);

        // 类型列使用下拉框
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"FIXED", "DYNAMIC"});
        this.headersTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(typeCombo));

        // 方法列使用下拉框
        JComboBox<String> methodCombo = new JComboBox<>(new String[]{"GET", "POST", "PUT", "DELETE"});
        this.headersTable.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(methodCombo));

        panel.add(new JBScrollPane(this.headersTable), BorderLayout.CENTER);

        // 说明
        JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        JLabel hintLabel = new JLabel("<html><body style='width: 350px'>全局请求头将应用于所有API测试。类型为DYNAMIC时，会先调用来源接口获取值。<br/>表达式示例: ${response.data.token}</body></html>");
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 11f));
        hintPanel.add(hintLabel);
        panel.add(hintPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createPreRequestPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        TitledBorder border = BorderFactory.createTitledBorder("前置请求 (如登录获取Token)");
        border.setTitleFont(border.getTitleFont().deriveFont(Font.PLAIN, 13f));
        panel.setBorder(border);

        JPanel content = new JPanel(new BorderLayout(5, 10));
        content.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        // 顶部：启用开关和基本配置
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        this.preRequestEnabled = new JCheckBox("启用前置请求");
        topPanel.add(this.preRequestEnabled);
        content.add(topPanel, BorderLayout.NORTH);

        // 中部：配置内容
        JPanel configPanel = new JPanel(new BorderLayout(5, 5));

        // URL和方法
        JPanel urlPanel = new JPanel(new BorderLayout(5, 0));
        JPanel urlLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        urlLine.add(new JLabel("URL:"));
        this.preRequestUrlField = new JTextField(25);
        urlLine.add(this.preRequestUrlField);
        urlLine.add(new JLabel("方法:"));
        this.preRequestMethodCombo = new JComboBox<>(new String[]{"GET", "POST", "PUT", "DELETE"});
        this.preRequestMethodCombo.setPreferredSize(new Dimension(80, 26));
        urlLine.add(this.preRequestMethodCombo);
        urlPanel.add(urlLine, BorderLayout.NORTH);

        // 请求体
        JPanel bodyPanel = new JPanel(new BorderLayout(3, 3));
        bodyPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        bodyPanel.add(new JLabel("请求体 (JSON):"), BorderLayout.NORTH);
        this.preRequestBodyArea = new JTextArea(4, 30);
        this.preRequestBodyArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        bodyPanel.add(new JBScrollPane(this.preRequestBodyArea), BorderLayout.CENTER);
        urlPanel.add(bodyPanel, BorderLayout.CENTER);

        configPanel.add(urlPanel, BorderLayout.NORTH);

        // Token配置
        JPanel tokenPanel = new JPanel(new BorderLayout(5, 5));
        TitledBorder tokenBorder = BorderFactory.createTitledBorder("Token配置");
        tokenBorder.setTitleFont(tokenBorder.getTitleFont().deriveFont(Font.PLAIN, 12f));
        tokenPanel.setBorder(tokenBorder);

        JPanel tokenConfigPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        tokenConfigPanel.add(new JLabel("提取路径:"));
        this.tokenJsonPathField = new JTextField(15);
        this.tokenJsonPathField.setToolTipText("如: data.token 或 access_token");
        tokenConfigPanel.add(this.tokenJsonPathField);

        tokenConfigPanel.add(new JLabel("请求头名称:"));
        this.tokenHeaderNameField = new JTextField(12);
        this.tokenHeaderNameField.setText("Authorization");
        tokenConfigPanel.add(this.tokenHeaderNameField);

        this.useBearerCheckBox = new JCheckBox("使用Bearer前缀");
        this.useBearerCheckBox.setSelected(true);
        tokenConfigPanel.add(this.useBearerCheckBox);

        tokenPanel.add(tokenConfigPanel, BorderLayout.NORTH);

        // 前置请求的自定义请求头
        JPanel preHeadersPanel = new JPanel(new BorderLayout(3, 3));
        preHeadersPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        JPanel preHeaderBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 2));
        JButton addPreHeaderBtn = new JButton("+");
        addPreHeaderBtn.setMargin(new Insets(2, 6, 2, 6));
        addPreHeaderBtn.addActionListener(e -> this.addPreRequestHeader());
        JButton removePreHeaderBtn = new JButton("-");
        removePreHeaderBtn.setMargin(new Insets(2, 6, 2, 6));
        removePreHeaderBtn.addActionListener(e -> this.removePreRequestHeader());
        preHeaderBtnPanel.add(addPreHeaderBtn);
        preHeaderBtnPanel.add(removePreHeaderBtn);
        preHeadersPanel.add(preHeaderBtnPanel, BorderLayout.NORTH);

        String[] preHeaderColumns = {"名称", "值"};
        this.preRequestHeadersModel = new DefaultTableModel(preHeaderColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };
        this.preRequestHeadersTable = new JBTable(this.preRequestHeadersModel);
        this.preRequestHeadersTable.setRowHeight(24);
        preHeadersPanel.add(new JBScrollPane(this.preRequestHeadersTable) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(super.getPreferredSize().width, 80);
            }
        }, BorderLayout.CENTER);

        tokenPanel.add(preHeadersPanel, BorderLayout.CENTER);

        configPanel.add(tokenPanel, BorderLayout.CENTER);

        content.add(configPanel, BorderLayout.CENTER);

        panel.add(content, BorderLayout.CENTER);

        return panel;
    }

    private void addHeader() {
        this.headersModel.addRow(new Object[]{"", "FIXED", "", "", "GET", ""});
    }

    private void removeHeader() {
        int selectedRow = this.headersTable.getSelectedRow();
        if (selectedRow >= 0) {
            this.headersModel.removeRow(selectedRow);
        }
    }

    private void addPreRequestHeader() {
        this.preRequestHeadersModel.addRow(new Object[]{"", ""});
    }

    private void removePreRequestHeader() {
        int selectedRow = this.preRequestHeadersTable.getSelectedRow();
        if (selectedRow >= 0) {
            this.preRequestHeadersModel.removeRow(selectedRow);
        }
    }

    private void loadConfig() {
        ApiTestConfigState state = ApiTestConfigState.getInstance(this.project);

        // 加载基础配置
        this.baseUrlField.setText(state.baseUrl);

        // 加载全局请求头
        this.headersModel.setRowCount(0);
        if (state.commonHeaders != null) {
            for (ApiTestConfigState.HeaderItem item : state.commonHeaders) {
                this.headersModel.addRow(new Object[]{
                    item.getName(),
                    item.getValueType() == null ? "FIXED" : item.getValueType().name(),
                    item.getValue(),
                    item.getSourceUrl(),
                    item.getSourceMethod(),
                    item.getSourceBody()
                });
            }
        }

        // 加载前置请求配置
        this.preRequestEnabled.setSelected(state.preRequestEnabled);
        if (state.preRequest != null) {
            this.preRequestUrlField.setText(state.preRequest.getUrl());
            this.preRequestMethodCombo.setSelectedItem(state.preRequest.getMethod());
            this.preRequestBodyArea.setText(state.preRequest.getBodyJson());
            this.tokenJsonPathField.setText(state.preRequest.getTokenJsonPath());
            this.tokenHeaderNameField.setText(state.preRequest.getTokenHeaderName());
            this.useBearerCheckBox.setSelected(state.preRequest.isUseBearer());

            this.preRequestHeadersModel.setRowCount(0);
            if (state.preRequest.getHeaders() != null) {
                for (ApiTestConfigState.HeaderItem item : state.preRequest.getHeaders()) {
                    this.preRequestHeadersModel.addRow(new Object[]{
                        item.getName(),
                        item.getValue()
                    });
                }
            }
        }
    }

    @Override
    public boolean isModified() {
        ApiTestConfigState state = ApiTestConfigState.getInstance(this.project);

        // 检查基础配置
        if (!this.equals(this.baseUrlField.getText(), state.baseUrl)) {
            return true;
        }

        // 检查前置请求开关
        if (this.preRequestEnabled.isSelected() != state.preRequestEnabled) {
            return true;
        }

        // 检查前置请求配置
        if (state.preRequest != null) {
            if (!this.equals(this.preRequestUrlField.getText(), state.preRequest.getUrl())) return true;
            if (!this.equals((String) this.preRequestMethodCombo.getSelectedItem(), state.preRequest.getMethod())) return true;
            if (!this.equals(this.preRequestBodyArea.getText(), state.preRequest.getBodyJson())) return true;
            if (!this.equals(this.tokenJsonPathField.getText(), state.preRequest.getTokenJsonPath())) return true;
            if (!this.equals(this.tokenHeaderNameField.getText(), state.preRequest.getTokenHeaderName())) return true;
            if (this.useBearerCheckBox.isSelected() != state.preRequest.isUseBearer()) return true;
        }

        // 简单检查请求头数量是否变化
        if (this.headersModel.getRowCount() != (state.commonHeaders != null ? state.commonHeaders.size() : 0)) {
            return true;
        }

        return false;
    }

    private boolean equals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    @Override
    public void apply() {
        ApiTestConfigState state = ApiTestConfigState.getInstance(this.project);

        // 保存基础配置
        state.baseUrl = this.baseUrlField.getText().trim();

        // 保存全局请求头
        List<ApiTestConfigState.HeaderItem> headers = new ArrayList<>();
        for (int i = 0; i < this.headersModel.getRowCount(); i++) {
            ApiTestConfigState.HeaderItem item = new ApiTestConfigState.HeaderItem();
            item.setName((String) this.headersModel.getValueAt(i, 0));
            String typeStr = (String) this.headersModel.getValueAt(i, 1);
            item.setValueType("DYNAMIC".equals(typeStr) ? ApiTestConfigState.ValueType.DYNAMIC : ApiTestConfigState.ValueType.FIXED);
            item.setValue((String) this.headersModel.getValueAt(i, 2));
            item.setSourceUrl((String) this.headersModel.getValueAt(i, 3));
            item.setSourceMethod((String) this.headersModel.getValueAt(i, 4));
            item.setSourceBody((String) this.headersModel.getValueAt(i, 5));
            headers.add(item);
        }
        state.commonHeaders = headers;

        // 保存前置请求配置
        state.preRequestEnabled = this.preRequestEnabled.isSelected();
        if (state.preRequest == null) {
            state.preRequest = new ApiTestConfigState.PreRequestConfig();
        }
        state.preRequest.setUrl(this.preRequestUrlField.getText().trim());
        state.preRequest.setMethod((String) this.preRequestMethodCombo.getSelectedItem());
        state.preRequest.setBodyJson(this.preRequestBodyArea.getText());
        state.preRequest.setTokenJsonPath(this.tokenJsonPathField.getText().trim());
        state.preRequest.setTokenHeaderName(this.tokenHeaderNameField.getText().trim());
        state.preRequest.setUseBearer(this.useBearerCheckBox.isSelected());

        List<ApiTestConfigState.HeaderItem> preHeaders = new ArrayList<>();
        for (int i = 0; i < this.preRequestHeadersModel.getRowCount(); i++) {
            ApiTestConfigState.HeaderItem item = new ApiTestConfigState.HeaderItem();
            item.setName((String) this.preRequestHeadersModel.getValueAt(i, 0));
            item.setValue((String) this.preRequestHeadersModel.getValueAt(i, 1));
            preHeaders.add(item);
        }
        state.preRequest.setHeaders(preHeaders);

        // 更新原始配置
        this.loadOriginalConfig();
    }

    @Override
    public void reset() {
        this.loadConfig();
    }
}
