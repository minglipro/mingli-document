package bench;

import com.mingliqiye.document.core.DocumentTypeReference;
import com.mingliqiye.document.format.cbor.CborMapper;
import com.mingliqiye.document.format.cbor.CborReader;
import com.mingliqiye.document.format.cbor.CborWriter;
import com.mingliqiye.document.format.json.JsonMapper;
import com.mingliqiye.document.format.json.JsonTokenReader;
import com.mingliqiye.document.format.json.JsonValueReader;
import com.mingliqiye.document.serialization.kotlin.KotlinModuleKt;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Token 直读/直写路径基准。
 *
 * 运行：gradle :benchmarks:jmh
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class TokenPathBenchmark {

    private JsonMapper jsonMapper;
    private CborMapper cborMapper;
    private List<KotlinUser> users;
    private List<BenchUser> javaUsers;
    private String json;
    private String javaJson;
    private byte[] cbor;
    private DocumentTypeReference<List<KotlinUser>> typeReference;
    private DocumentTypeReference<List<BenchUser>> javaTypeReference;

    @Setup(Level.Trial)
    public void setup() {
        jsonMapper = JsonMapper.builder().addModule(KotlinModuleKt.kotlinModule()).build();
        cborMapper = CborMapper.builder().addModule(KotlinModuleKt.kotlinModule()).build();
        users = createUsers(1000);
        javaUsers = createJavaUsers(1000);
        json = jsonMapper.format(users);
        javaJson = jsonMapper.format(javaUsers);
        cbor = cborMapper.formatBytes(users);
        typeReference = DocumentTypeReference.listType(KotlinUser.class);
        javaTypeReference = DocumentTypeReference.listType(BenchUser.class);
    }

    private static List<BenchUser> createJavaUsers(int size) {
        java.util.ArrayList<BenchUser> result = new java.util.ArrayList<BenchUser>(size);
        for (int index = 0; index < size; index++) {
            result.add(new BenchUser(
                    index,
                    "user-" + index,
                    index % 2 == 0,
                    index * 1.5,
                    java.util.Arrays.asList("a", "b", "c"),
                    new Address("city-" + index, String.valueOf(index * 10))
            ));
        }
        return result;
    }

    private static List<KotlinUser> createUsers(int size) {
        java.util.ArrayList<KotlinUser> result = new java.util.ArrayList<KotlinUser>(size);
        for (int index = 0; index < size; index++) {
            result.add(new KotlinUser(
                    index,
                    "user-" + index,
                    index % 2 == 0,
                    index * 1.5,
                    java.util.Arrays.asList("a", "b", "c"),
                    new KotlinAddress("city-" + index, String.valueOf(index * 10))
            ));
        }
        return result;
    }

    @Benchmark
    public Object jsonTokenScan() {
        JsonTokenReader reader = new JsonTokenReader(json, true);
        while (reader.nextToken() != null) {
            // drain
        }
        return null;
    }

    @Benchmark
    public Object jsonParseJavaBeanToken() {
        return jsonMapper.parse(javaJson, javaTypeReference);
    }

    @Benchmark
    public Object jsonParseJavaBeanValueReader() {
        return jsonMapper.readFrom(new JsonValueReader(javaJson, true), javaTypeReference);
    }

    @Benchmark
    public Object jsonParseBeanToken() {
        return jsonMapper.parse(json, typeReference);
    }

    @Benchmark
    public Object jsonParseBeanValueReader() {
        return jsonMapper.readFrom(new JsonValueReader(json, true), typeReference);
    }

    @Benchmark
    public Object cborParseBeanToken() {
        return cborMapper.parse(cbor, typeReference);
    }

    @Benchmark
    public Object cborParseBeanNode() {
        return cborMapper.fromNode(new CborReader(cbor).read(), typeReference);
    }

    @Benchmark
    public Object cborFormatBeanToken() {
        return cborMapper.formatBytes(users);
    }

    @Benchmark
    public Object cborFormatBeanNode() {
        return new CborWriter().write(cborMapper.toNode(users));
    }
}
