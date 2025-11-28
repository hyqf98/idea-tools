package io.github.easy.tools.ui.api;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import io.github.easy.tools.ui.config.ApiTestConfigState;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * <p> API 测试配置对话框 </p>
 * 支持配置服务基础地址、公共请求头、以及登录等前置请求。
 */
public class ApiTestConfigDialog extends DialogWrapper {

    private final Project project;

    private JTextField baseUrlField;

    private JCheckBox preEnabledCheck;
    private JTextField preUrlField;
    private JComboBox<String> preMethodBox;
    private JTextArea preHeadersArea;
    private JTextArea preBodyArea;
    private JTextField tokenPathField;
    private JTextField tokenHeaderNameField;
    private JCheckBox useBearerCheck;
    private JSpinner tokenExpireSpinner;

    private JTextArea commonHeadersArea;

    public ApiTestConfigDialog(@Nullable Project project) {
        super(project);
        this.project = project;
        setTitle("API测试配置");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // 基础地址
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; form.add(new JLabel("服务基础地址:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; baseUrlField = new JTextField(); form.add(baseUrlField, gbc); row++;

        // 公共头
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; form.add(new JLabel("公共请求头(每行: 名称:值):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; commonHeadersArea = new JTextArea(5, 40); form.add(new JBScrollPane(commonHeadersArea), gbc); row++;

        // 前置请求
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; form.add(new JLabel("启用前置请求(登录):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; preEnabledCheck = new JCheckBox("启用"); form.add(preEnabledCheck, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; form.add(new JLabel("登录URL:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; preUrlField = new JTextField(); form.add(preUrlField, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; form.add(new JLabel("登录方法:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; preMethodBox = new JComboBox<>(new String[]{"POST", "GET", "PUT", "DELETE"}); form.add(preMethodBox, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; form.add(new JLabel("登录请求头(每行: 名称:值):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; preHeadersArea = new JTextArea(4, 40); form.add(new JBScrollPane(preHeadersArea), gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; form.add(new JLabel("登录请求体(JSON):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; preBodyArea = new JTextArea(6, 40); form.add(new JBScrollPane(preBodyArea), gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; form.add(new JLabel("Token提取路径(点分):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; tokenPathField = new JTextField("access_token"); form.add(tokenPathField, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; form.add(new JLabel("挂载到请求头名称:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; tokenHeaderNameField = new JTextField("Authorization"); form.add(tokenHeaderNameField, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; form.add(new JLabel("使用Bearer前缀:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; useBearerCheck = new JCheckBox("Bearer "); useBearerCheck.setSelected(true); form.add(useBearerCheck, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; form.add(new JLabel("Token过期(分钟):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; tokenExpireSpinner = new JSpinner(new SpinnerNumberModel(120, 1, 1440, 1)); form.add(tokenExpireSpinner, gbc); row++;

        panel.add(form, BorderLayout.CENTER);

        // 载入配置
        loadConfig();
        return panel;
    }

    private void loadConfig() {
        ApiTestConfigState state = ApiTestConfigState.getInstance(this.project);
        baseUrlField.setText(state.baseUrl);
        preEnabledCheck.setSelected(state.preRequestEnabled);
        if (state.preRequest != null) {
            preUrlField.setText(state.preRequest.getUrl());
            preMethodBox.setSelectedItem(state.preRequest.getMethod());
            preBodyArea.setText(state.preRequest.getBodyJson());
            tokenPathField.setText(state.preRequest.getTokenJsonPath());
            tokenHeaderNameField.setText(state.preRequest.getTokenHeaderName());
            useBearerCheck.setSelected(state.preRequest.isUseBearer());
            tokenExpireSpinner.setValue(state.preRequest.getTokenExpireMinutes());
            commonHeadersArea.setText(headersToText(state.commonHeaders));
            preHeadersArea.setText(headersToText(state.preRequest.getHeaders()));
        }
    }

    @Override
    protected void doOKAction() {
        ApiTestConfigState state = ApiTestConfigState.getInstance(this.project);
        state.baseUrl = baseUrlField.getText();
        state.preRequestEnabled = preEnabledCheck.isSelected();
        if (state.preRequest == null) {
            state.preRequest = new ApiTestConfigState.PreRequestConfig();
        }
        state.preRequest.setUrl(preUrlField.getText());
        state.preRequest.setMethod((String) preMethodBox.getSelectedItem());
        state.preRequest.setBodyJson(preBodyArea.getText());
        state.preRequest.setTokenJsonPath(tokenPathField.getText());
        state.preRequest.setTokenHeaderName(tokenHeaderNameField.getText());
        state.preRequest.setUseBearer(useBearerCheck.isSelected());
        state.preRequest.setTokenExpireMinutes((Integer) tokenExpireSpinner.getValue());
        state.commonHeaders = parseHeaders(commonHeadersArea.getText());
        state.preRequest.setHeaders(parseHeaders(preHeadersArea.getText()));
        super.doOKAction();
    }

    private String headersToText(List<ApiTestConfigState.HeaderItem> headers) {
        StringBuilder sb = new StringBuilder();
        if (headers == null) return "";
        for (ApiTestConfigState.HeaderItem h : headers) {
            if (h == null) continue;
            sb.append(h.getName() == null ? "" : h.getName())
              .append(":")
              .append(h.getValue() == null ? "" : h.getValue())
              .append("\n");
        }
        return sb.toString();
    }

    private List<ApiTestConfigState.HeaderItem> parseHeaders(String text) {
        List<ApiTestConfigState.HeaderItem> list = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return list;
        String[] lines = text.split("\n");
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            int idx = l.indexOf(":");
            if (idx > 0) {
                ApiTestConfigState.HeaderItem item = new ApiTestConfigState.HeaderItem();
                item.setName(l.substring(0, idx).trim());
                item.setValue(l.substring(idx + 1).trim());
                list.add(item);
            }
        }
        return list;
    }
}
