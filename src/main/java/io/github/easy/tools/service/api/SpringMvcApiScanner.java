package io.github.easy.tools.service.api;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.util.Query;
import io.github.easy.tools.entity.api.ApiInfo;
import io.github.easy.tools.service.api.annotation.AnnotationAdapter;
import io.github.easy.tools.service.api.annotation.DefaultAnnotationAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spring MVC API扫描器
 * 用于扫描项目中的@RestController和@Controller注解类，并提取API接口信息
 * 支持递归处理元注解，能够识别包装过的注解和复合注解
 * 
 * <p>该扫描器能够处理以下注解：</p>
 * <ul>
 *   <li>@RestController 和 @Controller（包括通过元注解间接标注的类）</li>
 *   <li>@RequestMapping 及其变体：@GetMapping、@PostMapping、@PutMapping、@DeleteMapping、@PatchMapping</li>
 *   <li>Swagger/OpenAPI注解：@Operation、@Tag、@ApiOperation、@Api等</li>
 * </ul>
 * 
 * <p>扫描过程包括：</p>
 * <ol>
 *   <li>扫描项目中所有带有@Controller或@RestController注解的类</li>
 *   <li>递归处理元注解，支持自定义复合注解</li>
 *   <li>提取每个Controller类中的HTTP方法映射信息</li>
 *   <li>从Swagger/OpenAPI注解中提取API名称和Controller描述</li>
 *   <li>组合完整的API路径（Controller基础路径 + 方法相对路径）</li>
 * </ol>
 * 
 * @author iamxiaohaijun
 * @version 1.0.0
 * @since 2025.11.18
 * @see ApiInfo
 * @see AnnotationAdapter
 */
public class SpringMvcApiScanner {

    private final Project project;
    private final AnnotationAdapter annotationAdapter;

    /**
     * 构造函数
     * 使用默认的注解适配器创建API扫描器
     * 
     * @param project IntelliJ项目对象，用于访问项目中的类和索引
     */
    public SpringMvcApiScanner(Project project) {
        this.project = project;
        this.annotationAdapter = new DefaultAnnotationAdapter();
    }
    
    /**
     * 构造函数
     * 使用指定的注解适配器创建API扫描器
     * 
     * @param project IntelliJ项目对象，用于访问项目中的类和索引
     * @param annotationAdapter 注解适配器，用于处理不同类型的注解
     */
    public SpringMvcApiScanner(Project project, AnnotationAdapter annotationAdapter) {
        this.project = project;
        this.annotationAdapter = annotationAdapter;
    }

    /**
     * 扫描项目中所有的API接口
     * 该方法会扫描所有带有@RestController和@Controller注解的类
     * 并提取其中的API接口信息
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>在IDE处于dumb模式（索引未完成）时会返回空列表</li>
     *   <li>使用ReadAction确保线程安全访问PSI元素</li>
     *   <li>支持递归处理元注解和复合注解</li>
     * </ul>
     *
     * @return API信息列表，包含项目中所有REST API接口的详细信息
     * @see #findControllerClasses(String)
     */
    public List<ApiInfo> scanApis() {
        // 检查是否处于dumb模式（索引未完成）
        if (DumbService.isDumb(project)) {
            return new ArrayList<>(); // 返回空列表而不是抛出异常
        }
        
        List<ApiInfo> apiInfos = new ArrayList<>();
        
        // 查找所有带有@RestController注解的类（包括通过元注解间接标注的类）
        apiInfos.addAll(findControllerClasses("org.springframework.web.bind.annotation.RestController"));
        
        // 查找所有带有@Controller注解的类（包括通过元注解间接标注的类）
        apiInfos.addAll(findControllerClasses("org.springframework.stereotype.Controller"));
        
        return apiInfos;
    }

