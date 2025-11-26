package io.github.easy.tools.entity.doc;

/**
 * 模板参数模型类，用于承载Velocity模板渲染所需的参数
 * <p>
 * 该类表示一个键值对形式的模板参数，用于在Velocity模板渲染时提供变量替换的值。
 * </p>
 */
public class TemplateParameter<T> {
    /**
     * 参数名称
     */
    private String name;

    /**
     * 参数值
     */
    private T value;

    /**
     * 参数描述
     */
    private String description;

    /**
     * 默认构造函数
     */
    public TemplateParameter() {
    }

    /**
     * 带参数的构造函数
     *
     * @param name        参数名称
     * @param value       参数值
     * @param description 参数描述
     */
    public TemplateParameter(String name, T value, String description) {
        this.name = name;
        this.value = value;
        this.description = description;
    }

    /**
     * 获取参数名称
     *
     * @return 参数名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置参数名称
     *
     * @param name 参数名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取参数值
     *
     * @return 参数值
     */
    public T getValue() {
        return value;
    }

    /**
     * 设置参数值
     *
     * @param value 参数值
     */
    public void setValue(T value) {
        this.value = value;
    }

    /**
     * 获取参数描述
     *
     * @return 参数描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置参数描述
     *
     * @param description 参数描述
     */
    public void setDescription(String description) {
        this.description = description;
    }
}