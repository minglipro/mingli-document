package bench;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mingliqiye.document.core.DocumentTypeReference;
import com.mingliqiye.document.format.json.JsonMapper;
import com.mingliqiye.document.format.json.JsonReader;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JSON 性能基准：本项目 vs 上游实现。
 *
 * 运行：gradle :benchmarks:jmh
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JsonMapperBenchmark {

    private JsonMapper ourMapper;
    private JsonMapper kotlinMapper;
    private ObjectMapper jacksonMapper;
    private List<BenchUser> users;
    private List<KotlinUser> kotlinUsers;
    private String json;
    private String kotlinJson;
    private DocumentTypeReference<List<BenchUser>> typeReference;
    private TypeReference<List<BenchUser>> jacksonType;

    @Setup(Level.Trial)
    public void setup() {
        ourMapper = new JsonMapper();
        kotlinMapper = JsonMapper.builder().addModule(KotlinModuleKt.kotlinModule()).build();
        jacksonMapper = new ObjectMapper();
        users = createUsers(1000);
        kotlinUsers = createKotlinUsers(1000);
        json = ourMapper.format(users);
        kotlinJson = kotlinMapper.format(kotlinUsers);
        typeReference = DocumentTypeReference.listType(BenchUser.class);
        jacksonType = new TypeReference<List<BenchUser>>() {
        };
    }

    @Benchmark
    public Object ourParseTree() {
        return new JsonReader(json, true).read();
    }

    @Benchmark
    public Object jacksonParseTree() throws Exception {
        return jacksonMapper.readTree(json);
    }

    @Benchmark
    public Object ourParseData() {
        return ourMapper.parse(json, typeReference);
    }

    @Benchmark
    public Object jacksonParseData() throws Exception {
        return jacksonMapper.readValue(json, jacksonType);
    }

    @Benchmark
    public Object ourParseKotlinData() {
        return kotlinMapper.parse(kotlinJson, DocumentTypeReference.listType(KotlinUser.class));
    }

    @Benchmark
    public Object ourFormat() {
        return ourMapper.format(users);
    }

    @Benchmark
    public Object jacksonFormat() throws Exception {
        return jacksonMapper.writeValueAsString(users);
    }

    private static List<KotlinUser> createKotlinUsers(int size) {
        List<KotlinUser> result = new ArrayList<KotlinUser>(size);
        for (int index = 1; index <= size; index++) {
            result.add(new KotlinUser(
                    index,
                    "user-" + index,
                    index % 2 == 0,
                    index * 1.5,
                    Arrays.asList("alpha", "beta", "gamma"),
                    new KotlinAddress("杭州", "310000")
            ));
        }
        return result;
    }

    private static List<BenchUser> createUsers(int size) {
        List<BenchUser> result = new ArrayList<BenchUser>(size);
        for (int index = 1; index <= size; index++) {
            result.add(new BenchUser(
                    index,
                    "user-" + index,
                    index % 2 == 0,
                    index * 1.5,
                    Arrays.asList("alpha", "beta", "gamma"),
                    new Address("杭州", "310000")
            ));
        }
        return result;
    }
}
