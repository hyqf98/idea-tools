package io.github.easy.tools.ui.api;

import cn.hutool.core.util.StrUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import io.github.easy.tools.entity.api.ApiInfo;
import io.github.easy.tools.service.api.ApiTestService;
import io.github.easy.tools.ui.config.ApiTestConfigState;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API测试面板
 * <p>
 * 提供美观便捷的API测试界面，支持：
 * - 请求配置（方法、URL、请求头、请求体）
 * - 全局请求头与接口级请求头管理
 * - 请求头值支持固定值或从接口动态获取
 * - 响应结果展示
 * </p>
 *
 * @author haijun
 * @since 1.0.0
 */
public class ApiTestPanel extends JPanel {

    private final Project project;
    private final ApiTestService apiTestService;
    
    // 当前选中的API
    private ApiInfo currentApi;
    
    // 请求配置Tab组件
    private JLabel methodLabel;
    private JLabel urlLabel;
    private JBTable interfaceHeadersTable;
    private DefaultTableModel interfaceHeadersModel;
    private JTextArea bodyArea;
    private JButton executeButton;
    
    // 响应结果Tab组件
    private JLabel statusLabel;
    private JTextArea responseHeadersArea;
    private JTextArea responseBodyArea;
    
    // 全局配置Tab组件
    private JBTable globalHeadersTable;
    private DefaultTableModel globalHeadersModel;
    
    // Tab容器
    private JBTabbedPane tabbedPane;

    /**
     * 构造函数
     *
     * @param project 项目实例
     */
    public ApiTestPanel(Project project) {
        this.project = project;
        this.apiTestService = new ApiTestService();
        this.initializeUI();
    }

    /**
     * 初始化UI界面
     */
    private void initializeUI() {
        this.setLayout(new BorderLayout());
        
        // 创建Tab面板
        this.tabbedPane = new JBTabbedPane();
        
        // Tab 1: 请求配置
        JPanel requestPanel = this.createRequestPanel();
        this.tabbedPane.addTab("请求配置", requestPanel);
        
        // Tab 2: 响应结果
        JPanel responsePanel = this.createResponsePanel();
        this.tabbedPane.addTab("响应结果", responsePanel);
        
        // Tab 3: 全局配置
        JPanel globalPanel = this.createGlobalPanel();
        this.tabbedPane.addTab("全局配置", globalPanel);
        
        this.add(this.tabbedPane, BorderLayout.CENTER);
    }

    /**
     * 创建请求配置面板
     */
    private JPanel createRequestPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 顶部：API基本信息
        JPanel infoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        infoPanel.add(new JLabel("方法:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        this.methodLabel = new JLabel("-");
        this.methodLabel.setFont(this.methodLabel.getFont().deriveFont(Font.BOLD));
        infoPanel.add(this.methodLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        infoPanel.add(new JLabel("URL:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        this.urlLabel = new JLabel("-");
        infoPanel.add(this.urlLabel, gbc);
        
        panel.add(infoPanel, BorderLayout.NORTH);
        
        // 中部：请求头与请求体
        JPanel contentPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        
        // 接口级请求头
        JPanel headerPanel = new JPanel(new BorderLayout(3, 3));
        JPanel headerTitlePanel = new JPanel(new BorderLayout());
        headerTitlePanel.add(new JLabel("接口级请求头 (优先级高于全局请求头)"), BorderLayout.WEST);
        JPanel headerBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        JButton addHeaderBtn = new JButton("+");
        addHeaderBtn.setToolTipText("添加请求头");
        addHeaderBtn.addActionListener(e -> this.addInterfaceHeader());
        JButton removeHeaderBtn = new JButton("-");
        removeHeaderBtn.setToolTipText("删除选中请求头");
        removeHeaderBtn.addActionListener(e -> this.removeInterfaceHeader());
        headerBtnPanel.add(addHeaderBtn);
        headerBtnPanel.add(removeHeaderBtn);
        headerTitlePanel.add(headerBtnPanel, BorderLayout.EAST);
        headerPanel.add(headerTitlePanel, BorderLayout.NORTH);
        
        String[] headerColumns = {"名称", "值类型", "值/表达式", "来源URL", "来源方法", "来源Body"};
        this.interfaceHeadersModel = new DefaultTableModel(headerColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };
        this.interfaceHeadersTable = new JBTable(this.interfaceHeadersModel);
        this.interfaceHeadersTable.setRowHeight(25);
        // 设置值类型列为下拉框
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"FIXED", "DYNAMIC"});
        this.interfaceHeadersTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(typeCombo));
        headerPanel.add(new JBScrollPane(this.interfaceHeadersTable), BorderLayout.CENTER);
        
        contentPanel.add(headerPanel);
        
        // 请求体
        JPanel bodyPanel = new JPanel(new BorderLayout(3, 3));
        bodyPanel.add(new JLabel("请求体 (JSON):"), BorderLayout.NORTH);
        this.bodyArea = new JTextArea(8, 40);
        bodyPanel.add(new JBScrollPane(this.bodyArea), BorderLayout.CENTER);
        
        contentPanel.add(bodyPanel);
        panel.add(contentPanel, BorderLayout.CENTER);
        
        // 底部：执行按钮
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        this.executeButton = new JButton("执行测试");
        this.executeButton.setFont(this.executeButton.getFont().deriveFont(Font.BOLD, 14f));
        this.executeButton.addActionListener(e -> this.executeTest());
        actionPanel.add(this.executeButton);
        panel.add(actionPanel, BorderLayout.SOUTH);
        
        return panel;
    }

    /**
     * 创建响应结果面板
     */
    private JPanel createResponsePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 顶部：状态信息
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(new JLabel("状态:"));
        this.statusLabel = new JLabel("-");
        this.statusLabel.setFont(this.statusLabel.getFont().deriveFont(Font.BOLD));
        statusPanel.add(this.statusLabel);
        panel.add(statusPanel, BorderLayout.NORTH);
        
        // 中部：响应头与响应体
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(120);
        
        // 响应头
        JPanel respHeaderPanel = new JPanel(new BorderLayout(3, 3));
        respHeaderPanel.add(new JLabel("响应头:"), BorderLayout.NORTH);
        this.responseHeadersArea = new JTextArea(5, 40);
        this.responseHeadersArea.setEditable(false);
        respHeaderPanel.add(new JBScrollPane(this.responseHeadersArea), BorderLayout.CENTER);
        splitPane.setTopComponent(respHeaderPanel);
        
        // 响应体
        JPanel respBodyPanel = new JPanel(new BorderLayout(3, 3));
        respBodyPanel.add(new JLabel("响应体:"), BorderLayout.NORTH);
        this.responseBodyArea = new JTextArea(15, 40);
        this.responseBodyArea.setEditable(false);
        respBodyPanel.add(new JBScrollPane(this.responseBodyArea), BorderLayout.CENTER);
        splitPane.setBottomComponent(respBodyPanel);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        return panel;
    }

