package com.mingliqiye.document.annotation

/**
 * 指定 XML 格式写出时使用的根元素名。
 *
 * 仅 XML Mapper 使用；没有注解时使用 [com.mingliqiye.document.format.xml.XmlConfig] 的
 * `rootName`，再回退到 `root`。
 *
 * @property value 根元素名
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
public annotation class DocumentRootName(
    val value: String,
)
