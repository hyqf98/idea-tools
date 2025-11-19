package io.github.easy.tools.ui.api;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import io.github.easy.tools.entity.api.ApiInfo;
import io.github.easy.tools.service.api.SpringMvcApiScanner;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API管理面板
 * 用于展示和管理项目中的API接口信息，提供树形结构视图和搜索功能
 * 
 * <p>主要功能包括：</p>
 * <ul>
 *   <li>以树形结构展示项目中的所有API接口，按Controller分组</li>
 *   <li>支持按名称、URL、方法类型和类名进行搜索过滤</li>
 *   <li>双击API节点可快速跳转到对应的Java方法源码</li>
 *   <li>支持实时刷新API列表</li>
 *   <li>为不同HTTP方法类型提供颜色区分显示</li>
 * </ul>
 * 
 * <p>UI组件说明：</p>
 * <ul>
 *   <li>工具栏：包含刷新按钮和搜索框</li>
 *   <li>API树：以树形结构展示API接口，根节点为Controller类，子节点为API方法</li>
 * </ul>
 * 
 * @author iamxiaohaijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.18 15:48
 * @since 1.0.0
 * @see ApiInfo
 * @see SpringMvcApiScanner
 */
public class ApiManagerPanel extends JPanel {
    /** 当前IntelliJ项目实例 */
    private final Project project;
    /** API树形组件，用于展示API接口的层次结构 */
    private JTree apiTree;
    /** 树模型，管理API树的数据结构 */
    private DefaultTreeModel treeModel;
    /** 刷新按钮，用于重新扫描和加载API接口 */
    private JButton refreshButton;
    /** 搜索输入框，用于过滤API接口 */
    private JTextField searchField;
    /** 所有API接口列表，包含项目中的全部API信息 */
    private List<ApiInfo> apiList;
    /** 过滤后的API接口列表，根据搜索条件筛选后的结果 */
    private List<ApiInfo> filteredApiList;

    /**
     * 自定义树节点类，用于存储API信息并支持自定义显示文本
     * 该类扩展了DefaultMutableTreeNode，添加了对API信息的存储和HTML格式化显示的支持
     * 
     * <p>显示特性：</p>
     * <ul>
     *   <li>Controller节点：使用蓝色粗体显示</li>
     *   <li>GET方法节点：使用青色显示</li>
     *   <li>POST方法节点：使用蓝色显示</li>
     *   <li>PUT方法节点：使用绿色显示</li>
     *   <li>DELETE方法节点：使用红色显示</li>
     * </ul>
     *
     * @author iamxiaohaijun
     * @version 1.0.0
     * @email "mailto:iamxiaohaijun@gmail.com"
     * @date 2025.11.18 15:48
     * @since 1.0.0
     */
    private static class ApiTreeNode extends DefaultMutableTreeNode {
        /** API信息对象，存储具体的API接口数据 */
        private final ApiInfo apiInfo;
        /** 显示文本，用于在树节点中显示的格式化文本 */
        private final String displayText;

        /**
         * 构造函数
         * 创建一个新的API树节点
         *
         * @param apiInfo     API信息对象
         * @param displayText 节点显示文本
         */
        public ApiTreeNode(ApiInfo apiInfo, String displayText) {
            this.apiInfo = apiInfo;
            this.displayText = displayText;
        }

        /**
         * 获取API信息
         * 返回该节点关联的API信息对象
         *
         * @return API信息对象
         */
        public ApiInfo getApiInfo() {
            return this.apiInfo;
        }

        /**
         * 获取节点显示文本
         * 返回格式化的HTML显示文本，根据不同类型的节点应用不同的颜色样式
         * 
         * <p>颜色规则：</p>
         * <ul>
         *   <li>Controller节点（无方法类型）：蓝色粗体</li>
         *   <li>GET方法节点：青色</li>
         *   <li>POST方法节点：蓝色</li>
         *   <li>PUT方法节点：绿色</li>
         *   <li>DELETE方法节点：红色</li>
         *   <li>其他节点：深灰色</li>
         * </ul>
         *
         * @return 格式化的HTML显示文本
         */
        @Override
        public String toString() {
            // 为API管理面板添加颜色区分
            if (this.apiInfo != null) {
                // Controller节点
                if (this.apiInfo.getMethod() == null || this.apiInfo.getMethod().isEmpty()) {
                    return "<html><span style='color: #4A90E2; font-weight: bold;'>" + this.displayText + "</span></html>";
                } 
                // API方法节点
                else {
                    String method = this.apiInfo.getMethod();
                    String color = "#FF6B6B"; // 默认红色
                    
                    switch (method.toUpperCase()) {
                        case "GET":
                            color = "#4ECDC4"; // 青色
                            break;
                        case "POST":
                            color = "#45B7D1"; // 蓝色
                            break;
                        case "PUT":
                            color = "#96CEB4"; // 绿色
                            break;
                        case "DELETE":
                            color = "#FF6B6B"; // 红色
                            break;
                    }
                    
                    return "<html><span style='color: " + color + ";'>" + this.displayText + "</span></html>";
                }
            }
            return "<html><span style='color: #333333;'>" + this.displayText + "</span></html>";
        }
    }

