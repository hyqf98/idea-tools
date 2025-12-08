package io.github.easy.tools.ui.api;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import io.github.easy.tools.entity.api.ApiInfo;
import io.github.easy.tools.service.api.SpringMvcApiScanner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/**
 * API搜索对话框
 * 支持通过快捷键快速搜索和跳转到API接口，提供按回车触发搜索和键盘导航功能
 * 
 * <p>主要功能：</p>
 * <ul>
 *   <li>按回车搜索：输入关键字后按回车键开始搜索，避免实时搜索的性能问题</li>
 *   <li>键盘导航：支持上下箭头键选择，回车键确认跳转</li>
 *   <li>快速跳转：双击或回车可快速跳转到对应的API方法源码</li>
 *   <li>异步加载：后台线程加载API数据，避免阻塞UI</li>
 *   <li>防抖动：输入时使用防抖动机制，避免频繁搜索</li>
 * </ul>
 * 
 * <p>UI组件说明：</p>
 * <ul>
 *   <li>搜索输入框：用于输入搜索关键字</li>
 *   <li>结果列表：显示匹配的API接口，支持键盘导航</li>
 * </ul>
 * 
 * <p>交互流程：</p>
 * <ol>
 *   <li>用户按下快捷键（Ctrl+\）打开对话框</li>
 *   <li>对话框显示搜索输入框，自动获取焦点</li>
 *   <li>用户输入搜索关键字，按回车键触发搜索</li>
 *   <li>用户使用上下箭头键选择API接口</li>
 *   <li>用户按回车键或双击结果跳转到对应源码</li>
 * </ol>
 * 
 * @author iamxiaohaijun
 * @version 1.0.0
 * @since 1.0.0
 * @see ApiInfo
 * @see SpringMvcApiScanner
 */
public class ApiSearchDialog extends DialogWrapper {
    /** 当前IntelliJ项目实例 */
    private final Project project;
    /** 所有API接口列表，包含项目中的全部API信息 */
    private List<ApiInfo> allApis;
    /** API数据是否已加载完成的标志 */
    private boolean apisLoaded = false;
    /** 搜索输入框，用于输入搜索关键字 */
    private JTextField searchField;
    /** 结果列表，显示匹配的API接口 */
    private JList<ApiInfo> resultList;
    /** 结果列表模型，管理列表数据 */
    private DefaultListModel<ApiInfo> listModel;
    /** 过滤后的API接口列表，根据搜索条件筛选后的结果 */
    private List<ApiInfo> filteredApis;
    /** 用于去重的Set，避免显示重复的API接口 */
    private Set<String> uniqueApiSignatures;
    /** 结果面板，包含结果列表和滚动条 */
    private JPanel resultPanel;
    /** 滚动面板，为结果列表提供滚动功能 */
    private JBScrollPane scrollPane;
    /** 提示标签，用于显示“未搜索到接口”等提示信息 */
    private javax.swing.JLabel emptyLabel;
    /** 当前搜索任务，用于取消之前的搜索 */
    private Future<?> searchTask;
    /** 单线程执行器，用于异步执行搜索任务 */
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    /** 上次搜索关键字，用于避免重复搜索 */
    private String lastSearchKeyword = "";
    /** 导航键是否被按下的标志，用于区分导航操作和输入操作 */
    private boolean isNavigationKeyPressed = false;
    /** 是否已执行过搜索的标志 */
    private boolean hasSearched = false;

