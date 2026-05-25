package io.github.ideatools.ui.config;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import io.github.ideatools.service.database.DatabaseMetadataService;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/**
 * <p> 数据源配置对话框 </p>
 * <p>
 * 用于新增或编辑数据库连接信息，支持测试连接功能。
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @since 1.0.0
 * @date 2025-12-23 09:34:50
 */
public class DataSourceConfigDialog extends DialogWrapper {

    /**
     * data source
     */
    private final CodeGenConfigState.DataSourceConfig dataSource;
    /**
     * is new mode
     */
    private final boolean isNewMode;

    /**
     * name field
     */
    private JBTextField nameField;
    /**
     * jdbc url field
     */
    private JBTextField jdbcUrlField;
    /**
     * username field
     */
    private JBTextField usernameField;
    /**
     * password field
     */
    private JBPasswordField passwordField;
    /**
     * driver type combo
     */
    private JComboBox<String> driverTypeCombo;

    /**
     * 构造函数（新增模式）
     *
     * @since 1.0.0
     */
    public DataSourceConfigDialog() {
        this(null);
    }

    /**
     * 构造函数（编辑模式）
     *
     * @param dataSource 要编辑的数据源配置
     * @since 1.0.0
     */
    public DataSourceConfigDialog(@Nullable CodeGenConfigState.DataSourceConfig dataSource) {
        super(true);
        this.dataSource = dataSource != null ? dataSource : new CodeGenConfigState.DataSourceConfig();
        this.isNewMode = dataSource == null;
        this.setTitle(this.isNewMode ? "新增数据源" : "编辑数据源");
        this.init();
    }

    /**
     * Create Center Panel
     *
     * @return component
     * @since 1.0.0
     */
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        this.nameField = new JBTextField(this.dataSource.getName(), 30);
        this.jdbcUrlField = new JBTextField(this.dataSource.getJdbcUrl(), 40);
        this.usernameField = new JBTextField(this.dataSource.getUsername(), 30);
        this.passwordField = new JBPasswordField();
        this.passwordField.setText(this.dataSource.getPassword());

        String[] driverTypes = {"mysql", "postgresql", "sqlserver", "oracle"};
        this.driverTypeCombo = new JComboBox<>(driverTypes);
        this.driverTypeCombo.setSelectedItem(StringUtil.defaultIfEmpty(this.dataSource.getDriverType(), "mysql"));

        // 添加URL模板提示
        this.driverTypeCombo.addActionListener(e -> {
            String selected = (String) this.driverTypeCombo.getSelectedItem();
            String currentUrl = this.jdbcUrlField.getText();

            // 如果URL为空，或者当前URL是其他数据库的默认URL模板，则自动更新
            if (StringUtil.isNotEmpty(currentUrl) || this.isDefaultUrlTemplate(currentUrl)) {
                this.jdbcUrlField.setText(this.getDefaultJdbcUrl(selected));
            }
        });

        JButton testButton = new JButton("测试连接");
        testButton.addActionListener(e -> this.testConnection());

        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        testPanel.add(testButton);

        FormBuilder builder = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("数据源名称:"), this.nameField)
                .addLabeledComponent(new JBLabel("驱动类型:"), this.driverTypeCombo)
                .addLabeledComponent(new JBLabel("JDBC URL:"), this.jdbcUrlField)
                .addLabeledComponent(new JBLabel("用户名:"), this.usernameField)
                .addLabeledComponent(new JBLabel("密码:"), this.passwordField)
                .addComponent(testPanel);

        panel.add(builder.getPanel(), BorderLayout.CENTER);
        panel.setBorder(JBUI.Borders.empty(10));

        return panel;
    }

    /**
     * 测试数据库连接
     *
     * @since 1.0.0
     */
    private void testConnection() {
        this.applyFieldsToDataSource();
        DatabaseMetadataService service = new DatabaseMetadataService();
        String result = service.testConnection(this.dataSource);

        if (result.startsWith("连接成功")) {
            Messages.showInfoMessage(result, "测试连接");
        } else {
            Messages.showErrorDialog(result, "测试连接失败");
        }
    }

    /**
     * 获取默认的 JDBC URL 模板
     *
     * @param driverType 驱动类型
     * @return JDBC URL 模板
     * @since 1.0.0
     */
    private String getDefaultJdbcUrl(String driverType) {
        return switch (StringUtil.defaultIfEmpty(driverType, "mysql").toLowerCase()) {
            case "mysql" -> "jdbc:mysql://localhost:3306/database?useUnicode=true&characterEncoding=utf8&useSSL=false";
            case "postgresql" -> "jdbc:postgresql://localhost:5432/database";
            case "sqlserver" -> "jdbc:sqlserver://localhost:1433;databaseName=database";
            case "oracle" -> "jdbc:oracle:thin:@localhost:1521:orcl";
            default -> "";
        };
    }

    /**
     * 判断当前URL是否为默认模板
     * <p>
     * 判断逻辑：检查当前URL是否与任何一个数据库类型的默认URL匹配
     * </p>
     *
     * @param url 当前 JDBC URL
     * @return 是否为默认模板
     * @since 1.0.0
     */
    private boolean isDefaultUrlTemplate(String url) {
        if (StringUtil.isEmpty(url)) {
            return false;
        }

        // 检查是否与任何默认模板匹配
        return url.equals(this.getDefaultJdbcUrl("mysql")) ||
                url.equals(this.getDefaultJdbcUrl("postgresql")) ||
                url.equals(this.getDefaultJdbcUrl("sqlserver")) ||
                url.equals(this.getDefaultJdbcUrl("oracle"));
    }

    /**
     * 将输入框的值应用到数据源配置对象
     *
     * @since 1.0.0
     */
    private void applyFieldsToDataSource() {
        this.dataSource.setName(this.nameField.getText().trim());
        this.dataSource.setJdbcUrl(this.jdbcUrlField.getText().trim());
        this.dataSource.setUsername(this.usernameField.getText().trim());
        this.dataSource.setPassword(new String(this.passwordField.getPassword()));
        this.dataSource.setDriverType((String) this.driverTypeCombo.getSelectedItem());
    }

    /**
     * Do OK Action
     *
     * @since 1.0.0
     */
    @Override
    protected void doOKAction() {
        this.applyFieldsToDataSource();

        if (StringUtil.isEmpty(this.dataSource.getName())) {
            Messages.showWarningDialog("数据源名称不能为空", "验证失败");
            return;
        }

        if (StringUtil.isEmpty(this.dataSource.getJdbcUrl())) {
            Messages.showWarningDialog("JDBC URL 不能为空", "验证失败");
            return;
        }

        super.doOKAction();
    }

    /**
     * 获取配置后的数据源对象
     *
     * @return 数据源配置
     * @since 1.0.0
     */
    public CodeGenConfigState.DataSourceConfig getDataSource() {
        return this.dataSource;
    }

    /**
     * 是否为新增模式
     *
     * @return 是否新增
     * @since 1.0.0
     */
    public boolean isNewMode() {
        return this.isNewMode;
    }
}
