package io.github.easy.tools.entity.api;

import lombok.Data;

/**
 * API信息实体类
 * 用于存储和传递API接口的相关信息
 * 
 * @author iamxiaohaijun
 * @version 1.0.0
 * @since 2025.11.18
 */
@Data
public class ApiInfo {
    /**
     * API接口名称
     * 优先从Swagger/OpenAPI注解中提取，如@Operation的summary或operationId属性
     * 如果没有相关注解，则使用方法名作为默认名称
     */
    private String name;
    
    /**
     * API接口地址
     * 完整的URL路径，包含Controller级别的基础路径和方法级别的相对路径
     * 例如：/api/users/{id}，其中/api/users来自Controller的@RequestMapping，
     * {id}来自方法的@GetMapping等注解
     */
    private String url;
    
    /**
     * 接口方法类型（GET、POST、PUT、DELETE、PATCH等）
     * 从HTTP方法注解中提取，如@GetMapping、@PostMapping等
     */
    private String method;
    
    /**
     * 所属Controller类的完全限定名
     * 用于标识API接口所属的Controller类，格式为包名.类名
     * 例如：com.example.controller.UserController
     */
    private String className;
    
    /**
     * Controller描述
     * 从Swagger/OpenAPI注解中提取Controller的描述信息
     * 优先级：@Tag(name) > @Tag(description) > @Api(value) > @Api(description) > 简单类名
     */
    private String controllerDescription;
    
    /**
     * 方法名
     * Controller类中处理该API接口的方法名称
     */
    private String methodName;
    
    /**
     * 方法所在的虚拟文件路径
     * 用于在IDE中定位到具体的Java文件，支持快速跳转到源码
     * 格式为文件系统的绝对路径
     */
    private String virtualFilePath;
    
    /**
     * 方法在文件中的偏移量
     * 用于在IDE中精确定位到方法的起始位置，支持快速跳转到源码
     * 偏移量以文件开头为0计算的字符位置
     */
    private int methodOffset;
}