package com.mingliqiye.document.format.ion

import com.amazon.ion.IonSystem
import com.amazon.ion.system.IonSystemBuilder
import com.mingliqiye.document.core.Node
import java.io.ByteArrayOutputStream

/**
 * 将文档树写成二进制 Ion。
 *
 * 使用 ion-java 的 `IonSystem.newBinaryWriter(OutputStream)`，输出以
 * `$ion_1_0` 二进制版本标记开头；`BinaryNode` 写为 Ion blob。
 *
 * [preserveTypes] = true 时识别 [IonTypeFidelity] 包装对象并写成对应的
 * timestamp / symbol / clob / blob；[IonWriteFeature.USE_NATIVE_TYPE_ID] 开启时
 * 把 `$ionAnnotations` 包装对象写成原生 Ion annotation。
 *
 * @param preserveTypes 是否识别类型保真包装对象
 * @param writeFeatures Ion 写出特性
 */
internal class IonBinaryWriter(
    private val preserveTypes: Boolean = false,
    private val writeFeatures: Set<IonWriteFeature> = IonConfig.DEFAULT_WRITE_FEATURES,
) {

    /**
     * 写出节点并返回二进制 Ion 字节。
     */
    fun write(node: Node): ByteArray {
        val output = ByteArrayOutputStream()
        val writer = ION_SYSTEM.newBinaryWriter(output)
        writeIonNode(writer, node, preserveTypes, writeFeatures)
        writer.finish()
        return output.toByteArray()
    }

    private companion object {

        private val ION_SYSTEM: IonSystem = IonSystemBuilder.standard().build()
    }
}

/**
 * 将文档树写成二进制 Ion 字节。
 *
 * @return 二进制 Ion 内容
 */
public fun Node.toIonBytes(): ByteArray = IonBinaryWriter().write(this)