    /**
     * 查找带有指定注解（包括元注解）的Controller类
     * 支持递归处理元注解，能够识别包装过的注解和复合注解
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>首先查找直接使用该注解的类</li>
     *   <li>然后遍历项目中所有类，检查是否通过元注解间接包含目标注解</li>
     *   <li>避免重复添加相同的类</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>在IDE处于dumb模式（索引未完成）时会返回空列表</li>
     *   <li>使用ReadAction确保线程安全访问PSI元素</li>
     * </ul>
     *
     * @param annotationFqn 注解全限定名，例如"org.springframework.web.bind.annotation.RestController"
     * @return API信息列表，包含所有匹配Controller类中的API接口信息
     * @see #extractApiFromController(PsiClass)
     * @see #hasAnnotationRecursively(PsiClass, String)
     */
    public List<ApiInfo> findControllerClasses(String annotationFqn) {
        // 检查是否处于dumb模式（索引未完成）
        if (DumbService.isDumb(project)) {
            return new ArrayList<>(); // 返回空列表而不是抛出异常
        }
        
        return ReadAction.compute(() -> {
            List<ApiInfo> apiInfos = new ArrayList<>();
            
            // 查找直接使用该注解的类
            PsiClass targetAnnotation = JavaPsiFacade.getInstance(project)
                    .findClass(annotationFqn, GlobalSearchScope.allScope(project));
            
            if (targetAnnotation != null) {
                Query<PsiClass> directClasses = AnnotatedElementsSearch.searchPsiClasses(
                        targetAnnotation, GlobalSearchScope.projectScope(project));
                
                // 使用Set跟踪已添加的类，避免重复
                Set<String> addedClasses = new HashSet<>();
                for (PsiClass psiClass : directClasses.findAll()) {
                    // 检查是否已添加过该类，避免重复
                    String classQualifiedName = psiClass.getQualifiedName();
                    if (classQualifiedName != null && !addedClasses.contains(classQualifiedName)) {
                        addedClasses.add(classQualifiedName);
                        apiInfos.addAll(extractApiFromController(psiClass));
                    }
                }
            }
            
            // 查找项目中的所有类，并检查它们是否通过元注解间接包含目标注解
            // 使用Set跟踪已添加的类，避免重复
            Set<String> addedClasses = new HashSet<>();
            // 先添加已找到的类
            for (ApiInfo info : apiInfos) {
                if (info.getClassName() != null) {
                    addedClasses.add(info.getClassName());
                }
            }
            
            for (PsiClass psiClass : getAllProjectClasses()) {
                if (hasAnnotationRecursively(psiClass, annotationFqn)) {
                    String classQualifiedName = psiClass.getQualifiedName();
                    // 确保不重复添加
                    if (classQualifiedName != null && !addedClasses.contains(classQualifiedName)) {
                        addedClasses.add(classQualifiedName);
                        apiInfos.addAll(extractApiFromController(psiClass));
                    }
                }
            }

            return apiInfos;
        });
    }

    /**
     * 获取项目中所有Java类
     * 遍历项目中的所有Java文件，提取其中的类定义，包括内部类
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>使用FilenameIndex获取项目中所有.java文件</li>
     *   <li>遍历每个Java文件，提取其中的类定义</li>
     *   <li>递归获取内部类</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>在IDE处于dumb模式（索引未完成）时会返回空列表</li>
     *   <li>只处理项目范围内的文件，不包括外部库</li>
     * </ul>
     *
     * @return 项目中所有Java类的列表，包括内部类
     * @see #getInnerClasses(PsiClass)
     */
    private List<PsiClass> getAllProjectClasses() {
        // 检查是否处于dumb模式（索引未完成）
        if (DumbService.isDumb(project)) {
            return new ArrayList<>(); // 返回空列表而不是抛出异常
        }
        
        List<PsiClass> classes = new ArrayList<>();
        
        // 获取项目中的所有Java文件
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> virtualFiles = com.intellij.psi.search.FilenameIndex.getAllFilesByExt(project, "java", projectScope);
        
        for (VirtualFile virtualFile : virtualFiles) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
            if (psiFile instanceof PsiJavaFile) {
                PsiJavaFile javaFile = (PsiJavaFile) psiFile;
                PsiClass[] fileClasses = javaFile.getClasses();
                for (PsiClass psiClass : fileClasses) {
                    classes.add(psiClass);
                    // 添加内部类
                    classes.addAll(getInnerClasses(psiClass));
                }
            }
        }
        
