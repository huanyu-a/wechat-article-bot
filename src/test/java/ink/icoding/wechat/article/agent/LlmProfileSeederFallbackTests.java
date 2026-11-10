package ink.icoding.wechat.article.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 标准档案种子的「兜底档案唯一」断言（2026-11-10 生产升级回归）。
 *
 * <p>为什么需要这个类：{@link LlmProfileMapper#findFallback()} 的契约写着「应用层保证唯一」，
 * 但种子清单里 {@code hy4-preview} 声明了 {@code fallback=true}，而原先的
 * {@code createIfAbsent} 只检查「同名档案在不在」，**不检查库里是否已经有兜底**。
 * 于是老库（本来就设过 qwen3.8-flash 为兜底）升级后会被种子再插一条兜底档案，把不变量破坏掉。
 *
 * <p>行为后果要如实记下来：{@code findFallback()} 按 id 升序取第一条，所以**实际生效的兜底没变**，
 * 多出来的那个标志当时没有改变故障切换的终点。但「唯一」一旦失守，后续任何按 is_fallback
 * 统计、导出或改选的逻辑都会得到两条，属于必须现在就钉住的数据卫生问题。
 *
 * <p>用 Mockito 而不是手写 mapper 实现：{@code SmartMapper} 的抽象方法很多，
 * 逐个实现既容易漏（第一次就漏了 executeSql 编译不过），也会随上游接口变化而失效。
 */
class LlmProfileSeederFallbackTests {

    /** 内存表 + Mockito 转发：只钉本类关心的查询语义。 */
    private static final class MemoryStore {
        final List<LlmProfile> rows = new ArrayList<>();
        long nextId = 1;

        LlmProfileMapper mapper() {
            LlmProfileMapper mapper = mock(LlmProfileMapper.class);
            when(mapper.findByName(anyString())).thenAnswer(inv -> rows.stream()
                    .filter(r -> inv.getArgument(0).equals(r.getName())).findFirst().orElse(null));
            when(mapper.findFallback()).thenAnswer(inv -> rows.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsFallback())).findFirst().orElse(null));
            when(mapper.findDefault()).thenAnswer(inv -> rows.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsDefault())).findFirst().orElse(null));
            when(mapper.findAll()).thenAnswer(inv -> rows);
            when(mapper.insert(any(LlmProfile.class))).thenAnswer(inv -> {
                LlmProfile row = inv.getArgument(0);
                row.setId(nextId++);
                rows.add(row);
                return 1;
            });
            when(mapper.updateById(any(LlmProfile.class))).thenReturn(1);
            return mapper;
        }

        List<String> fallbackNames() {
            return rows.stream().filter(r -> Boolean.TRUE.equals(r.getIsFallback()))
                    .map(LlmProfile::getName).toList();
        }
    }

    private static LlmProfileSeeder seeder(LlmProfileMapper mapper) {
        LlmProfileService service = mock(LlmProfileService.class);
        // 复制来源的判据走运行时口径：这里让任何带密文的档案都算「拿得到 key」
        when(service.hasReachableKey(any())).thenAnswer(inv -> {
            LlmProfile profile = inv.getArgument(0);
            return profile != null && profile.getApiKeyEncrypted() != null
                    && !profile.getApiKeyEncrypted().isBlank();
        });
        return new LlmProfileSeeder(mapper, service);
    }

    private static LlmProfile seeded(String name, boolean isDefault, boolean isFallback) {
        LlmProfile profile = new LlmProfile();
        profile.setName(name);
        profile.setProviderId(1L);
        profile.setBaseUrl("https://gateway.example.com");
        profile.setModelName("auto/" + name);
        profile.setApiKeyEncrypted("cipher-text-placeholder");
        profile.setEnabled(true);
        profile.setIsDefault(isDefault);
        profile.setIsFallback(isFallback);
        return profile;
    }

    private static void runSeeder(MemoryStore store) {
        seeder(store.mapper()).run(mock(ApplicationArguments.class));
    }

    @Test
    void existingFallbackIsNeverDuplicatedBySeeding() {
        MemoryStore store = new MemoryStore();
        store.rows.add(seeded("deepseek-flash", true, false));
        store.rows.add(seeded("qwen3.8-flash", false, true));

        runSeeder(store);

        assertThat(store.fallbackNames())
                .as("种子不得在老库已有兜底时再插一个兜底档案")
                .containsExactly("qwen3.8-flash");
    }

    @Test
    void declaredFallbackIsAppliedWhenNoneExists() {
        MemoryStore store = new MemoryStore();
        store.rows.add(seeded("deepseek-flash", true, false));

        runSeeder(store);

        assertThat(store.fallbackNames())
                .as("库里没有兜底时，种子应当把声明的那条设为兜底")
                .containsExactly("hy4-preview");
    }

    @Test
    void seedingIsIdempotentAcrossRestarts() {
        MemoryStore store = new MemoryStore();
        store.rows.add(seeded("deepseek-flash", true, false));
        store.rows.add(seeded("qwen3.8-flash", false, true));

        LlmProfileMapper mapper = store.mapper();
        LlmProfileSeeder seeder = seeder(mapper);
        seeder.run(mock(ApplicationArguments.class));
        int afterFirst = store.rows.size();
        seeder.run(mock(ApplicationArguments.class));

        assertThat(afterFirst).as("第一次启动应当补建标准档案").isGreaterThan(2);
        assertThat(store.rows).as("第二次启动不该再插入任何档案").hasSize(afterFirst);
        assertThat(store.fallbackNames()).as("反复启动后兜底档案仍只有一个").hasSize(1);
    }
}
