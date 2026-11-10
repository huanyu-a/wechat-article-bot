package ink.icoding.wechat.article.agent;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 供应商数据访问。
 *
 * <p><b>为什么没有 {@code findByTriple}（按 (provider, baseUrl, key) 查）</b>：归组键的第三段是
 * **解密后的明文 key**（{@code CryptoService} 的 AES-GCM 每次加密用随机 IV，同一明文得到不同密文，
 * 所以密文相等**不等于**同一个 key），明文不可能作为 SQL 条件，只能在 Java 层逐行解密后比较——
 * 那一步在 {@link LlmProviderService#findByTriple}。供应商行数是个位数，全表读出再比是可接受的代价。
 */
@Mapper
public interface LlmProviderMapper extends SmartMapper<LlmProvider> {
    default List<LlmProvider> findAll() {
        return select(Where.where().orderBy(LlmProvider::getId).asc());
    }

    default LlmProvider findById(Long id) {
        return selectById(id);
    }

    default LlmProvider findByName(String name) {
        return selectFirst(Where.where(LlmProvider::getName).eq(name));
    }
}
