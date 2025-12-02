package io.github.easy.tools.action.codegen;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import io.github.easy.tools.service.codegen.CodeGenService;
import io.github.easy.tools.ui.config.CodeGenConfigState;
import io.github.easy.tools.utils.NotificationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <p> AI代码生成触发Action </p>
 * <p>
 * 用户通过此Action触发代码生成流程，选择模板、数据源和表后，调用 CodeGenService 生成代码。
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @since 1.0.0
 */
public class GenerateCodeByAiAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        CodeGenConfigState state = CodeGenConfigState.getInstance();
        
        if (CollUtil.isEmpty(state.templates)) {
            Messages.showWarningDialog(project, "请先在设置中配置代码生成模板", "提示");
            return;
        }

        if (CollUtil.isEmpty(state.dataSources)) {
            Messages.showWarningDialog(project, "请先在设置中配置数据源", "提示");
            return;
        }

        CodeGenDialog dialog = new CodeGenDialog(project, state);
        if (dialog.showAndGet()) {
            this.executeCodeGeneration(project, dialog);
        }
    }

    /**
     * 执行代码生成
     *
     * @param project 项目
     * @param dialog  对话框
     */
    private void executeCodeGeneration(Project project, CodeGenDialog dialog) {
        CodeGenConfigState.TemplateConfig template = dialog.getSelectedTemplate();
        CodeGenConfigState.DataSourceConfig dataSource = dialog.getSelectedDataSource();
        List<String> tableNames = dialog.getSelectedTableNames();

        if (template == null || dataSource == null || CollUtil.isEmpty(tableNames)) {
            Messages.showWarningDialog(project, "请选择模板、数据源和表", "提示");
            return;
        }

        CodeGenService service = new CodeGenService();

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "AI代码生成中...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);

                List<CodeGenService.CodeGenResult> results = service.generateCode(
                        project,
                        template,
                        dataSource,
                        tableNames,
                        (current, total, message) -> {
                            indicator.setFraction((double) current / total);
                            indicator.setText(message);
                        }
                );

                int successCount = (int) results.stream().filter(CodeGenService.CodeGenResult::isSuccess).count();
                int failCount = results.size() - successCount;

                if (failCount > 0) {
                    StringBuilder errorMsg = new StringBuilder("部分代码生成失败:\n");
                    results.stream()
                            .filter(r -> !r.isSuccess())
                            .forEach(r -> errorMsg.append("- ")
                                    .append(r.getTableName())
                                    .append(": ")
                                    .append(r.getErrorMessage())
                                    .append("\n"));
                    
                    NotificationUtil.showWarning(project, errorMsg.toString());
                } else {
                    NotificationUtil.showInfo(project, "代码生成成功！共生成 " + successCount + " 个文件");
                }
            }
        });
    }

    /**
     * 代码生成对话框
     */
    private static class CodeGenDialog extends DialogWrapper {

        private final Project project;
        private final CodeGenConfigState state;

        private JComboBox<String> templateCombo;
        private JComboBox<String> dataSourceCombo;
        private JList<String> tableList;
        private JCheckBox selectAllCheckBox;

        protected CodeGenDialog(@Nullable Project project, CodeGenConfigState state) {
            super(project);
            this.project = project;
            this.state = state;
            this.setTitle("AI代码生成");
            this.init();
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout());

            this.templateCombo = new JComboBox<>();
            for (CodeGenConfigState.TemplateConfig template : this.state.templates) {
                this.templateCombo.addItem(template.getName());
            }

            this.dataSourceCombo = new JComboBox<>();
            for (CodeGenConfigState.DataSourceConfig ds : this.state.dataSources) {
                this.dataSourceCombo.addItem(ds.getName());
            }

            this.tableList = new JList<>(new DefaultListModel<>());
            this.tableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

            this.selectAllCheckBox = new JCheckBox("全选");
            this.selectAllCheckBox.addActionListener(e -> {
                if (this.selectAllCheckBox.isSelected()) {
                    this.tableList.setSelectionInterval(0, this.tableList.getModel().getSize() - 1);
                } else {
                    this.tableList.clearSelection();
                }
            });

            // 数据源切换时加载表列表
            this.dataSourceCombo.addActionListener(e -> this.loadTables());

            JPanel formPanel = FormBuilder.createFormBuilder()
                    .addLabeledComponent("选择模板:", this.templateCombo)
                    .addLabeledComponent("选择数据源:", this.dataSourceCombo)
                    .getPanel();

            JPanel tablePanel = new JPanel(new BorderLayout());
            tablePanel.setBorder(JBUI.Borders.emptyTop(10));
            tablePanel.add(this.selectAllCheckBox, BorderLayout.NORTH);
            tablePanel.add(new JBScrollPane(this.tableList), BorderLayout.CENTER);

            panel.add(formPanel, BorderLayout.NORTH);
            panel.add(tablePanel, BorderLayout.CENTER);
            panel.setPreferredSize(JBUI.size(400, 300));

            // 初始加载
            if (this.state.dataSources.size() > 0) {
                this.loadTables();
            }

            return panel;
        }

        /**
         * 加载表列表
         */
        private void loadTables() {
            CodeGenConfigState.DataSourceConfig ds = this.getSelectedDataSource();
            if (ds == null) {
                return;
            }

            DefaultListModel<String> model = (DefaultListModel<String>) this.tableList.getModel();
            model.clear();

            try {
                io.github.easy.tools.service.database.DatabaseMetadataService dbService = 
                        new io.github.easy.tools.service.database.DatabaseMetadataService();
                List<String> tables = dbService.listTables(ds);
                for (String table : tables) {
                    model.addElement(table);
                }
            } catch (Exception e) {
                Messages.showErrorDialog(this.project, "获取表列表失败: " + e.getMessage(), "错误");
            }
        }

        /**
         * 获取选中的模板
         *
         * @return 模板配置
         */
        public CodeGenConfigState.TemplateConfig getSelectedTemplate() {
            String selectedName = (String) this.templateCombo.getSelectedItem();
            if (StrUtil.isBlank(selectedName)) {
                return null;
            }
            return this.state.templates.stream()
                    .filter(t -> t.getName().equals(selectedName))
                    .findFirst()
                    .orElse(null);
        }

        /**
         * 获取选中的数据源
         *
         * @return 数据源配置
         */
        public CodeGenConfigState.DataSourceConfig getSelectedDataSource() {
            String selectedName = (String) this.dataSourceCombo.getSelectedItem();
            if (StrUtil.isBlank(selectedName)) {
                return null;
            }
            return this.state.dataSources.stream()
                    .filter(ds -> ds.getName().equals(selectedName))
                    .findFirst()
                    .orElse(null);
        }

        /**
         * 获取选中的表名列表
         *
         * @return 表名列表
         */
        public List<String> getSelectedTableNames() {
            return this.tableList.getSelectedValuesList();
        }
    }
}
