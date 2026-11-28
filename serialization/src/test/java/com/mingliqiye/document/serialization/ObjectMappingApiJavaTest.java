package com.mingliqiye.document.serialization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mingliqiye.document.core.DocumentTypeReference;
import com.mingliqiye.document.core.Node;
import com.mingliqiye.document.format.json.JsonMapper;
import com.mingliqiye.document.serialization.exception.DocumentSerializationException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 对象映射便捷 API 的 Java 调用测试。
 *
 * <p>验证 Java 直接通过 {@link JsonMapper} 具体类型即可调用树 API、值 API 与
 * Reader/Writer 包装，不需要额外适配层或 Kotlin 语法支持。
 */
class ObjectMappingApiJavaTest {

    private final JsonMapper mapper = new JsonMapper();

    @Test
    void treeApiIsCallableFromJava() {
        Node node = mapper.readTree("{\"name\":\"小于\",\"age\":18,\"tags\":[\"a\",\"b\"]}");
        assertTrue(node.isObject());
        assertEquals("小于", node.asObject().get("name").asString());
        assertEquals(2, node.asObject().get("tags").asArray().getSize());

        String text = mapper.writeTree(node);
        assertEquals(node, mapper.readTree(text));
        assertArrayEquals(mapper.formatBytes(node), mapper.writeTreeAsBytes(node));

        Node valueTree = mapper.valueToTree(user("小于", 18));
        User restored = mapper.treeToValue(valueTree, User.class);
        assertEquals(18, restored.getAge());
        assertEquals("小于", restored.getName());

        assertNull(mapper.treeToValue(mapper.readTree("null"), User.class));
        assertArrayEquals(
            "[1,2]".getBytes(StandardCharsets.UTF_8),
            mapper.writeTreeAsBytes(mapper.readTree("[1,2]"))
        );
    }

    @Test
    void valueApiIsCallableFromJava() {
        User user = mapper.readValue("{\"name\":\"a\",\"age\":1}", User.class);
        assertEquals("a", user.getName());
        assertEquals(
            "b",
            mapper.readValue("{\"name\":\"b\",\"age\":2}".getBytes(StandardCharsets.UTF_8), User.class).getName()
        );

        List<Integer> numbers = mapper.readValue("[1,2,3]", DocumentTypeReference.listType(Integer.class));
        assertEquals(Arrays.asList(1, 2, 3), numbers);

        assertEquals(mapper.format(user), mapper.writeValueAsString(user));
        assertArrayEquals(mapper.formatBytes(user), mapper.writeValueAsBytes(user));

        User updated = mapper.updateValue(user, Collections.singletonMap("age", 9));
        assertEquals(9, updated.getAge());
        assertEquals("a", updated.getName());
        assertThrows(DocumentSerializationException.class, () -> mapper.updateValue(user, null));

        User converted = mapper.convertValue(user, User.class);
        assertEquals("a", converted.getName());
        assertEquals(
            Arrays.asList(4, 5),
            mapper.convertValue(Arrays.asList(4, 5), DocumentTypeReference.listType(Integer.class))
        );
    }

    @Test
    void readerWriterAreCallableFromJava() {
        DocumentObjectReader<User> reader = mapper.readerFor(User.class);
        assertEquals("r", reader.readValue("{\"name\":\"r\",\"age\":2}").getName());
        assertEquals(
            "r",
            reader.readValue("{\"name\":\"r\",\"age\":2}".getBytes(StandardCharsets.UTF_8)).getName()
        );

        DocumentObjectReader<List<Integer>> listReader =
            mapper.readerFor(DocumentTypeReference.listType(Integer.class));
        assertEquals(Arrays.asList(1, 2), listReader.readValue("[1,2]"));

        DocumentObjectWriter writer = mapper.writer();
        User user = user("w", 3);
        assertEquals(mapper.format(user), writer.writeValueAsString(user));
        assertArrayEquals(mapper.formatBytes(user), writer.writeValueAsBytes(user));
        assertFalse(writer.getPretty());

        DocumentObjectWriter prettyWriter = mapper.writerWithDefaultPrettyPrinter();
        String pretty = prettyWriter.writeValueAsString(user);
        assertTrue(pretty.contains("\n"));
        assertTrue(prettyWriter.getPretty());
        assertEquals("w", mapper.readValue(pretty.getBytes(StandardCharsets.UTF_8), User.class).getName());
    }

    private static User user(String name, int age) {
        User user = new User();
        user.setName(name);
        user.setAge(age);
        return user;
    }
}
