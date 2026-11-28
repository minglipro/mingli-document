package com.mingliqiye.document.graalvm.smoke;

import com.mingliqiye.document.annotation.DocumentIgnore;
import com.mingliqiye.document.annotation.DocumentName;
import com.mingliqiye.document.core.Node;
import com.mingliqiye.document.format.json.JsonMapper;

/**
 * GraalVM Native Image 冒烟示例。
 *
 * <p>本类只使用 mingli-document 的公开 API，不依赖 GraalVM：先在 JVM 上运行验证行为，
 * 再用 native-image 构建成静态镜像验证同一份代码在 native 下仍可工作。</p>
 *
 * <p>覆盖的反射路径：</p>
 * <ul>
 *     <li>普通可变 Bean（无参构造 + getter/setter）与嵌套 Bean</li>
 *     <li>{@code @DocumentName} / {@code @DocumentIgnore} 注解解析</li>
 *     <li>Java record 组件读写</li>
 *     <li>文本、格式化文本、UTF-8 字节与 Node 树四条入口</li>
 * </ul>
 *
 * <p>这些路径在 native 镜像中都需要反射元数据，见同目录
 * {@code META-INF/native-image/.../reflect-config.json} 与上级 README。</p>
 */
public final class NativeSmoke {

    private NativeSmoke() {
    }

    public static void main(String[] args) {
        JsonMapper mapper = new JsonMapper();

        Address address = new Address();
        address.setCity("Hangzhou");
        address.setZip(310000);

        User user = new User();
        user.setName("Li Ming");
        user.setAge(30);
        user.setActive(true);
        user.setSecret("internal-only");
        user.setAddress(address);

        // 1) Bean -> JSON：getter 反射、@DocumentName、@DocumentIgnore、嵌套 Bean。
        String json = mapper.format(user);
        System.out.println("JSON        : " + json);
        require(json.contains("\"user_name\":\"Li Ming\""), "@DocumentName 未生效: " + json);
        require(json.contains("\"city\":\"Hangzhou\""), "嵌套 Bean 未序列化: " + json);
        require(!json.contains("internal-only"), "@DocumentIgnore 未生效: " + json);

        // 2) JSON -> Bean：无参构造 + setter、嵌套 Bean。
        User parsed = mapper.parse(json, User.class);
        require("Li Ming".equals(parsed.getName()), "反序列化 name 失败");
        require(parsed.getAge() == 30, "反序列化 age 失败");
        require(parsed.isActive(), "反序列化 active 失败");
        require(parsed.getAddress() != null && "Hangzhou".equals(parsed.getAddress().getCity()), "反序列化嵌套 Bean 失败");

        // 3) 格式化文本与 UTF-8 字节路径。
        String pretty = mapper.formatPretty(user);
        require(pretty.contains("\n") && pretty.contains("\"user_name\""), "formatPretty 输出异常: " + pretty);
        byte[] bytes = mapper.formatBytes(user);
        User fromBytes = mapper.parse(bytes, User.class);
        require("Li Ming".equals(fromBytes.getName()), "字节路径反序列化失败");

        // 4) Java record（JDK 16+ 运行时）：record 组件读写反射。
        Point point = new Point(3, 4);
        String pointJson = mapper.format(point);
        System.out.println("Record JSON : " + pointJson);
        Point parsedPoint = mapper.parse(pointJson, Point.class);
        require(parsedPoint.x() == 3 && parsedPoint.y() == 4, "record 读写失败: " + pointJson);

        // 5) 文档树 <-> Bean。
        Node node = mapper.toNode(user);
        User fromNode = mapper.fromNode(node, User.class);
        require("Li Ming".equals(fromNode.getName()), "Node 路径转换失败");

        System.out.println("SMOKE OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("SMOKE FAILED: " + message);
        }
    }

    /** 嵌套 Bean：验证递归 Bean 映射。 */
    public static class Address {

        private String city;

        private int zip;

        public Address() {
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public int getZip() {
            return zip;
        }

        public void setZip(int zip) {
            this.zip = zip;
        }
    }

    /** 可变 Bean：无参构造 + getter/setter，含注解与嵌套 Bean。 */
    public static class User {

        @DocumentName("user_name")
        private String name;

        private int age;

        private boolean active;

        private String secret;

        private Address address;

        public User() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        @DocumentIgnore
        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }
    }

    /** Java record：验证 record 组件反射路径。 */
    public record Point(int x, int y) {
    }
}
