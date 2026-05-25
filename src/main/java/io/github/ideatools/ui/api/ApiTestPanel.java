package io.github.ideatools.ui.api;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import io.github.ideatools.entity.api.ApiInfo;
import io.github.ideatools.service.api.ApiTestService;
import io.github.ideatools.ui.config.ApiTestConfigState;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * API测试面板
 * 提供API测试界面，包含请求配置和响应结果
 * 全局配置在 Settings -> Easy Config -> HTTP接口测试 中配置
 *
 * @author haijun
 * @since 2.0.0
 */
public class ApiTestPanel extends JPanel {

    private static final Color COLOR_SUCCESS = new Color(76, 175, 80);
    private static final Color COLOR_ERROR = new Color(244, 67, 54);
    private static final Color COLOR_PENDING = new Color(158, 158, 158);

    private static final Color COLOR_GET = new Color(78, 205, 196);
    private static final Color COLOR_POST = new Color(69, 183, 209);
    private static final Color COLOR_PUT = new Color(150, 206, 180);
    private static final Color COLOR_DELETE = new Color(255, 107, 107);

    private final Project project;
    private final ApiTestService apiTestService;

    private ApiInfo currentApi;

    // API信息
    private JLabel methodLabel;
    private JLabel urlLabel;
    private JLabel hintLabel;

    // 请求配置
    private JBTable interfaceHeadersTable;
    private DefaultTableModel interfaceHeadersModel;
    private JTextArea bodyArea;
    private JButton executeButton;

    // 响应结果
    private JLabel statusLabel;
    private JTextArea responseHeadersArea;
    private JTextArea responseBodyArea;

    public ApiTestPanel(Project project) {
        this.project = project;
        this.apiTestService = new ApiTestService();
        this.initializeUI();
    }