    /**
     * 创建全局配置面板
     */
    private JPanel createGlobalPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 顶部：说明与基础URL配置
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        
        // 配置说明
        JLabel descLabel = new JLabel("<html><b>全局请求头配置</b><br/>这些请求头将应用于所有API测试请求（优先级低于接口级请求头）</html>");
        topPanel.add(descLabel, BorderLayout.NORTH);
        
        // 基础URL配置
        JPanel baseUrlPanel = new JPanel(new BorderLayout(5, 5));
        baseUrlPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        JPanel baseUrlLine = new JPanel(new BorderLayout(5, 0));
        baseUrlLine.add(new JLabel("服务基础地址："), BorderLayout.WEST);
        JTextField baseUrlField = new JTextField();
        ApiTestConfigState cfg = ApiTestConfigState.getInstance(this.project);
        baseUrlField.setText(cfg.baseUrl);
        baseUrlLine.add(baseUrlField, BorderLayout.CENTER);
        JButton saveBaseUrlBtn = new JButton("保存");
        saveBaseUrlBtn.addActionListener(e -> {
            ApiTestConfigState.getInstance(this.project).baseUrl = baseUrlField.getText();
            JOptionPane.showMessageDialog(this, "基础地址已保存", "提示", JOptionPane.INFORMATION_MESSAGE);
        });
        baseUrlLine.add(saveBaseUrlBtn, BorderLayout.EAST);
        baseUrlPanel.add(baseUrlLine, BorderLayout.NORTH);
        topPanel.add(baseUrlPanel, BorderLayout.CENTER);
        
        panel.add(topPanel, BorderLayout.NORTH);
        