        return classes;
    }

    /**
     * 获取指定类的所有内部类（递归）
     * 递归遍历类的内部类结构，获取所有层级的内部类
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>获取类的直接内部类</li>
     *   <li>递归获取每个内部类的内部类</li>
     *   <li>将所有内部类添加到结果列表中</li>
     * </ol>
     *
     * @param psiClass 外部类
     * @return 该类及其所有内部类的列表
     * @see PsiClass#getInnerClasses()
     */
    private List<PsiClass> getInnerClasses(PsiClass psiClass) {
        List<PsiClass> innerClasses = new ArrayList<>();
        
        PsiClass[] innerClassArray = psiClass.getInnerClasses();
        for (PsiClass innerClass : innerClassArray) {
            innerClasses.add(innerClass);
            // 递归获取更深层次的内部类
            innerClasses.addAll(getInnerClasses(innerClass));
        }
        
        return innerClasses;
    }

    /**
     * 递归检查类是否具有指定注解（直接或间接通过元注解）
     * 支持递归处理元注解，能够识别包装过的注解和复合注解
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>检查类是否直接包含指定注解</li>
     *   <li>递归检查类上的所有注解，看它们是否包含目标注解作为元注解</li>
     *   <li>使用visited集合防止循环引用导致的无限递归</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>在IDE处于dumb模式（索引未完成）时会返回false</li>
     *   <li>使用ReadAction确保线程安全访问PSI元素</li>
     *   <li>通过visited集合防止循环引用</li>
     * </ul>
     *
     * @param psiClass      要检查的类
     * @param annotationFqn 注解全限定名，例如"org.springframework.web.bind.annotation.RestController"
     * @return 如果类直接或间接具有该注解则返回true，否则返回false
     * @see #hasAnnotationRecursivelyInternal(PsiClass, String, Set)
     * @see AnnotationAdapter#hasAnnotationRecursively(PsiClass, String, Set)
     */
    private boolean hasAnnotationRecursively(@NotNull PsiClass psiClass, String annotationFqn) {
        // 检查是否处于dumb模式（索引未完成）
        if (DumbService.isDumb(project)) {
            return false; // 返回false而不是抛出异常
        }
        
        return ReadAction.compute(() -> hasAnnotationRecursivelyInternal(psiClass, annotationFqn, new HashSet<>()));
    }

    /**
     * 递归检查类是否具有指定注解（直接或间接通过元注解）- 内部实现
     * 该方法是[hasAnnotationRecursively](file:///Users/haijun/Work/work/idea-tools/src/main/java/io/github/easy/tools/service/api/SpringMvcApiScanner.java#L181-L196)方法的内部实现，负责具体的递归检查逻辑
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>防止循环引用：检查当前类是否已访问过</li>
     *   <li>将当前类添加到已访问集合</li>
     *   <li>委托给注解适配器进行具体的注解检查</li>
     * </ol>
     *
     * @param psiClass      要检查的类
     * @param annotationFqn 注解全限定名
     * @param visited       已访问的类集合，用于防止循环引用
     * @return 如果类直接或间接具有该注解则返回true，否则返回false
     * @see AnnotationAdapter#hasAnnotationRecursively(PsiClass, String, Set)
     */
    private boolean hasAnnotationRecursivelyInternal(@NotNull PsiClass psiClass, String annotationFqn, Set<String> visited) {
        return annotationAdapter.hasAnnotationRecursively(psiClass, annotationFqn, visited);
    }

    /**
     * 从Controller类中提取API信息
     * 遍历Controller类中的所有方法，提取其中的API接口信息
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>获取Controller类上的基础路径（来自@RequestMapping注解）</li>
     *   <li>遍历类中的所有方法</li>
     *   <li>为每个方法提取API信息</li>
     *   <li>合并所有方法的API信息</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>使用ReadAction确保线程安全访问PSI元素</li>
     *   <li>支持处理多种HTTP方法注解</li>
     * </ul>
     *
     * @param controllerClass Controller类
     * @return API信息列表，包含Controller类中的所有API接口信息
     * @see #extractApisFromMethod(PsiMethod, String, PsiClass)
     * @see #getClassRequestMappingPath(PsiClass)
     */
    private List<ApiInfo> extractApiFromController(@NotNull PsiClass controllerClass) {
        return ReadAction.compute(() -> {
            List<ApiInfo> apiInfos = new ArrayList<>();
            
            // 获取类上的@RequestMapping注解基础路径
            String basePath = getClassRequestMappingPath(controllerClass);
            
            // 遍历类中的所有方法
            PsiMethod[] methods = controllerClass.getMethods();
            for (PsiMethod method : methods) {
                List<ApiInfo> methodApis = extractApisFromMethod(method, basePath, controllerClass);
                apiInfos.addAll(methodApis);
            }
            
            return apiInfos;
        });
    }

    /**
     * 获取类上的@RequestMapping注解路径
     * 该方法会递归检查类上的注解，包括元注解
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>使用ReadAction确保线程安全访问PSI元素</li>
     * </ul>
     *
     * @param psiClass 类
     * @return 基础路径
     * @see #findAnnotationRecursively(PsiClass, String)
     * @see #getAnnotationAttributeValue(PsiAnnotation, String...)
     */
    private String getClassRequestMappingPath(@NotNull PsiClass psiClass) {
        return ReadAction.compute(() -> {
            // 递归检查类上的注解，包括元注解
            PsiAnnotation requestMapping = findAnnotationRecursively(psiClass, "org.springframework.web.bind.annotation.RequestMapping");
            if (requestMapping != null) {
                return getAnnotationAttributeValue(requestMapping, "value", "path");
            }
            return "";
        });
    }

    /**
     * 递归查找注解，包括元注解
     * 该方法会递归检查类及其父类、接口，直到找到目标注解或遍历完所有可能的路径
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>在IDE处于dumb模式（索引未完成）时会返回null</li>
     *   <li>使用ReadAction确保线程安全访问PSI元素</li>
     * </ul>
     *
     * @param psiClass    类
     * @param annotationFqn 注解全限定名
     * @return 注解
     * @see #findAnnotationRecursivelyInternal(PsiClass, String, Set)
     */
    private @Nullable PsiAnnotation findAnnotationRecursively(@NotNull PsiClass psiClass, String annotationFqn) {
        // 检查是否处于dumb模式（索引未完成）
        if (DumbService.isDumb(project)) {
            return null; // 返回null而不是抛出异常
        }
        
        return ReadAction.compute(() -> findAnnotationRecursivelyInternal(psiClass, annotationFqn, new HashSet<>()));
    }

    /**
     * 递归查找注解，包括元注解
     * 该方法会递归检查类及其父类、接口，直到找到目标注解或遍历完所有可能的路径
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>使用Set记录已访问的类，防止循环引用</li>
     * </ul>
     *
     * @param psiClass    类
     * @param annotationFqn 注解全限定名
     * @param visited       已访问的类集合，用于防止循环引用
     * @return 注解
     * @see AnnotationAdapter#findAnnotationRecursively(PsiClass, String, Set)
     */
    private @Nullable PsiAnnotation findAnnotationRecursivelyInternal(@NotNull PsiClass psiClass, String annotationFqn, Set<String> visited) {
        return annotationAdapter.findAnnotationRecursively(psiClass, annotationFqn, visited);
    }

    /**
     * 从方法中提取API信息
     * 该方法会检查方法上的各种HTTP方法注解，并提取其中的API接口信息
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>使用ReadAction确保线程安全访问PSI元素</li>
     *   <li>支持处理多种HTTP方法注解</li>
     * </ul>
     *
     * @param method         方法
     * @param basePath       基础路径
     * @param controllerClass Controller类
     * @return API信息列表，包含方法中的所有API接口信息
     * @see #checkHttpMethodAnnotation(PsiMethod, String, PsiClass, String, String, List)
     * @see #getAnnotationAttributeValue(PsiAnnotation, String...)
     */
    private List<ApiInfo> extractApisFromMethod(@NotNull PsiMethod method, String basePath, @NotNull PsiClass controllerClass) {
        return ReadAction.compute(() -> {
            List<ApiInfo> apiInfos = new ArrayList<>();
            
            // 检查各种HTTP方法注解
            checkHttpMethodAnnotation(method, basePath, controllerClass, "org.springframework.web.bind.annotation.GetMapping", "GET", apiInfos);
            checkHttpMethodAnnotation(method, basePath, controllerClass, "org.springframework.web.bind.annotation.PostMapping", "POST", apiInfos);
            checkHttpMethodAnnotation(method, basePath, controllerClass, "org.springframework.web.bind.annotation.PutMapping", "PUT", apiInfos);
            checkHttpMethodAnnotation(method, basePath, controllerClass, "org.springframework.web.bind.annotation.DeleteMapping", "DELETE", apiInfos);
            checkHttpMethodAnnotation(method, basePath, controllerClass, "org.springframework.web.bind.annotation.PatchMapping", "PATCH", apiInfos);
            
            // 检查@RequestMapping
            PsiAnnotation requestMapping = method.getAnnotation("org.springframework.web.bind.annotation.RequestMapping");
            if (requestMapping != null) {
                String[] methods = getRequestMethodFromRequestMapping(requestMapping);
                String path = getAnnotationAttributeValue(requestMapping, "value", "path");
                
                for (String httpMethod : methods) {
                    ApiInfo apiInfo = createApiInfo(method, basePath, path, httpMethod, controllerClass);
                    if (apiInfo != null) {
                        apiInfos.add(apiInfo);
                    }
                }
            }
            
            return apiInfos;
        });
    }

    /**
     * 检查HTTP方法注解
     * 该方法会检查方法上的指定HTTP方法注解，并提取其中的API接口信息
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>使用ReadAction确保线程安全访问PSI元素</li>
     * </ul>
     *
     * @param method         方法
     * @param basePath       基础路径
     * @param controllerClass Controller类
     * @param annotationFqn  注解全限定名
     * @param httpMethod     HTTP方法
     * @param apiInfos       API信息列表
     * @see #createApiInfo(PsiMethod, String, String, String, PsiClass)
     * @see #getAnnotationAttributeValue(PsiAnnotation, String...)
     */
    private void checkHttpMethodAnnotation(@NotNull PsiMethod method, String basePath, @NotNull PsiClass controllerClass,
                                         String annotationFqn, String httpMethod, List<ApiInfo> apiInfos) {
        ReadAction.run(() -> {
            PsiAnnotation annotation = method.getAnnotation(annotationFqn);
            if (annotation != null) {
                String path = getAnnotationAttributeValue(annotation, "value", "path");
                ApiInfo apiInfo = createApiInfo(method, basePath, path, httpMethod, controllerClass);
                if (apiInfo != null) {
                    apiInfos.add(apiInfo);
                }
            }
        });
    }

    /**
     * 创建API信息
     * 该方法会创建一个API信息对象，并填充其中的详细信息
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>使用ReadAction确保线程安全访问PSI元素</li>
     *   <li>支持从Swagger/OpenAPI注解中提取API名称</li>
     * </ul>
     *
     * @param method         方法
     * @param basePath       基础路径
     * @param path           路径
     * @param httpMethod     HTTP方法
     * @param controllerClass Controller类
     * @return API信息
     * @see #getSwaggerApiName(PsiMethod, PsiClass)
     * @see #getControllerDescription(PsiClass)
     * @see #combinePaths(String, String)
     */
    private @Nullable ApiInfo createApiInfo(@NotNull PsiMethod method, String basePath, String path, String httpMethod, @NotNull PsiClass controllerClass) {
        return ReadAction.compute(() -> {
            ApiInfo apiInfo = new ApiInfo();
            
            // 尝试从Swagger/OpenAPI注解获取API名称
            String apiName = getSwaggerApiName(method, controllerClass);
            if (apiName == null || apiName.isEmpty()) {
                apiName = method.getName(); // 默认使用方法名作为API名称
            }
            
            apiInfo.setName(apiName);
            apiInfo.setMethod(httpMethod);
            apiInfo.setClassName(controllerClass.getQualifiedName());
            apiInfo.setControllerDescription(getControllerDescription(controllerClass));
            apiInfo.setMethodName(method.getName());
            
            // 保存方法所在的虚拟文件路径和偏移量用于跳转
            if (method.getContainingFile() != null && method.getContainingFile().getVirtualFile() != null) {
                apiInfo.setVirtualFilePath(method.getContainingFile().getVirtualFile().getPath());
                apiInfo.setMethodOffset(method.getTextOffset());
            }
            
            // 组合完整路径
            String fullPath = combinePaths(basePath, path);
            apiInfo.setUrl(fullPath);
            
            return apiInfo;
        });
    }

    /**
     * 从Swagger/OpenAPI注解中获取API名称
     * 该方法会检查方法上的Swagger/OpenAPI注解，并提取其中的API名称
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>使用ReadAction确保线程安全访问PSI元素</li>
     * </ul>
     *
     * @param method         方法
     * @param controllerClass Controller类
     * @return API名称
     * @see AnnotationAdapter#getApiNameFromMethod(PsiMethod, PsiClass)
     */
    private String getSwaggerApiName(@NotNull PsiMethod method, @NotNull PsiClass controllerClass) {
        return annotationAdapter.getApiNameFromMethod(method, controllerClass);
    }

    /**
     * 从Swagger/OpenAPI注解中获取Controller描述
     * 该方法会检查Controller类上的Swagger/OpenAPI注解，并提取其中的Controller描述
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>使用ReadAction确保线程安全访问PSI元素</li>
     * </ul>
     *
     * @param controllerClass Controller类
     * @return Controller描述
     * @see AnnotationAdapter#getControllerDescription(PsiClass)
     */
    private String getControllerDescription(@NotNull PsiClass controllerClass) {
        return annotationAdapter.getControllerDescription(controllerClass);
    }

    /**
     * 从@RequestMapping注解中获取HTTP方法
     * 该方法会检查@RequestMapping注解，并提取其中的HTTP方法
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>使用ReadAction确保线程安全访问PSI元素</li>
     * </ul>
     *
     * @param requestMapping RequestMapping注解
     * @return HTTP方法数组
     * @see AnnotationAdapter#getRequestMethodFromRequestMapping(PsiAnnotation)
     */
    private String[] getRequestMethodFromRequestMapping(@NotNull PsiAnnotation requestMapping) {
        return annotationAdapter.getRequestMethodFromRequestMapping(requestMapping);
    }

    /**
     * 获取注解属性值
     * 该方法会检查注解上的属性，并提取其中的属性值
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>使用ReadAction确保线程安全访问PSI元素</li>
     * </ul>
     *
     * @param annotation 注解
     * @param attrNames  属性名（可以有多个备选）
     * @return 属性值
     * @see AnnotationAdapter#getAnnotationAttributeValue(PsiAnnotation, String...)
     */
    private String getAnnotationAttributeValue(@NotNull PsiAnnotation annotation, String... attrNames) {
        return annotationAdapter.getAnnotationAttributeValue(annotation, attrNames);
    }

    /**
     * 组合路径
     * 该方法会组合基础路径和相对路径，生成完整的API路径
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>确保路径格式正确</li>
     * </ul>
     *
     * @param basePath 基础路径
     * @param path     相对路径
     * @return 完整路径
     */
    private String combinePaths(String basePath, String path) {
        if (basePath == null || basePath.isEmpty()) {
            return path;
        }
        if (path == null || path.isEmpty()) {
            return basePath;
        }
        
        // 确保路径格式正确
        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        if (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        
        if (path.isEmpty()) {
            return basePath;
        }
        
        return basePath + "/" + path;
    }
}