    /**
     * 构造函数
     * 创建API管理面板并初始化UI组件
     *
     * @param project 当前IntelliJ项目实例
     */
    public ApiManagerPanel(Project project) {
        this.project = project;
        this.apiList = new ArrayList<>();
        this.filteredApiList = new ArrayList<>();
        this.initializeUI();
    }

    /**
     * 初始化UI界面
     * 设置布局、创建工具栏、树形组件等
     *
     */
    private void initializeUI() {
        this.setLayout(new BorderLayout());

        // 创建工具栏
        JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        this.refreshButton = new JButton("刷新");
        this.refreshButton.addActionListener(new RefreshActionListener());

        JLabel searchLabel = new JLabel("搜索:");
        this.searchField = new JTextField(20);
        this.searchField.addActionListener(new SearchActionListener());

        toolbarPanel.add(this.refreshButton);
        toolbarPanel.add(Box.createHorizontalStrut(10));
        toolbarPanel.add(searchLabel);
        toolbarPanel.add(this.searchField);

        this.add(toolbarPanel, BorderLayout.NORTH);

        // 创建树模型和树
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("API接口");
        this.treeModel = new DefaultTreeModel(root);
        this.apiTree = new Tree(this.treeModel);
        this.apiTree.setRootVisible(false);
        this.apiTree.setShowsRootHandles(true);
        // 启用HTML渲染
        this.apiTree.setCellRenderer(new javax.swing.tree.DefaultTreeCellRenderer() {
            @Override
            public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                // 启用HTML渲染
                this.putClientProperty("html.disable", null);
                return this;
            }
        });