        // 中部：全局请求头表格
        JPanel tablePanel = new JPanel(new BorderLayout(3, 3));
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 3));
        JButton addBtn = new JButton("添加");
        addBtn.addActionListener(e -> this.addGlobalHeader());
        JButton removeBtn = new JButton("删除");
        removeBtn.addActionListener(e -> this.removeGlobalHeader());
        JButton saveBtn = new JButton("保存请求头");
        saveBtn.addActionListener(e -> this.saveGlobalHeaders());
        btnPanel.add(addBtn);
        btnPanel.add(removeBtn);
        btnPanel.add(saveBtn);
        tablePanel.add(btnPanel, BorderLayout.NORTH);
        
        String[] columns = {"名称", "值类型", "值/表达式", "来源URL", "来源方法", "来源Body"};
        this.globalHeadersModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };
        this.globalHeadersTable = new JBTable(this.globalHeadersModel);
        this.globalHeadersTable.setRowHeight(25);
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"FIXED", "DYNAMIC"});
        this.globalHeadersTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(typeCombo));
        tablePanel.add(new JBScrollPane(this.globalHeadersTable), BorderLayout.CENTER);
        
        panel.add(tablePanel, BorderLayout.CENTER);
        
        // 底部：动态表达式说明
        JPanel helpPanel = new JPanel(new BorderLayout());
        helpPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(10, 0, 0, 0),
            BorderFactory.createTitledBorder("动态值表达式说明")
        ));
        
        JTextArea helpText = new JTextArea();
        helpText.setEditable(false);
        helpText.setBackground(panel.getBackground());
        helpText.setFont(helpText.getFont().deriveFont(12f));
        helpText.setText(
            "当值类型选择为 DYNAMIC 时，系统将自动从指定接口获取值。\n\n" +
            "表达式语法示例：\n" +
            "  1. ${response.data.token}  - 提取 response.data.token 字段的值\n" +
            "  2. ${response.access_token}  - 提取 response.access_token 字段的值\n" +
            "  3. data.user.id  - 也可省略 ${} 包裹，直接使用JSON路径\n" +
            "  4. token  - 单层级字段\n\n" +
            "配置示例：\n" +
            "  名称: Authorization\n" +
            "  值类型: DYNAMIC\n" +
            "  值/表达式: ${response.data.token}\n" +
            "  来源URL: /api/login\n" +
            "  来源方法: POST\n" +
            "  来源Body: {\"username\":\"admin\",\"password\":\"123456\"}\n\n" +
            "注意：\n" +
            "  - 支持多层级JSON路径，使用点号 . 分隔\n" +
            "  - 来源URL可以是相对路径或绝对URL\n" +
            "  - 每次执行测试时，会先调用来源接口获取最新值"
        );
        helpPanel.add(new JBScrollPane(helpText), BorderLayout.CENTER);
        
        panel.add(helpPanel, BorderLayout.SOUTH);
        
        // 加载全局配置
        this.loadGlobalHeaders();
        
        return panel;
    }

    /**
     * 更新当前选中的API
     */
    public void updateSelectedApi(ApiInfo apiInfo) {
        this.currentApi = apiInfo;
        if (apiInfo != null) {
            this.methodLabel.setText(apiInfo.getMethod());
            ApiTestConfigState cfg = ApiTestConfigState.getInstance(this.project);
            String base = cfg.baseUrl;
            String apiPath = apiInfo.getUrl();
            if (StrUtil.isBlank(base) || apiPath == null) {
                this.urlLabel.setText(apiPath);
            } else {
                String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
                String p = apiPath.startsWith("/") ? apiPath : "/" + apiPath;
                this.urlLabel.setText(b + p);
            }
        } else {
            this.methodLabel.setText("-");
            this.urlLabel.setText("-");
        }
        // 清空接口级请求头
        this.interfaceHeadersModel.setRowCount(0);
        this.bodyArea.setText("");
        // 切换到请求配置Tab
        this.tabbedPane.setSelectedIndex(0);
    }

    /**
     * 显示全局配置面板（切换到全局配置Tab）
     */
    public void showGlobalConfig() {
        this.tabbedPane.setSelectedIndex(2);
    }

    /**
     * 显示请求配置面板（切换到请求配置Tab）
     */
    public void showRequestConfig() {
        this.tabbedPane.setSelectedIndex(0);
    }

    /**
     * 添加接口级请求头
     */
    private void addInterfaceHeader() {
        this.interfaceHeadersModel.addRow(new Object[]{"", "FIXED", "", "", "GET", ""});
    }

    /**
     * 删除接口级请求头
     */
    private void removeInterfaceHeader() {
        int selectedRow = this.interfaceHeadersTable.getSelectedRow();
        if (selectedRow >= 0) {
            this.interfaceHeadersModel.removeRow(selectedRow);
        }
    }

    /**
     * 添加全局请求头
     */
    private void addGlobalHeader() {
        this.globalHeadersModel.addRow(new Object[]{"", "FIXED", "", "", "GET", ""});
    }

    /**
     * 删除全局请求头
     */
    private void removeGlobalHeader() {
        int selectedRow = this.globalHeadersTable.getSelectedRow();
        if (selectedRow >= 0) {
            this.globalHeadersModel.removeRow(selectedRow);
        }
    }

    /**
     * 加载全局请求头配置
     */
    private void loadGlobalHeaders() {
        ApiTestConfigState cfg = ApiTestConfigState.getInstance(this.project);
        this.globalHeadersModel.setRowCount(0);
        if (cfg.commonHeaders != null) {
            for (ApiTestConfigState.HeaderItem item : cfg.commonHeaders) {
                this.globalHeadersModel.addRow(new Object[]{
                    item.getName(),
                    item.getValueType() == null ? "FIXED" : item.getValueType().name(),
                    item.getValue(),
                    item.getSourceUrl(),
                    item.getSourceMethod(),
                    item.getSourceBody()
                });
            }
        }
    }

    /**
     * 保存全局请求头配置
     */
    private void saveGlobalHeaders() {
        ApiTestConfigState cfg = ApiTestConfigState.getInstance(this.project);
        List<ApiTestConfigState.HeaderItem> headers = new ArrayList<>();
        for (int i = 0; i < this.globalHeadersModel.getRowCount(); i++) {
            ApiTestConfigState.HeaderItem item = new ApiTestConfigState.HeaderItem();
            item.setName((String) this.globalHeadersModel.getValueAt(i, 0));
            String typeStr = (String) this.globalHeadersModel.getValueAt(i, 1);
            item.setValueType("DYNAMIC".equals(typeStr) ? ApiTestConfigState.ValueType.DYNAMIC : ApiTestConfigState.ValueType.FIXED);
            item.setValue((String) this.globalHeadersModel.getValueAt(i, 2));
            item.setSourceUrl((String) this.globalHeadersModel.getValueAt(i, 3));
            item.setSourceMethod((String) this.globalHeadersModel.getValueAt(i, 4));
            item.setSourceBody((String) this.globalHeadersModel.getValueAt(i, 5));
            headers.add(item);
        }
        cfg.commonHeaders = headers;
        JOptionPane.showMessageDialog(this, "全局请求头配置已保存", "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 执行API测试
     */
    private void executeTest() {
        if (this.currentApi == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个API接口", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 收集接口级请求头
        List<ApiTestConfigState.HeaderItem> interfaceHeaders = new ArrayList<>();
        for (int i = 0; i < this.interfaceHeadersModel.getRowCount(); i++) {
            ApiTestConfigState.HeaderItem item = new ApiTestConfigState.HeaderItem();
            item.setName((String) this.interfaceHeadersModel.getValueAt(i, 0));
            String typeStr = (String) this.interfaceHeadersModel.getValueAt(i, 1);
            item.setValueType("DYNAMIC".equals(typeStr) ? ApiTestConfigState.ValueType.DYNAMIC : ApiTestConfigState.ValueType.FIXED);
            item.setValue((String) this.interfaceHeadersModel.getValueAt(i, 2));
            item.setSourceUrl((String) this.interfaceHeadersModel.getValueAt(i, 3));
            item.setSourceMethod((String) this.interfaceHeadersModel.getValueAt(i, 4));
            item.setSourceBody((String) this.interfaceHeadersModel.getValueAt(i, 5));
            interfaceHeaders.add(item);
        }
        
        String bodyJson = this.bodyArea.getText();
        
        // 执行测试
        this.executeButton.setEnabled(false);
        this.statusLabel.setText("请求中...");
        this.responseHeadersArea.setText("");
        this.responseBodyArea.setText("");
        
        SwingWorker<Map<String, Object>, Void> worker = new SwingWorker<Map<String, Object>, Void>() {
            @Override
            protected Map<String, Object> doInBackground() throws Exception {
                return ApiTestPanel.this.apiTestService.executeWithHeaders(
                    ApiTestPanel.this.project,
                    ApiTestPanel.this.currentApi,
                    interfaceHeaders,
                    bodyJson
                );
            }
            
            @Override
            protected void done() {
                try {
                    Map<String, Object> result = this.get();
                    int statusCode = (int) result.get("statusCode");
                    @SuppressWarnings("unchecked")
                    Map<String, List<String>> headers = (Map<String, List<String>>) result.get("headers");
                    String body = (String) result.get("body");
                    
                    ApiTestPanel.this.statusLabel.setText("HTTP " + statusCode);
                    
                    // 显示响应头
                    StringBuilder headerText = new StringBuilder();
                    if (headers != null) {
                        headers.forEach((k, v) -> {
                            if (k != null) {
                                headerText.append(k).append(": ").append(String.join(", ", v)).append("\n");
                            }
                        });
                    }
                    ApiTestPanel.this.responseHeadersArea.setText(headerText.toString());
                    ApiTestPanel.this.responseBodyArea.setText(body);
                    
                    // 切换到响应结果Tab
                    ApiTestPanel.this.tabbedPane.setSelectedIndex(1);
                } catch (Exception e) {
                    ApiTestPanel.this.statusLabel.setText("请求失败");
                    ApiTestPanel.this.responseBodyArea.setText("错误: " + e.getMessage());
                } finally {
                    ApiTestPanel.this.executeButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}
