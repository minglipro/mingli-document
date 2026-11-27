package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.Node

/**
 * 远程 `$ref` 文档解析器。
 *
 * 当 `$ref` 不以 `#` 开头时，Mapper 调用本解析器获取外部文档树；
 * 解析器负责 URI 校验、IO、缓存与安全策略。
 */
public fun interface RemoteReferenceResolver {

    /**
     * 解析远程 URI 对应的文档树。
     *
     * @param uri 不含 fragment 的 URI
     * @return 外部文档树
     */
    public fun resolve(uri: String): Node
}
