package io.github.easy.tools.service.api;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import io.github.easy.tools.entity.api.ApiInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * API缓存服务
 * 提供项目级别的API接口信息缓存，避免重复扫描Controller类以提升性能
 * 
 * <p>功能特性：</p>
 * <ul>
 *   <li>项目级别缓存：每个项目维护独立的API缓存数据</li>
 *   <li>线程安全：使用读写锁保证并发访问安全</li>
 *   <li>缓存失效机制：支持手动清除缓存，适配项目文件变更场景</li>
 *   <li>延迟加载：仅在首次访问时加载数据</li>
 * </ul>
 * 
 * <p>使用场景：</p>
 * <ul>
 *   <li>API搜索对话框：避免每次打开对话框都重新扫描</li>
 *   <li>API管理面板：共享缓存数据提升响应速度</li>
 * </ul>
 * 
 * <p>缓存策略：</p>
 * <ul>
 *   <li>首次加载后长期有效，直到手动清除或IDE重启</li>
 *   <li>建议在文件保存、项目结构变更时清除缓存</li>
 * </ul>
 * 
 * @author iamxiaohaijun
 * @version 1.0.0
 * @since 1.0.0
 * @see ApiInfo
 * @see SpringMvcApiScanner
 */
@Service(Service.Level.PROJECT)
public final class ApiCacheService {

    /** 当前项目实例 */
    private final Project project;
    
    /** 缓存的API接口列表 */
    private volatile List<ApiInfo> cachedApis;
    
    /** 缓存是否已加载的标志 */
    private volatile boolean isLoaded = false;
    
    /** 读写锁，保证并发访问安全 */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 构造函数
     * 由IntelliJ平台自动注入项目实例
     *
     * @param project 当前项目实例
     */
    public ApiCacheService(Project project) {
        this.project = project;
    }

    /**
     * 获取缓存的API列表
     * 如果缓存为空，则自动加载API数据
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>使用读锁检查缓存是否已加载</li>
     *   <li>如果缓存为空，升级为写锁并加载数据</li>
     *   <li>使用双重检查锁定模式避免重复加载</li>
     *   <li>返回缓存数据的副本，防止外部修改</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>返回的是缓存数据的副本，外部修改不影响缓存</li>
     *   <li>使用读写锁保证线程安全</li>
     *   <li>采用双重检查锁定模式优化性能</li>
     * </ul>
     *
     * @return API接口列表的副本
     * @see #loadApis()
     */
    public List<ApiInfo> getCachedApis() {
        // 快速路径：使用读锁检查缓存
        this.lock.readLock().lock();
        try {
            if (this.isLoaded && this.cachedApis != null) {
                return new ArrayList<>(this.cachedApis);
            }
        } finally {
            this.lock.readLock().unlock();
        }

        // 慢速路径：使用写锁加载数据
        this.lock.writeLock().lock();
        try {
            // 双重检查：防止多个线程同时进入写锁区域
            if (!this.isLoaded || this.cachedApis == null) {
                this.cachedApis = this.loadApis();
                this.isLoaded = true;
            }
            return new ArrayList<>(this.cachedApis);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /**
     * 检查缓存是否已加载
     * 用于外部判断是否需要显示加载中状态
     *
     * @return 如果缓存已加载返回true，否则返回false
     */
    public boolean isLoaded() {
        this.lock.readLock().lock();
        try {
            return this.isLoaded;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * 清除缓存
     * 强制下次访问时重新加载API数据
     * 
     * <p>使用场景：</p>
     * <ul>
     *   <li>项目文件保存后，可能有新的Controller或API方法</li>
     *   <li>项目结构发生变化，需要刷新API列表</li>
     *   <li>用户手动触发刷新操作</li>
     * </ul>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>使用写锁保证线程安全</li>
     *   <li>清除后下次访问会自动重新加载</li>
     * </ul>
     */
    public void clearCache() {
        this.lock.writeLock().lock();
        try {
            this.cachedApis = null;
            this.isLoaded = false;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /**
     * 强制重新加载API数据
     * 清除现有缓存并立即重新扫描加载
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>清除现有缓存</li>
     *   <li>重新扫描并加载API数据</li>
     *   <li>更新缓存状态</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>此操作会阻塞当前线程直到加载完成</li>
     *   <li>建议在后台线程中调用</li>
     * </ul>
     *
     * @return 重新加载的API接口列表
     * @see #loadApis()
     */
    public List<ApiInfo> reloadApis() {
        this.lock.writeLock().lock();
        try {
            this.cachedApis = this.loadApis();
            this.isLoaded = true;
            return new ArrayList<>(this.cachedApis);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /**
     * 加载所有API接口数据
     * 使用SpringMvcApiScanner扫描项目中的所有Controller类
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>创建SpringMvcApiScanner实例</li>
     *   <li>扫描@RestController注解的类</li>
     *   <li>扫描@Controller注解的类</li>
     *   <li>合并并排序结果</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>此方法会执行耗时的扫描操作</li>
     *   <li>建议在后台线程中调用</li>
     *   <li>支持递归处理元注解和复合注解</li>
     * </ul>
     *
     * @return 扫描到的API接口列表
     * @see SpringMvcApiScanner
     */
    private List<ApiInfo> loadApis() {
        SpringMvcApiScanner scanner = new SpringMvcApiScanner(this.project);
        List<ApiInfo> apiInfos = new ArrayList<>();

        // 查找所有带有@RestController注解的类（包括通过元注解间接标注的类）
        apiInfos.addAll(scanner.findControllerClasses("org.springframework.web.bind.annotation.RestController"));

        // 查找所有带有@Controller注解的类（包括通过元注解间接标注的类）
        apiInfos.addAll(scanner.findControllerClasses("org.springframework.stereotype.Controller"));

        // 按名称排序
        return apiInfos.stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
    }
}