        // 添加树双击事件监听器，用于跳转到对应的Java文件
        this.apiTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = ApiManagerPanel.this.apiTree.getSelectionPath();
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        // 检查是否为自定义的API树节点
                        if (node instanceof ApiTreeNode) {
                            ApiInfo apiInfo = ((ApiTreeNode) node).getApiInfo();
                            ApiManagerPanel.this.navigateToMethod(apiInfo);
                        }
                    }
                }
            }
        });

        // 添加滚动面板
        JScrollPane scrollPane = new JScrollPane(this.apiTree);
        this.add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * 刷新API列表
     * 扫描项目中的所有API接口并更新树形结构
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>检查IDE是否处于dumb模式，如果是则等待索引完成</li>
     *   <li>清空现有数据和树结构</li>
     *   <li>使用SpringMvcApiScanner扫描@RestController和@Controller注解的类</li>
     *   <li>使用SwingWorker在后台线程执行扫描，避免阻塞UI</li>
     *   <li>逐步更新UI显示扫描结果</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>使用SwingWorker确保后台扫描不会阻塞UI线程</li>
     *   <li>通过DumbService检查避免在索引未完成时访问索引数据</li>
     *   <li>避免重复添加相同的API接口</li>
     * </ul>
     *
     * @see SpringMvcApiScanner
     * @see SwingWorker
     * @see DumbService
     */
    public void refreshApiList() {
        // 检查是否处于dumb模式（索引未完成）
        if (DumbService.isDumb(this.project)) {
            // 如果处于dumb模式，等待索引完成后再刷新
            DumbService.getInstance(this.project).runWhenSmart(this::refreshApiList);
            return;
        }
        
        // 清空现有数据
        this.apiList.clear();
        this.filteredApiList.clear();

        // 清空树结构
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) this.treeModel.getRoot();
        root.removeAllChildren();
        this.treeModel.reload();

        // 创建新的扫描器
        SpringMvcApiScanner scanner = new SpringMvcApiScanner(this.project);

        // 使用SwingWorker进行后台扫描，逐步更新UI
        SwingWorker<Void, ApiInfo> worker = new SwingWorker<Void, ApiInfo>() {
            @Override
            protected Void doInBackground() throws Exception {
                // 扫描@RestController注解的类
                List<ApiInfo> restControllerApis = scanner.findControllerClasses("org.springframework.web.bind.annotation.RestController");
                for (ApiInfo apiInfo : restControllerApis) {
                    this.publish(apiInfo);
                }

                // 扫描@Controller注解的类
                List<ApiInfo> controllerApis = scanner.findControllerClasses("org.springframework.stereotype.Controller");
                for (ApiInfo apiInfo : controllerApis) {
                    this.publish(apiInfo);
                }

                return null;
            }

            @Override
            protected void process(List<ApiInfo> chunks) {
                // 在EDT线程中更新UI
                for (ApiInfo apiInfo : chunks) {
                    // 避免重复添加
                    boolean alreadyExists = false;
                    for (ApiInfo existing : ApiManagerPanel.this.apiList) {
                        if (existing.getClassName() != null && existing.getClassName().equals(apiInfo.getClassName()) &&
                            existing.getMethodName() != null && existing.getMethodName().equals(apiInfo.getMethodName())) {
                            alreadyExists = true;
                            break;
                        }
                    }

                    if (!alreadyExists) {
                        ApiManagerPanel.this.apiList.add(apiInfo);
                        // 如果当前没有过滤条件，则添加到过滤列表中
                        if (ApiManagerPanel.this.searchField.getText().trim().isEmpty()) {
                            ApiManagerPanel.this.filteredApiList.add(apiInfo);
                        } else {
                            // 根据搜索条件过滤
                            ApiManagerPanel.this.filterApiList(ApiManagerPanel.this.searchField.getText());
                        }
                    }
                }

                // 更新树结构
                ApiManagerPanel.this.updateTreeStructure();
            }

            @Override
            protected void done() {
                // 扫描完成后的处理
                try {
                    this.get(); // 检查是否有异常
                    // 最后一次更新树结构
                    ApiManagerPanel.this.updateTreeStructure();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    /**
     * 更新树形结构
     * 根据当前的API列表构建树形结构，按Controller对API接口进行分组显示
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>清空现有树结构</li>
     *   <li>按Controller描述对API进行分组</li>
     *   <li>为每个Controller创建树节点</li>
     *   <li>为每个API接口创建子节点</li>
     *   <li>重新加载树模型</li>
     * </ol>
     * 
     * <p>分组规则：</p>
     * <ul>
     *   <li>优先使用Controller描述（来自Swagger/OpenAPI注解）</li>
     *   <li>如果没有描述则使用类名</li>
     *   <li>如果都不存在则使用"未知Controller"作为默认值</li>
     * </ul>
     *
     * @see ApiTreeNode
     */
    private void updateTreeStructure() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) this.treeModel.getRoot();
        root.removeAllChildren();

        // 按Controller分组
        Map<String, List<ApiInfo>> groupedApis = new HashMap<>();
        for (ApiInfo apiInfo : this.filteredApiList) {
            String controllerDesc = apiInfo.getControllerDescription();
            if (controllerDesc == null || controllerDesc.isEmpty()) {
                controllerDesc = apiInfo.getClassName();
                if (controllerDesc == null) {
                    controllerDesc = "未知Controller";
                }
            }
            groupedApis.computeIfAbsent(controllerDesc, k -> new ArrayList<>()).add(apiInfo);
        }

        // 为每个Controller创建节点
        for (Map.Entry<String, List<ApiInfo>> entry : groupedApis.entrySet()) {
            String controllerDesc = entry.getKey();
            List<ApiInfo> apis = entry.getValue();

            // 创建Controller节点
            DefaultMutableTreeNode controllerNode = new DefaultMutableTreeNode(controllerDesc);
            root.add(controllerNode);

            // 为每个API创建子节点
            for (ApiInfo apiInfo : apis) {
                // 创建API显示文本
                String apiText = String.format("%s %s [%s]", apiInfo.getMethod(), apiInfo.getUrl(), apiInfo.getName());
                // 创建一个自定义节点，将ApiInfo存储在节点中，同时支持自定义显示文本
                ApiTreeNode apiNode = new ApiTreeNode(apiInfo, apiText);
                controllerNode.add(apiNode);
            }
        }

        this.treeModel.reload();
    }


    /**
     * 过滤API列表
     * 根据关键字过滤API列表并更新树形结构
     * 
     * <p>过滤规则：</p>
     * <ul>
     *   <li>支持按API名称过滤</li>
     *   <li>支持按API URL过滤</li>
     *   <li>支持按HTTP方法类型过滤</li>
     *   <li>支持按Controller描述过滤</li>
     *   <li>支持按类名过滤</li>
     * </ul>
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>如果关键字为空，则显示所有API</li>
     *   <li>否则根据关键字进行不区分大小写的匹配</li>
     *   <li>更新过滤后的API列表</li>
     *   <li>调用[updateTreeStructure()](file:///Users/haijun/Work/work/idea-tools/src/main/java/io/github/easy/tools/ui/api/ApiManagerPanel.java#L306-L342)更新树形结构</li>
     * </ol>
     *
     * @param keyword 过滤关键字
     * @see #updateTreeStructure()
     */
    private void filterApiList(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            this.filteredApiList = new ArrayList<>(this.apiList);
        } else {
            this.filteredApiList = new ArrayList<>();
            String lowerKeyword = keyword.toLowerCase();
            for (ApiInfo apiInfo : this.apiList) {
                if (apiInfo.getName().toLowerCase().contains(lowerKeyword) ||
                    apiInfo.getUrl().toLowerCase().contains(lowerKeyword) ||
                    apiInfo.getMethod().toLowerCase().contains(lowerKeyword) ||
                    (apiInfo.getControllerDescription() != null && apiInfo.getControllerDescription().toLowerCase().contains(lowerKeyword)) ||
                    (apiInfo.getClassName() != null && apiInfo.getClassName().toLowerCase().contains(lowerKeyword))) {
                    this.filteredApiList.add(apiInfo);
                }
            }
        }
        // 更新树结构
        this.updateTreeStructure();
    }

    /**
     * 跳转到指定的API方法
     * 根据API信息打开对应的Java文件并定位到方法位置
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>根据API信息中的虚拟文件路径查找VirtualFile对象</li>
     *   <li>使用FileEditorManager打开文件</li>
     *   <li>使用OpenFileDescriptor定位到方法的具体位置</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>只有在API信息包含有效虚拟文件路径时才执行跳转</li>
     *   <li>使用FileEditorManager确保在IntelliJ IDE中正确打开文件</li>
     * </ul>
     *
     * @param apiInfo API信息，包含文件路径和方法偏移量
     * @see FileEditorManager
     * @see com.intellij.openapi.fileEditor.OpenFileDescriptor
     */
    private void navigateToMethod(ApiInfo apiInfo) {
        if (apiInfo.getVirtualFilePath() != null) {
            // 根据虚拟文件路径获取VirtualFile
            com.intellij.openapi.vfs.VirtualFile virtualFile =
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(apiInfo.getVirtualFilePath());

            if (virtualFile != null) {
                // 打开文件
                FileEditorManager.getInstance(this.project).openFile(virtualFile, true);
                // 将光标定位到方法位置
                FileEditorManager.getInstance(this.project).openTextEditor(
                    new com.intellij.openapi.fileEditor.OpenFileDescriptor(
                            this.project,
                        virtualFile,
                        apiInfo.getMethodOffset()
                    ),
                    true
                );
            }
        }
    }


    /**
     * 刷新按钮事件监听器
     * 处理刷新按钮的点击事件，触发API列表的重新扫描和显示
     * 
     * <p>功能说明：</p>
     * <ul>
     *   <li>当用户点击刷新按钮时，调用[refreshApiList()](file:///Users/haijun/Work/work/idea-tools/src/main/java/io/github/easy/tools/ui/api/ApiManagerPanel.java#L214-L282)方法重新扫描API</li>
     *   <li>确保在事件调度线程(EDT)中执行操作</li>
     * </ul>
     *
     * @author iamxiaohaijun
     * @version 1.0.0
     * @email "mailto:iamxiaohaijun@gmail.com"
     * @date 2025.11.18 15:48
     * @since 1.0.0
     * @see #refreshApiList()
     */
    private class RefreshActionListener implements ActionListener {
        /**
         * 处理按钮点击事件
         * 调用外部类的[refreshApiList()](file:///Users/haijun/Work/work/idea-tools/src/main/java/io/github/easy/tools/ui/api/ApiManagerPanel.java#L214-L282)方法重新扫描API接口
         *
         * @param e 动作事件对象
         * @since 1.0.0
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            ApiManagerPanel.this.refreshApiList();
        }
    }

    /**
     * 搜索框事件监听器
     * 处理搜索框的回车键事件，触发API列表的过滤显示
     * 
     * <p>功能说明：</p>
     * <ul>
     *   <li>当用户在搜索框中按下回车键时，根据输入的关键字过滤API列表</li>
     *   <li>调用[filterApiList()](file:///Users/haijun/Work/work/idea-tools/src/main/java/io/github/easy/tools/ui/api/ApiManagerPanel.java#L344-L369)方法执行过滤操作</li>
     *   <li>确保在事件调度线程(EDT)中执行操作</li>
     * </ul>
     *
     * @author iamxiaohaijun
     * @version 1.0.0
     * @email "mailto:iamxiaohaijun@gmail.com"
     * @date 2025.11.18 15:48
     * @since 1.0.0
     * @see #filterApiList(String)
     */
    private class SearchActionListener implements ActionListener {
        /**
         * 处理搜索框回车事件
         * 根据搜索框中的文本过滤API列表
         *
         * @param e 动作事件对象
         * @since 1.0.0
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            ApiManagerPanel.this.filterApiList(ApiManagerPanel.this.searchField.getText());
        }
    }
}
