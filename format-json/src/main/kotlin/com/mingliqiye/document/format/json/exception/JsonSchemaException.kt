package com.mingliqiye.document.format.json.exception

import com.mingliqiye.document.exception.DocumentException

/**
 * JSON Schema 编译或使用错误。
 */
public class JsonSchemaException : DocumentException {

    /**
     * 创建异常。
     *
     * @param message 错误信息
     * @param cause 原始异常
     */
    public constructor(message: String, cause: Throwable? = null) : super(message, cause)
}
