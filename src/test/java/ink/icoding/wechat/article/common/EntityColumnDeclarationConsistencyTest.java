package ink.icoding.wechat.article.common;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实体列声明一致性回归护栏（Phase 6）。
 *
 * <p><b>为什么需要这个测试</b>：smart-mybatis 的列声明缓存
 * （{@code MapperUtil.getColumnDeclaration}）键是 {@code field.getName()} **单键**，不含类名。
 * 于是「同名字段」在所有实体里**共用一份列声明**——谁先初始化谁说了算。而
 * {@code MysqlDialect.buildAlterColumn} 会发出 {@code MODIFY COLUMN}（已从 3.0.1 的字节码确认：
 * {@code MapperUtil.updateTable} → {@code buildAlterTable} → {@code buildAlterColumn}），
 * 意味着声明窄的一方可以把已放宽的**真实列收窄**。
 *
 * <p><b>这不是理论风险，是已发生的静默收窄</b>：{@code Asset.sourceUrl} 声明 2000，
 * 而 {@code Article} / {@code ArticleRevision} 的同名字段声明 1000，实测 {@code ASSET.SOURCE_URL}
 * 就是 {@code varchar(1000)}——2000 那处声明从未生效过。同类隐患还有 5 组
 * （{@code name} / {@code provider} / {@code modelName} / {@code author}），
 * 其中 {@code name} 若由 100 那处胜出，会把**用户输入的**公众号名、任务名一起收窄到 100 字符。
 *
 * <p>这个测试把「所有同名字段声明一致」变成一条构建期不变量：新增实体或改长度时，
 * 不一致会立刻失败，而不是等到某次重启按初始化顺序悄悄改掉列宽。
 */
class EntityColumnDeclarationConsistencyTest {

    /** 扫描范围：主源码编译产物（测试类路径下同一根）。 */
    private static final File CLASSES_ROOT = new File("target/classes");

    @Test
    void sameNameFieldsShareTheSameColumnDeclaration() throws Exception {
        Map<String, List<String>> declarationsByField = new LinkedHashMap<>();
        Map<String, List<String>> entitiesByField = new LinkedHashMap<>();

        for (Class<?> entity : entityClasses()) {
            for (Field field : entity.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) continue;
                TableField annotation = field.getAnnotation(TableField.class);
                if (annotation != null && !annotation.exist()) continue;
                String declaration = annotation == null
                        ? "default(len=255)" : "len=" + annotation.length() + ",colType=" + annotation.columnType();
                declarationsByField.computeIfAbsent(field.getName(), key -> new ArrayList<>()).add(declaration);
                entitiesByField.computeIfAbsent(field.getName(), key -> new ArrayList<>())
                        .add(entity.getSimpleName() + "=" + declaration);
            }
        }

        assertThat(entitiesByField).as("未扫描到任何实体，说明 target/classes 不是预期结构").isNotEmpty();

        List<String> collisions = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : declarationsByField.entrySet()) {
            if (entry.getValue().stream().distinct().count() > 1) {
                collisions.add(entry.getKey() + " → " + entitiesByField.get(entry.getKey()));
            }
        }

        assertThat(collisions)
                .as("同名字段的列声明必须逐字一致，否则 smart-mybatis 会按初始化顺序任选一份"
                        + "（并可能对真实列发 MODIFY COLUMN 收窄）。修法是让所有同名声明取同一个值，"
                        + "且该值必须等于库里真实列宽：%s", collisions)
                .isEmpty();
    }

    /** 已修复的 5 组冲突逐条钉住——回归时能立刻看出是哪一组又漂了。 */
    @Test
    void previouslyCollidingFieldsStayAligned() throws Exception {
        // sourceUrl：曾在 1000/2000 之间冲突，ASSET.SOURCE_URL 被静默收窄成 varchar(1000)
        assertDeclaration("sourceUrl", "len=1000,colType=");
        // name：曾在 100/255 之间冲突（255 是 WechatAccount/ScheduleTask 的默认值）
        assertDeclaration("name", "len=255,colType=");
        // provider：曾在 50/255 之间冲突
        assertDeclaration("provider", "len=50,colType=");
        // modelName：曾在 200/255 之间冲突
        assertDeclaration("modelName", "len=200,colType=");
        // author：曾在 255/default 之间冲突（default 也是 255，但声明文本不同即会二选一）
        assertDeclaration("author", "len=255,colType=");
    }

    private static void assertDeclaration(String fieldName, String expected) throws Exception {
        List<String> found = new ArrayList<>();
        for (Class<?> entity : entityClasses()) {
            for (Field field : entity.getDeclaredFields()) {
                if (!field.getName().equals(fieldName)) continue;
                TableField annotation = field.getAnnotation(TableField.class);
                if (annotation != null && !annotation.exist()) continue;
                found.add(annotation == null
                        ? "default(len=255)" : "len=" + annotation.length() + ",colType=" + annotation.columnType());
            }
        }
        assertThat(found).as("字段 %s 在所有实体里都应声明为 %s", fieldName, expected)
                .isNotEmpty().allMatch(expected::equals);
    }

    /** 扫描 {@code target/classes} 下所有 PO 实体（与线上装配用的是同一份编译产物）。 */
    private static List<Class<?>> entityClasses() throws Exception {
        List<Class<?>> found = new ArrayList<>();
        collect(CLASSES_ROOT, CLASSES_ROOT, found);
        return found;
    }

    private static void collect(File root, File dir, List<Class<?>> out) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                collect(root, file, out);
                continue;
            }
            if (!file.getName().endsWith(".class") || file.getName().contains("$")) continue;
            String relative = root.toURI().relativize(file.toURI()).getPath();
            String className = relative.substring(0, relative.length() - 6).replace('/', '.');
            try {
                Class<?> candidate = Class.forName(className, false,
                        EntityColumnDeclarationConsistencyTest.class.getClassLoader());
                if (PO.class.isAssignableFrom(candidate) && !candidate.isInterface()
                        && !Modifier.isAbstract(candidate.getModifiers())) {
                    out.add(candidate);
                }
            } catch (Throwable ignored) {
                // 内部类/不可加载的类跳过：本测试只关心实体
            }
        }
    }
}
