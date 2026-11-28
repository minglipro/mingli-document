# 大文件流式 Bean 映射

## 目标

避免把整个文件读入 `String` 或构建完整 `Node` 树，逐条处理元素/记录。

## JSON Lines / NDJSON

```kotlin
val mapper = JsonMapper()

mapper.parseLines(inputStream, User::class.java) { user ->
    process(user)
}
```

写出：

```kotlin
mapper.formatLines(users, outputStream)
```

语义：

```text
一行一个完整 JSON 值
空行跳过
BOM 处理
畸形行带行号
不关闭调用方流
```

## JSON 顶层数组

```kotlin
mapper.parseArray(inputStream, User::class.java) { user ->
    process(user)
}
```

`parseArray` 逐元素扫描顶层数组，不构建完整 ArrayNode。

写出：

```kotlin
mapper.formatArray(users, outputStream)
```

## CSV 逐行

```kotlin
val mapper = CsvMapper()

mapper.parseStream(inputStream, User::class.java) { user ->
    process(user)
}
```

支持：

```text
表头 / 无表头
类型推断
CsvSchema / 多级 Schema
嵌套单元格
maxRows
```

## 内存语义

```text
JSON Lines：峰值内存约为单行大小
JSON Array：峰值内存约为单元素大小
CSV：峰值内存约为单条记录大小
```

## 限制

```text
JSON Lines 不支持跨行 JSON
JSON parseArray 只支持顶层数组
元素之间不共享 $ref 上下文
流式写出失败时可能已写出部分内容
调用方负责关闭流
```