    /**
     * 构造函数
     * 创建API搜索对话框实例
     * 
     * <p>初始化过程：</p>
     * <ol>
     *   <li>设置对话框为无边框模式</li>
     *   <li>初始化API数据相关字段</li>
     *   <li>设置对话框标题和尺寸</li>
     *   <li>调用init()方法初始化UI组件</li>
     * </ol>
     *
     * @param project 当前IntelliJ项目实例，可能为null
     */
    public ApiSearchDialog(@Nullable Project project) {
        super(project, true); // 设置为无边框模式
        this.project = project;
        // 延迟加载API数据，避免初始化时卡顿
        this.allApis = null;
        this.apisLoaded = false;
        // 初始时不显示任何API
        this.filteredApis = new ArrayList<>();
        this.uniqueApiSignatures = new HashSet<>();
        this.setTitle("API搜索");
        this.setResizable(true); // 允许调整大小
        this.setSize(500, 150); // 设置默认大小，只显示输入框
        this.init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 创建搜索输入框面板，使用OverlayLayout实现图标叠加
        JPanel searchPanel = new JPanel();
        searchPanel.setLayout(new javax.swing.OverlayLayout(searchPanel));
        
        // 创建搜索输入框
        this.searchField = new JTextField();
        this.searchField.setPreferredSize(new Dimension(480, 30));
        this.searchField.setOpaque(false); // 设置透明以便看到下层组件
        this.searchField.setBorder(javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createLineBorder(java.awt.Color.GRAY, 1),
            javax.swing.BorderFactory.createEmptyBorder(5, 10, 5, 35) // 右侧留出空间给放大镜图标
        ));
        this.searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // 如果还没有执行过搜索，则执行搜索
                    if (!ApiSearchDialog.this.hasSearched) {
                        String currentText = ApiSearchDialog.this.searchField.getText();
                        if (!currentText.equals(ApiSearchDialog.this.lastSearchKeyword)) {
                            ApiSearchDialog.this.performSearchWithDebounce(currentText);
                            ApiSearchDialog.this.hasSearched = true;
                        }
                        e.consume(); // 防止事件继续传播
                        return;
                    }
                    