    private void initializeUI() {
        this.setLayout(new BorderLayout(0, 0));

        // 顶部：API信息 + 执行按钮
        JPanel topPanel = this.createTopPanel();
        this.add(topPanel, BorderLayout.NORTH);

        // 中部：左右分割布局
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setDividerLocation(400);
        mainSplit.setResizeWeight(0.5);
        mainSplit.setBorder(null);

        // 左侧：请求配置
        JPanel leftPanel = this.createLeftPanel();
        mainSplit.setLeftComponent(leftPanel);

        // 右侧：响应结果
        JPanel rightPanel = this.createRightPanel();
        mainSplit.setRightComponent(rightPanel);

        this.add(mainSplit, BorderLayout.CENTER);
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // 左侧：API信息
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));

        this.methodLabel = new JLabel("-");
        this.methodLabel.setFont(this.methodLabel.getFont().deriveFont(Font.BOLD, 16f));
        this.methodLabel.setForeground(Color.GRAY);
        infoPanel.add(this.methodLabel);

        this.urlLabel = new JLabel("请先在\"API列表\"中选择一个接口");
        this.urlLabel.setFont(this.urlLabel.getFont().deriveFont(Font.PLAIN, 13f));
        infoPanel.add(this.urlLabel);

        panel.add(infoPanel, BorderLayout.WEST);

        // 右侧：执行按钮
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        this.executeButton = new JButton("执行测试");
        this.executeButton.setFont(this.executeButton.getFont().deriveFont(Font.BOLD, 13f));
        this.executeButton.setPreferredSize(new Dimension(100, 32));
        this.executeButton.addActionListener(e -> this.executeTest());
        actionPanel.add(this.executeButton);
        panel.add(actionPanel, BorderLayout.EAST);

        // 底部：提示
        JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        this.hintLabel = new JLabel("全局配置请前往: Settings → Easy Config → HTTP接口测试");
        this.hintLabel.setFont(this.hintLabel.getFont().deriveFont(Font.PLAIN, 11f));
        this.hintLabel.setForeground(Color.GRAY);
        hintPanel.add(this.hintLabel);
        panel.add(hintPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 5));

        // 上部：请求头
        JPanel headerPanel = new JPanel(new BorderLayout(3, 3));
        TitledBorder headerBorder = BorderFactory.createTitledBorder("请求头 (接口级，覆盖全局配置)");
        headerBorder.setTitleFont(headerBorder.getTitleFont().deriveFont(Font.PLAIN, 12f));
        headerPanel.setBorder(headerBorder);

        JPanel headerBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 2));
        JButton addBtn = new JButton("+");
        addBtn.setMargin(new Insets(2, 8, 2, 8));
        addBtn.addActionListener(e -> this.addInterfaceHeader());
        JButton removeBtn = new JButton("-");
        removeBtn.setMargin(new Insets(2, 8, 2, 8));
        removeBtn.addActionListener(e -> this.removeInterfaceHeader());
        headerBtnPanel.add(addBtn);
        headerBtnPanel.add(removeBtn);
        headerPanel.add(headerBtnPanel, BorderLayout.NORTH);

        String[] headerColumns = {"名称", "类型", "值/表达式"};
        this.interfaceHeadersModel = new DefaultTableModel(headerColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };
        this.interfaceHeadersTable = new JBTable(this.interfaceHeadersModel);
        this.interfaceHeadersTable.setRowHeight(24);
        this.interfaceHeadersTable.getTableHeader().setReorderingAllowed(false);
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"FIXED", "DYNAMIC"});
        this.interfaceHeadersTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(typeCombo));
        headerPanel.add(new JBScrollPane(this.interfaceHeadersTable), BorderLayout.CENTER);

        panel.add(headerPanel, BorderLayout.CENTER);

        // 下部：请求体
        JPanel bodyPanel = new JPanel(new BorderLayout(3, 3));
        TitledBorder bodyBorder = BorderFactory.createTitledBorder("请求体 (JSON)");
        bodyBorder.setTitleFont(bodyBorder.getTitleFont().deriveFont(Font.PLAIN, 12f));
        bodyPanel.setBorder(bodyBorder);
        bodyPanel.setPreferredSize(new Dimension(-1, 150));

        this.bodyArea = new JTextArea();
        this.bodyArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        bodyPanel.add(new JBScrollPane(this.bodyArea), BorderLayout.CENTER);

        panel.add(bodyPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(3, 3));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 5, 10, 10));

        // 响应结果
        JPanel responsePanel = new JPanel(new BorderLayout(3, 3));
        TitledBorder responseBorder = BorderFactory.createTitledBorder("响应结果");
        responseBorder.setTitleFont(responseBorder.getTitleFont().deriveFont(Font.PLAIN, 12f));
        responsePanel.setBorder(responseBorder);

        // 状态行
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        statusPanel.add(new JLabel("状态:"));
        this.statusLabel = new JLabel("等待请求");
        this.statusLabel.setFont(this.statusLabel.getFont().deriveFont(Font.BOLD, 12f));
        this.statusLabel.setForeground(COLOR_PENDING);
        statusPanel.add(this.statusLabel);
        responsePanel.add(statusPanel, BorderLayout.NORTH);

        // 响应内容
        JSplitPane responseSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        responseSplit.setDividerLocation(100);
        responseSplit.setResizeWeight(0.25);
        responseSplit.setBorder(null);

        // 响应头
        JPanel respHeaderPanel = new JPanel(new BorderLayout(3, 3));
        respHeaderPanel.setBorder(BorderFactory.createTitledBorder("响应头"));
        this.responseHeadersArea = new JTextArea();
        this.responseHeadersArea.setEditable(false);
        this.responseHeadersArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        this.responseHeadersArea.setBackground(panel.getBackground());
        respHeaderPanel.add(new JBScrollPane(this.responseHeadersArea), BorderLayout.CENTER);
        responseSplit.setTopComponent(respHeaderPanel);

        // 响应体
        JPanel respBodyPanel = new JPanel(new BorderLayout(3, 3));
        respBodyPanel.setBorder(BorderFactory.createTitledBorder("响应体"));
        this.responseBodyArea = new JTextArea();
        this.responseBodyArea.setEditable(false);
        this.responseBodyArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        respBodyPanel.add(new JBScrollPane(this.responseBodyArea), BorderLayout.CENTER);
        responseSplit.setBottomComponent(respBodyPanel);

        responsePanel.add(responseSplit, BorderLayout.CENTER);

        panel.add(responsePanel, BorderLayout.CENTER);

        return panel;
    }

    public void updateSelectedApi(ApiInfo apiInfo) {
        this.currentApi = apiInfo;
        if (apiInfo != null) {
            this.methodLabel.setText(apiInfo.getMethod());
            this.updateUrlDisplay();

            // 设置方法颜色
            String method = apiInfo.getMethod();
            if ("GET".equalsIgnoreCase(method)) {
                this.methodLabel.setForeground(COLOR_GET);
            } else if ("POST".equalsIgnoreCase(method)) {
                this.methodLabel.setForeground(COLOR_POST);
            } else if ("PUT".equalsIgnoreCase(method)) {
                this.methodLabel.setForeground(COLOR_PUT);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                this.methodLabel.setForeground(COLOR_DELETE);
            } else {
                this.methodLabel.setForeground(Color.GRAY);
            }
        } else {
            this.methodLabel.setText("-");
            this.urlLabel.setText("请先在\"API列表\"中选择一个接口");
            this.methodLabel.setForeground(Color.GRAY);
        }

        // 清空接口级请求头和请求体
        this.interfaceHeadersModel.setRowCount(0);
        this.bodyArea.setText("");
    }

    private void updateUrlDisplay() {
        if (this.currentApi == null) {
            return;
        }
        ApiTestConfigState cfg = ApiTestConfigState.getInstance(this.project);
        String base = cfg.baseUrl;
        String apiPath = this.currentApi.getUrl();
        if (StringUtil.isEmpty(base) || apiPath == null) {
            this.urlLabel.setText(apiPath != null ? apiPath : "-");
        } else {
            String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            String p = apiPath.startsWith("/") ? apiPath : "/" + apiPath;
            this.urlLabel.setText(b + p);
        }
    }

    private void addInterfaceHeader() {
        this.interfaceHeadersModel.addRow(new Object[]{"", "FIXED", ""});
    }

    private void removeInterfaceHeader() {
        int selectedRow = this.interfaceHeadersTable.getSelectedRow();
        if (selectedRow >= 0) {
            this.interfaceHeadersModel.removeRow(selectedRow);
        }
    }

    private void executeTest() {
        if (this.currentApi == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个API接口", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 检查baseUrl是否配置
        ApiTestConfigState cfg = ApiTestConfigState.getInstance(this.project);
        if (StringUtil.isEmpty(cfg.baseUrl)) {
            int result = JOptionPane.showConfirmDialog(this,
                "未配置服务基础地址(BaseUrl)，请先在 Settings → Easy Config → HTTP接口测试 中配置。\n\n是否现在去配置？",
                "提示", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                // 打开设置
                com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                    .showSettingsDialog(this.project, io.github.ideatools.ui.config.ApiTestConfig.class);
            }
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
            interfaceHeaders.add(item);
        }

        String bodyJson = this.bodyArea.getText();

        // 更新UI状态
        this.executeButton.setEnabled(false);
        this.statusLabel.setText("请求中...");
        this.statusLabel.setForeground(COLOR_PENDING);
        this.responseHeadersArea.setText("");
        this.responseBodyArea.setText("");

        SwingWorker<Map<String, Object>, Void> worker = new SwingWorker<>() {
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
                    ApiTestPanel.this.statusLabel.setForeground(
                        statusCode >= 200 && statusCode < 300 ? COLOR_SUCCESS : COLOR_ERROR
                    );

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
                } catch (Exception e) {
                    ApiTestPanel.this.statusLabel.setText("请求失败");
                    ApiTestPanel.this.statusLabel.setForeground(COLOR_ERROR);
                    ApiTestPanel.this.responseBodyArea.setText("错误: " + e.getMessage());
                } finally {
                    ApiTestPanel.this.executeButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}
