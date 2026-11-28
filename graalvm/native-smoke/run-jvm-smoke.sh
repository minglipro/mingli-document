#!/usr/bin/env bash
# mingli-document GraalVM Native Image 冒烟示例 —— JVM 侧运行脚本（Bash）。
#
# 作用：
#   1. 用仓库已构建的库 jar 编译 NativeSmoke.java；
#   2. 把 native-smoke 的 META-INF/native-image 配置一起拷到 classes 目录；
#   3. 在 JVM 上运行 smoke，验证示例与配置引用的类/注解都可用。
#
# 本脚本不需要 GraalVM；native 构建命令见 README.md。
# 先决条件：仓库根目录执行过
#   ./gradlew :annotation:jar :exception:jar :core:jar :serialization:jar :format-json:jar
#
# 可选环境变量（自动探测失败时手工指定）：
#   KOTLIN_STDLIB_JAR     kotlin-stdlib-2.x.jar 的绝对路径
#   ALIAS_FOR_JAR         alias-for-1.0.3.jar 的绝对路径
#   MINGLI_EXCEPTION_JAR  mingli-exception-1.1.1-Final.jar 的绝对路径

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
build_dir="$script_dir/build"
classes_dir="$build_dir/classes"

echo '== mingli-document GraalVM native smoke (JVM) =='
echo "repo root : $repo_root"

missing=()
lib_jars=()
for relative in \
    annotation/build/libs/annotation-2.2.0.jar \
    exception/build/libs/exception-2.2.0.jar \
    core/build/libs/core-2.2.0.jar \
    serialization/build/libs/serialization-2.2.0.jar \
    format-json/build/libs/format-json-2.2.0.jar; do
    if [[ -f "$repo_root/$relative" ]]; then
        lib_jars+=("$repo_root/$relative")
    else
        missing+=("$relative")
    fi
done

if (( ${#missing[@]} > 0 )); then
    echo ''
    echo '缺少库 jar：'
    printf '  - %s\n' "${missing[@]}"
    echo ''
    echo '请先在仓库根目录构建库模块：'
    echo '  ./gradlew :annotation:jar :exception:jar :core:jar :serialization:jar :format-json:jar'
    exit 1
fi

gradle_home="${GRADLE_USER_HOME:-$HOME/.gradle}"

find_gradle_jar() {
    local module_path="$1"
    local name_regex="$2"
    local module_dir="$gradle_home/caches/modules-2/files-2.1/$module_path"
    [[ -d "$module_dir" ]] || return 0
    find "$module_dir" -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' 2>/dev/null \
        | grep -E "/$name_regex\$" \
        | sort -r \
        | head -n 1 || true
}

kotlin_stdlib="${KOTLIN_STDLIB_JAR:-}"
if [[ -z "$kotlin_stdlib" ]]; then
    kotlin_stdlib="$(find_gradle_jar 'org.jetbrains.kotlin/kotlin-stdlib' 'kotlin-stdlib-[0-9][^/]*\.jar')"
fi
alias_for="${ALIAS_FOR_JAR:-}"
if [[ -z "$alias_for" ]]; then
    alias_for="$(find_gradle_jar 'com.mingliqiye.utils.aliasfor/alias-for' 'alias-for-[0-9][^/]*\.jar')"
fi

mingli_exception="${MINGLI_EXCEPTION_JAR:-}"
if [[ -z "$mingli_exception" ]]; then
    mingli_exception="$(find_gradle_jar 'com.mingliqiye.exception/mingli-exception' 'mingli-exception-[0-9][^/]*\.jar')"
fi

if [[ -z "$kotlin_stdlib" ]]; then
    echo '找不到 kotlin-stdlib jar：请设置 KOTLIN_STDLIB_JAR 指向 kotlin-stdlib-2.x.jar'
    exit 1
fi
if [[ -z "$alias_for" ]]; then
    echo '找不到 alias-for jar：请设置 ALIAS_FOR_JAR 指向 alias-for-1.0.3.jar'
    exit 1
fi
if [[ -z "$mingli_exception" ]]; then
    echo '找不到 mingli-exception jar：请设置 MINGLI_EXCEPTION_JAR 指向 mingli-exception-1.1.1-Final.jar'
    exit 1
fi

rm -rf "$classes_dir"
mkdir -p "$classes_dir"
cp -R "$script_dir/META-INF" "$classes_dir/"

classpath="$classes_dir"
for jar in "${lib_jars[@]}"; do
    classpath="$classpath:$jar"
done
classpath="$classpath:$kotlin_stdlib:$alias_for:$mingli_exception"

echo "kotlin-stdlib    : $kotlin_stdlib"
echo "alias-for        : $alias_for"
echo "mingli-exception : $mingli_exception"
echo "classes          : $classes_dir"
echo ''
echo 'javac ...'
javac -encoding UTF-8 --release 17 -cp "$classpath" -d "$classes_dir" "$script_dir/NativeSmoke.java"

echo 'java ...'
java -cp "$classpath" com.mingliqiye.document.graalvm.smoke.NativeSmoke

echo ''
echo 'JVM smoke 通过（SMOKE OK）。'
echo ''
echo '如需构建 native 镜像（需要 GraalVM for JDK 17+，且 native-image 在 PATH 中；'
echo '本库 Bean 映射依赖 java.beans.Introspector，建议使用 GraalVM for JDK 22+）：'
echo "  native-image -cp \"$classpath\" -H:Name=\"$build_dir/native-smoke\" \\"
echo '    com.mingliqiye.document.graalvm.smoke.NativeSmoke'
echo ''
echo '说明：native-smoke 的反射配置已随 classes/META-INF/native-image 进入 classpath，'
echo 'native-image 会自动发现；serialization 库 jar 内嵌的配置也会自动生效。'