                    // 回车键跳转到选中的API
                    if (ApiSearchDialog.this.resultList != null && ApiSearchDialog.this.resultList.getModel().getSize() > 0) {
                        int selectedIndex = ApiSearchDialog.this.resultList.getSelectedIndex();
                        if (selectedIndex >= 0 && selectedIndex < ApiSearchDialog.this.filteredApis.size()) {
                            ApiSearchDialog.this.navigateToApi(ApiSearchDialog.this.filteredApis.get(selectedIndex));
                            ApiSearchDialog.this.close(OK_EXIT_CODE);
                        } else if (ApiSearchDialog.this.filteredApis.size() > 0) {
                            // 如果没有选择项，跳转到第一个
                            ApiSearchDialog.this.navigateToApi(ApiSearchDialog.this.filteredApis.get(0));
                            ApiSearchDialog.this.close(OK_EXIT_CODE);
                        }
                    }
                    e.consume(); // 防止事件继续传播
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    // 上箭头键
                    if (ApiSearchDialog.this.resultList != null && ApiSearchDialog.this.resultList.getModel().getSize() > 0) {
                        int selectedIndex = ApiSearchDialog.this.resultList.getSelectedIndex();
                        if (selectedIndex > 0) {
                            ApiSearchDialog.this.resultList.setSelectedIndex(selectedIndex - 1);
                            ApiSearchDialog.this.scrollToVisible(selectedIndex - 1);
                        } else if (selectedIndex == -1 && ApiSearchDialog.this.resultList.getModel().getSize() > 0) {
                            // 如果没有选择项，选择第一个
                            ApiSearchDialog.this.resultList.setSelectedIndex(0);
                            ApiSearchDialog.this.scrollToVisible(0);
                        }
                        // 标记为导航键操作
                        ApiSearchDialog.this.isNavigationKeyPressed = true;
                    }
                    e.consume(); // 防止事件继续传播
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    // 下箭头键
                    if (ApiSearchDialog.this.resultList != null && ApiSearchDialog.this.resultList.getModel().getSize() > 0) {
                        int selectedIndex = ApiSearchDialog.this.resultList.getSelectedIndex();
                        if (selectedIndex < ApiSearchDialog.this.resultList.getModel().getSize() - 1) {
                            ApiSearchDialog.this.resultList.setSelectedIndex(selectedIndex + 1);
                            ApiSearchDialog.this.scrollToVisible(selectedIndex + 1);
                        } else if (selectedIndex == -1 && ApiSearchDialog.this.resultList.getModel().getSize() > 0) {
                            // 如果没有选择项，选择第一个
                            ApiSearchDialog.this.resultList.setSelectedIndex(0);
                            ApiSearchDialog.this.scrollToVisible(0);
                        }
                        // 标记为导航键操作
                        ApiSearchDialog.this.isNavigationKeyPressed = true;
                    }
                    e.consume(); // 防止事件继续传播
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    // ESC键关闭对话框
                    ApiSearchDialog.this.close(CANCEL_EXIT_CODE);
                    e.consume(); // 防止事件继续传播
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                // 不再在keyReleased中执行实时搜索
                // 重置导航键状态
                ApiSearchDialog.this.isNavigationKeyPressed = false;
            }
        });
        
        // 创建放大镜图标面板
        JPanel iconPanel = new JPanel(new BorderLayout());
        iconPanel.setOpaque(false);
        javax.swing.JLabel searchIconLabel = new javax.swing.JLabel("🔍");
        searchIconLabel.setForeground(java.awt.Color.GRAY);
        searchIconLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 0, 5, 10));
        iconPanel.add(searchIconLabel, BorderLayout.EAST);
        
        // 设置对齐方式以便OverlayLayout正确叠加
        this.searchField.setAlignmentX(0.0f);
        this.searchField.setAlignmentY(0.0f);
        iconPanel.setAlignmentX(0.0f);
        iconPanel.setAlignmentY(0.0f);
        
        // 添加组件到searchPanel（顺序很重要：先添加的在下层）
        searchPanel.add(iconPanel);
        searchPanel.add(this.searchField);
        searchPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        searchPanel.setPreferredSize(new Dimension(480, 30));

        // 创建结果面板（初始时不显示）
        this.resultPanel = new JPanel(new BorderLayout());
        this.resultPanel.setVisible(false);

        // 创建空提示面板
        JPanel emptyPanel = new JPanel(new BorderLayout());
        this.emptyLabel = new javax.swing.JLabel("未搜索到接口，请检查关键字是否正确", javax.swing.SwingConstants.CENTER);
        this.emptyLabel.setForeground(java.awt.Color.GRAY);
        emptyPanel.add(this.emptyLabel, BorderLayout.CENTER);
        emptyPanel.setVisible(false);

        // 创建结果列表面板
        JPanel listPanel = new JPanel(new BorderLayout());
        this.listModel = new DefaultListModel<>();
        this.resultList = new JBList<>(this.listModel);
        this.resultList.setCellRenderer(new ApiListCellRenderer());
        this.resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // 启用HTML渲染
        this.resultList.setCellRenderer(new ApiListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                // 启用HTML渲染
                if (component instanceof javax.swing.JLabel) {
                    ((javax.swing.JLabel) component).putClientProperty("html.disable", null);
                }
                return component;
            }
        });
        // 初始时不选择任何项
        this.resultList.clearSelection();
        this.resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int selectedIndex = ApiSearchDialog.this.resultList.getSelectedIndex();
                    if (selectedIndex >= 0) {
                        ApiSearchDialog.this.navigateToApi(ApiSearchDialog.this.filteredApis.get(selectedIndex));
                        ApiSearchDialog.this.close(OK_EXIT_CODE);
                    }
                }
            }
        });
        this.resultList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    int selectedIndex = ApiSearchDialog.this.resultList.getSelectedIndex();
                    if (selectedIndex >= 0) {
                        ApiSearchDialog.this.navigateToApi(ApiSearchDialog.this.filteredApis.get(selectedIndex));
                        ApiSearchDialog.this.close(OK_EXIT_CODE);
                    }
                    e.consume(); // 防止事件继续传播
                }
            }
        });

        this.scrollPane = new JBScrollPane(this.resultList);
        this.scrollPane.setPreferredSize(new Dimension(480, 100)); // 设置为5行高度
        listPanel.add(this.scrollPane, BorderLayout.CENTER);

        // 使用CardLayout来切换结果列表和空提示
        java.awt.CardLayout cardLayout = new java.awt.CardLayout();
        JPanel cardPanel = new JPanel(cardLayout);
        cardPanel.add(listPanel, "list");
        cardPanel.add(emptyPanel, "empty");
        
        this.resultPanel.add(cardPanel, BorderLayout.CENTER);
        
        // 保存cardLayout和cardPanel以便后续切换
        this.resultPanel.putClientProperty("cardLayout", cardLayout);
        this.resultPanel.putClientProperty("cardPanel", cardPanel);

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(this.resultPanel, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(500, 350)); // 设置首选大小

        return panel;
    }

    /**
     * 显示对话框
     * 重写父类方法，在显示对话框前延迟加载API数据，并在显示后自动获取输入框焦点
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>调用[loadAllApisAsync()](file:///Users/haijun/Work/work/idea-tools/src/main/java/io/github/easy/tools/ui/api/ApiSearchDialog.java#L456-L472)方法异步加载API数据</li>
     *   <li>调用父类show()方法显示对话框</li>
     *   <li>设置搜索输入框自动获取焦点</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>使用异步加载避免阻塞UI线程</li>
     *   <li>自动获取焦点提升用户体验</li>
     * </ul>
     *
     * @see #loadAllApisAsync()
     */
    @Override
    public void show() {
        // 延迟加载API数据
        this.loadAllApisAsync();
        super.show();
        // 对话框显示后自动获取焦点
        this.searchField.requestFocusInWindow();
        // 重置搜索状态
        this.hasSearched = false;
    }

    /**
     * 释放资源
     * 重写父类方法，在关闭对话框时清理占用的资源
     * 
     * <p>清理操作：</p>
     * <ul>
     *   <li>取消正在进行的搜索任务</li>
     *   <li>关闭线程池执行器</li>
     * </ul>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>确保及时释放线程池资源</li>
     *   <li>避免内存泄漏</li>
     * </ul>
     *
     * @see DialogWrapper#dispose()
     */
    @Override
    protected void dispose() {
        super.dispose();
        // 清理资源
        if (this.searchTask != null && !this.searchTask.isDone()) {
            this.searchTask.cancel(true);
        }
        this.executorService.shutdownNow();
    }

    /**
     * 创建南部面板
     * 重写父类方法，返回null表示不创建任何按钮面板
     * 实现无按钮对话框的交互模式
     * 
     * <p>设计说明：</p>
     * <ul>
     *   <li>遵循无按钮对话框设计规范</li>
     *   <li>通过回车键确认选择，ESC键关闭对话框</li>
     * </ul>
     *
     * @return null，表示不创建南部面板
     * @see #createActions()
     */
    @Override
    protected @Nullable JComponent createSouthPanel() {
        // 不创建任何按钮面板
        return null;
    }

    /**
     * 创建动作按钮
     * 重写父类方法，返回空数组表示不创建任何动作按钮
     * 实现无按钮对话框的交互模式
     * 
     * <p>设计说明：</p>
     * <ul>
     *   <li>遵循无按钮对话框设计规范</li>
     *   <li>通过回车键确认选择，ESC键关闭对话框</li>
     * </ul>
     *
     * @return 空的动作数组
     * @see #createSouthPanel()
     */
    @Override
    protected Action @NotNull [] createActions() {
        // 不创建任何动作按钮
        return new Action[0];
    }

    /**
     * 加载所有API接口
     * 扫描项目中的所有API接口并按名称排序
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>检查IDE是否处于dumb模式，如果是则返回空列表</li>
     *   <li>使用SpringMvcApiScanner扫描@RestController注解的类</li>
     *   <li>使用SpringMvcApiScanner扫描@Controller注解的类</li>
     *   <li>合并所有API接口并按名称排序</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>通过DumbService检查避免在索引未完成时访问索引数据</li>
     *   <li>支持递归处理元注解和复合注解</li>
     *   <li>按API名称不区分大小写排序</li>
     * </ul>
     *
     * @return 排序后的API接口列表
     * @see SpringMvcApiScanner
     * @see DumbService#isDumb(Project)
     */
    private List<ApiInfo> loadAllApis() {
        // 检查是否处于dumb模式（索引未完成）
        if (DumbService.isDumb(this.project)) {
            return new ArrayList<>(); // 返回空列表而不是抛出异常
        }
        
        SpringMvcApiScanner scanner = new SpringMvcApiScanner(this.project);
        List<ApiInfo> apiInfos = new ArrayList<>();

        // 查找所有带有@RestController注解的类（包括通过元注解间接标注的类）
        apiInfos.addAll(scanner.findControllerClasses("org.springframework.web.bind.annotation.RestController"));

        // 查找所有带有@Controller注解的类（包括通过元注解间接标注的类）
        apiInfos.addAll(scanner.findControllerClasses("org.springframework.stereotype.Controller"));

        // 按名称排序
        return apiInfos.stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .collect(Collectors.toList());
    }

    /**
     * 根据关键字过滤API接口
     * 根据输入的关键字过滤API接口列表，并更新结果列表显示
     * 
     * <p>过滤规则：</p>
     * <ul>
     *   <li>支持按API名称过滤</li>
     *   <li>支持按API URL过滤</li>
     *   <li>支持按HTTP方法类型过滤</li>
     *   <li>支持按Controller描述过滤</li>
     *   <li>支持按类名过滤</li>
     *   <li>支持路径参数模糊匹配，如/machiness/71可以匹配/machiness/{id}</li>
     * </ul>
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>清空当前结果列表</li>
     *   <li>如果关键字为空，则隐藏结果面板</li>
     *   <li>检查API数据是否已加载完成</li>
     *   <li>使用并行流进行不区分大小写的匹配过滤</li>
     *   <li>限制结果数量为5条以提升性能</li>
     *   <li>更新结果列表和面板显示</li>
     *   <li>自动选择第一个匹配项</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>使用并行流提升过滤性能</li>
     *   <li>限制结果数量避免UI卡顿</li>
     *   <li>动态显示/隐藏结果面板</li>
     *   <li>支持路径参数模糊匹配</li>
     * </ul>
     *
     * @param keyword 过滤关键字
     * @see #scrollToVisible(int)
     * @see #matchesPathPattern(String, String)
     */
    private void filterApis(String keyword) {
        this.listModel.clear();
        // 更新上次搜索关键词
        String previousKeyword = this.lastSearchKeyword;
        this.lastSearchKeyword = keyword;
        
        if (keyword == null || keyword.trim().isEmpty()) {
            // 如果搜索关键字为空，隐藏结果面板
            this.filteredApis = new ArrayList<>();
            this.resultPanel.setVisible(false);
        } else {
            // 检查API数据是否已加载
            if (this.allApis == null || !this.apisLoaded) {
                // API数据尚未加载完成，显示提示信息
                this.filteredApis = new ArrayList<>();
                this.resultPanel.setVisible(false);
                return;
            }

            String lowerKeyword = keyword.toLowerCase().trim();
            // 优化搜索逻辑，先进行粗略过滤再进行详细过滤
            // 清空去重集合
            this.uniqueApiSignatures.clear();
            this.filteredApis = this.allApis.parallelStream()
                    .filter(api -> {
                        // 构建API签名用于去重
                        String signature = api.getMethod() + "|" + api.getUrl() + "|" + api.getClassName() + "|" + api.getMethodName();
                        // 检查是否已存在相同签名的API
                        if (this.uniqueApiSignatures.contains(signature)) {
                            return false;
                        }
                        // 添加到去重集合
                        this.uniqueApiSignatures.add(signature);
                        
                        // 进行关键字匹配
                        return api.getName().toLowerCase().contains(lowerKeyword) ||
                               this.matchesPathPattern(api.getUrl(), lowerKeyword) ||
                               api.getMethod().toLowerCase().contains(lowerKeyword) ||
                               (api.getControllerDescription() != null &&
                                api.getControllerDescription().toLowerCase().contains(lowerKeyword)) ||
                               (api.getClassName() != null &&
                                api.getClassName().toLowerCase().contains(lowerKeyword));
                    })
                    .limit(10) // 限制结果数量为10条以提升性能
                    .collect(Collectors.toList());

            for (ApiInfo api : this.filteredApis) {
                this.listModel.addElement(api);
            }

            // 显示结果面板
            this.resultPanel.setVisible(true);
            
            // 使用CardLayout切换显示
            java.awt.CardLayout cardLayout = (java.awt.CardLayout) this.resultPanel.getClientProperty("cardLayout");
            JPanel cardPanel = (JPanel) this.resultPanel.getClientProperty("cardPanel");
            
            if (cardLayout != null && cardPanel != null) {
                if (this.filteredApis.isEmpty()) {
                    // 显示空提示
                    cardLayout.show(cardPanel, "empty");
                } else {
                    // 显示结果列表
                    cardLayout.show(cardPanel, "list");
                }
            }

            // 只有在搜索关键词改变时才选择第一个结果
            if (!this.filteredApis.isEmpty() && !keyword.equals(previousKeyword)) {
                this.resultList.setSelectedIndex(0);
                this.scrollToVisible(0);
            } else if (this.filteredApis.isEmpty()) {
                this.resultList.clearSelection();
            }
        }

        // 重新验证面板布局
        this.resultPanel.revalidate();
        this.resultPanel.repaint();
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
     * @see com.intellij.openapi.fileEditor.FileEditorManager
     * @see com.intellij.openapi.fileEditor.OpenFileDescriptor
     */
    private void navigateToApi(ApiInfo apiInfo) {
        if (apiInfo.getVirtualFilePath() != null) {
            // 根据虚拟文件路径获取VirtualFile
            com.intellij.openapi.vfs.VirtualFile virtualFile =
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(apiInfo.getVirtualFilePath());

            if (virtualFile != null) {
                // 打开文件
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(this.project).openFile(virtualFile, true);
                // 将光标定位到方法位置
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(this.project).openTextEditor(
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
     * 滚动到可见位置
     * 确保指定索引的列表项在可视区域内
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>检查索引是否有效</li>
     *   <li>调用JList的ensureIndexIsVisible方法滚动到指定位置</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>只在索引有效时执行滚动操作</li>
     *   <li>避免数组越界异常</li>
     * </ul>
     *
     * @param index 要滚动到的项的索引
     * @see JList#ensureIndexIsVisible(int)
     */
    private void scrollToVisible(int index) {
        if (index >= 0 && index < this.resultList.getModel().getSize()) {
            this.resultList.ensureIndexIsVisible(index);
        }
    }

    /**
     * 路径模糊匹配
     * 支持将具体路径值匹配到路径参数模板，例如/machiness/71可以匹配/machiness/{id}
     * 
     * <p>匹配规则：</p>
     * <ul>
     *   <li>首先尝试直接包含匹配（不区分大小写）</li>
     *   <li>然后尝试路径参数模糊匹配：将路径参数{xxx}替换为正则表达式，匹配任意非斜杠字符</li>
     *   <li>支持多个路径参数的匹配</li>
     * </ul>
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>首先进行直接包含匹配</li>
     *   <li>如果直接匹配失败，则尝试将API路径中的{xxx}替换为正则表达式\\w+</li>
     *   <li>使用正则表达式匹配输入的关键字</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>匹配时不区分大小写</li>
     *   <li>正则表达式需要转义特殊字符</li>
     * </ul>
     * 
     * <p>示例：</p>
     * <ul>
     *   <li>API路径: /machiness/{id} 可以匹配关键字: /machiness/71</li>
     *   <li>API路径: /users/{userId}/orders/{orderId} 可以匹配关键字: /users/123/orders/456</li>
     * </ul>
     *
     * @param apiUrl API路径，可能包含路径参数如{id}
     * @param keyword 搜索关键字，可能是具体的路径值
     * @return 如果匹配则返回true，否则返回false
     */
    private boolean matchesPathPattern(String apiUrl, String keyword) {
        if (apiUrl == null || keyword == null) {
            return false;
        }
        
        String lowerApiUrl = apiUrl.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        
        // 首先尝试直接包含匹配
        if (lowerApiUrl.contains(lowerKeyword)) {
            return true;
        }
        
        // 尝试路径参数模糊匹配
        // 将 {xxx} 替换为正则表达式 \\w+（匹配一个或多个字母数字或下划线）
        // 但为了更通用，使用 [^/]+ 匹配任意非斜杠字符
        String pattern = lowerApiUrl.replaceAll("\\{[^}]+\\}", "[^/]+");
        // 转义正则表达式中的特殊字符
        pattern = pattern.replace("/", "\\/");
        
        try {
            // 使用正则表达式匹配
            return lowerKeyword.matches(".*" + pattern + ".*");
        } catch (Exception e) {
            // 如果正则表达式匹配失败，返回false
            return false;
        }
    }

    /**
     * 执行带防抖动的搜索
     * 使用防抖动机制避免用户输入时频繁触发搜索操作
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>取消之前未完成的搜索任务</li>
     *   <li>创建新的搜索任务，延迟150ms执行</li>
     *   <li>在延迟期间如果有新输入，则取消当前任务</li>
     *   <li>延迟结束后在EDT线程中执行过滤操作</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>使用单线程执行器确保任务顺序执行</li>
     *   <li>在EDT线程中更新UI组件</li>
     *   <li>正确处理线程中断和任务取消</li>
     * </ul>
     *
     * @param keyword 搜索关键字
     * @see #filterApis(String)
     * @see ExecutorService
     * @see SwingUtilities#invokeLater(Runnable)
     */
    private void performSearchWithDebounce(String keyword) {
        // 取消之前的搜索任务
        if (this.searchTask != null && !this.searchTask.isDone()) {
            this.searchTask.cancel(true);
        }

        try {
            // 创建新的搜索任务
            this.searchTask = this.executorService.submit(() -> {
                try {
                    // 延迟150ms执行搜索，避免频繁触发
                    Thread.sleep(150);
                    if (!Thread.currentThread().isInterrupted()) {
                        // 在EDT线程中更新UI
                        SwingUtilities.invokeLater(() -> {
                            this.filterApis(keyword);
                        });
                    }
                } catch (InterruptedException e) {
                    // 任务被取消
                    Thread.currentThread().interrupt();
                }
            });
        } catch (RejectedExecutionException e) {
            // 线程池已关闭，忽略任务提交
            System.out.println("线程池已关闭，跳过搜索任务");
        }
    }

    /**
     * 延迟加载所有API数据
     * 在后台线程中异步加载所有API接口数据，避免阻塞UI线程
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>检查IDE是否处于dumb模式，如果是则等待索引完成</li>
     *   <li>使用线程池提交加载任务</li>
     *   <li>在后台线程中扫描所有API接口</li>
     *   <li>在EDT线程中更新数据字段和加载状态</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>通过DumbService检查避免在索引未完成时访问索引数据</li>
     *   <li>使用runWhenSmart方法确保在索引完成后执行加载操作</li>
     *   <li>在EDT线程中更新UI相关字段</li>
     * </ul>
     *
     * @see #loadAllApis()
     * @see DumbService#isDumb(Project)
     * @see DumbService#runWhenSmart(Runnable)
     */
    private void loadAllApisAsync() {
        // 检查是否处于dumb模式（索引未完成）
        if (DumbService.isDumb(this.project)) {
            // 如果处于dumb模式，等待索引完成后再加载
            DumbService.getInstance(this.project).runWhenSmart(this::loadAllApisAsync);
            return;
        }
        
        try {
            this.executorService.submit(() -> {
                try {
                    // 延迟加载API数据，避免阻塞UI线程
                    List<ApiInfo> apis = this.loadAllApis();
                    SwingUtilities.invokeLater(() -> {
                        // 直接更新allApis字段
                        this.allApis = apis;
                        this.apisLoaded = true;
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (RejectedExecutionException e) {
            // 线程池已关闭，忽略任务提交
        }
    }

    /**
     * API列表单元格渲染器
     */
    private static class ApiListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                    boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof ApiInfo) {
                ApiInfo apiInfo = (ApiInfo) value;
                String text = String.format("%s %s [%s] - %s",
                    apiInfo.getMethod(),
                    apiInfo.getUrl(),
                    apiInfo.getName(),
                    apiInfo.getControllerDescription());
                this.setText(text);
                
                // 添加颜色区分
                if (!isSelected) {
                    // 非选中状态下，为不同部分添加颜色
                    String methodName = apiInfo.getMethod();
                    String url = apiInfo.getUrl();
                    String name = apiInfo.getName();
                    String description = apiInfo.getControllerDescription();
                    
                    // 设置HTML格式和颜色
                    String coloredText = String.format("<html><span style='color: #FF6B6B;'>%s</span> " +
                        "<span style='color: #4ECDC4;'>%s</span> " +
                        "<span style='color: #45B7D1;'>[%s]</span> " +
                        "<span style='color: #96CEB4;'>- %s</span></html>",
                        methodName, url, name, description != null ? description : "");
                    this.setText(coloredText);
                }
            }
            
            // 设置HTML渲染
            this.setOpaque(true);
            
            return this;
        }
    }
}
