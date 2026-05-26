package io.github.easy.tools.service.mybatis;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mapper Xml Cache Service
 * <p>
 * 改为项目级别服务，确保缓存与项目绑定，避免多项目之间的冲突。
 * </p>
 *
 * @author haijun
 * @date 2025-12-12 14:07:26
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service(Service.Level.PROJECT)
public final class MapperXmlCacheService {

    /**
     * namespace到XML文件的映射缓存 <p> key: namespace的完全限定名(如: com.example.mapper.UserMapper) value: 对应的XmlFile对象 </p>
     */
    private final Map<String, XmlFile> namespaceToXmlFileCache = new ConcurrentHashMap<>();

    /**
     * 文件路径到namespace的映射缓存（用于删除操作） <p> key: XML文件的路径 value: 对应的namespace </p>
     */
    private final Map<String, String> pathToNamespaceCache = new ConcurrentHashMap<>();

    /**
     * 关联的项目
     */
    private final Project project;

    /**
     * 缓存是否已初始化
     */
    private volatile boolean initialized = false;

    /**
     * 构造函数
     *
     * @param project 关联的项目
     * @since 1.0.0
     */
    public MapperXmlCacheService(Project project) {
        this.project = project;
    }

    /**
     * 获取服务实例
     *
     * @param project 项目
     * @return MapperXmlCacheService实例
     * @since 1.0.0
     */
    public static MapperXmlCacheService getInstance(Project project) {
        return project.getService(MapperXmlCacheService.class);
    }

    /**
     * 扫描项目中的所有Mapper XML文件并构建缓存
     *
     * @since 1.0.0
     */
    public void scanAndCacheMapperXmlFiles() {
        ApplicationManager.getApplication().runReadAction(() -> {
            try {
                // 清空旧缓存
                this.namespaceToXmlFileCache.clear();
                this.pathToNamespaceCache.clear();

                // 获取项目中所有的XML文件
                var xmlFiles = FileTypeIndex.getFiles(
                        com.intellij.ide.highlighter.XmlFileType.INSTANCE,
                        GlobalSearchScope.projectScope(this.project)
                );

                PsiManager psiManager = PsiManager.getInstance(this.project);

                // 遍历所有XML文件
                for (VirtualFile virtualFile : xmlFiles) {
                    // 检查是否被取消
                    ProgressManager.checkCanceled();

                    PsiFile psiFile = psiManager.findFile(virtualFile);
                    if (!(psiFile instanceof XmlFile xmlFile)) {
                        continue;
                    }

                    // 提取namespace并缓存
                    String namespace = this.extractNamespace(xmlFile);
                    if (StringUtil.isNotEmpty(namespace)) {
                        this.namespaceToXmlFileCache.put(namespace, xmlFile);
                        this.pathToNamespaceCache.put(virtualFile.getPath(), namespace);
                        log.debug("缓存Mapper XML: namespace={}, file={}", namespace, virtualFile.getPath());
                    }
                }

                this.initialized = true;
                log.info("MyBatis Mapper XML缓存完成，项目: {}, 共缓存{}个文件",
                        this.project.getName(), this.namespaceToXmlFileCache.size());
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Exception e) {
                log.error("扫描Mapper XML文件失败", e);
            }
        });
    }

    /**
     * 检查缓存是否已初始化
     *
     * @return true如果已初始化
     * @since 1.0.0
     */
    public boolean isInitialized() {
        return this.initialized;
    }

    /**
     * 从XML文件中提取namespace属性
     *
     * @param xmlFile XML文件
     * @return namespace值，如果不是Mapper文件或没有namespace则返回null
     * @since 1.0.0
     */
    private String extractNamespace(XmlFile xmlFile) {
        XmlTag rootTag = xmlFile.getRootTag();
        if (rootTag == null) {
            return null;
        }

        // 检查根标签是否为mapper
        if (!"mapper".equals(rootTag.getName())) {
            return null;
        }

        // 获取namespace属性
        return rootTag.getAttributeValue("namespace");
    }

    /**
     * 根据namespace获取对应的XML文件
     *
     * @param namespace Mapper接口的完全限定名
     * @return 对应的XmlFile，如果未找到则返回null
     * @since 1.0.0
     */
    public XmlFile getXmlFileByNamespace(String namespace) {
        return this.namespaceToXmlFileCache.get(namespace);
    }

    /**
     * 清空缓存
     *
     * @since 1.0.0
     */
    public void clearCache() {
        this.namespaceToXmlFileCache.clear();
        this.pathToNamespaceCache.clear();
        log.info("MyBatis Mapper XML缓存已清空");
    }

    /**
     * 添加文件到缓存
     *
     * @param namespace Mapper接口的完全限定名
     * @param xmlFile 对应的XmlFile
     * @since 1.0.0
     */
    public void addToCache(String namespace, XmlFile xmlFile) {
        if (StringUtil.isEmpty(namespace) || xmlFile == null) {
            return;
        }
        this.namespaceToXmlFileCache.put(namespace, xmlFile);
        this.pathToNamespaceCache.put(xmlFile.getVirtualFile().getPath(), namespace);
    }

    /**
     * 通过文件路径从缓存中移除
     *
     * @param filePath XML文件路径
     * @since 1.0.0
     */
    public void removeFromCacheByPath(String filePath) {
        String namespace = this.pathToNamespaceCache.remove(filePath);
        if (StringUtil.isNotEmpty(namespace)) {
            this.namespaceToXmlFileCache.remove(namespace);
        }
    }

    /**
     * 获取当前缓存的文件数量
     *
     * @return 缓存的文件数量
     * @since 1.0.0
     */
    public int getCacheSize() {
        return this.namespaceToXmlFileCache.size();
    }

    /**
     * 判断缓存是否为空
     *
     * @return true如果缓存为空
     * @since 1.0.0
     */
    public boolean isCacheEmpty() {
        return this.namespaceToXmlFileCache == null || this.namespaceToXmlFileCache.isEmpty();
    }
}
