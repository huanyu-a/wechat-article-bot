package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.asset.Asset;
import ink.icoding.wechat.article.asset.AssetService;
import ink.icoding.wechat.article.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 封面归属校验单测（Phase 4.5）：三条链路统一在交付前校验。
 *
 * <p>为什么必须统一：素材库按公众号隔离，封面是**对外可见**的内容——把 A 号素材当 B 号封面，
 * 既是越权使用，也可能泄露未发布的视觉素材。此前只有 SINGLE 链路校验
 * （{@code ArticleAiService.runScheduledAgent}），PIPELINE / COORDINATOR 两条链路产出的文章
 * 可以带别的公众号的封面。
 */
class TaskExecutionServiceCoverTest {

    private static TaskExecutionService service(AssetService assetService) {
        // 构造器按并发上限分配线程池，因此需要一个真实的 InFlightGate（不能用 mock：它会读 limit()）
        ink.icoding.wechat.article.common.InFlightGate gate =
                new ink.icoding.wechat.article.common.InFlightGate(2, 0);
        return new TaskExecutionService(null, null, null, null, null, null, gate, assetService, 3L, 120L, 15L);
    }

    private static Asset asset(Long id, Long accountId) {
        Asset asset = new Asset();
        asset.setId(id);
        asset.setAccountId(accountId);
        return asset;
    }

    /** 封面属于目标公众号：放行。 */
    @Test
    void coverFromTheSameAccountIsAccepted() {
        AssetService assetService = mock(AssetService.class);
        when(assetService.required(5L)).thenReturn(asset(5L, 1L));

        assertThatCode(() -> service(assetService).requireCoverOwnership(1L, 5L))
                .doesNotThrowAnyException();
    }

    /** 封面属于别的公众号：拒绝交付。 */
    @Test
    void coverFromAnotherAccountIsRejected() {
        AssetService assetService = mock(AssetService.class);
        when(assetService.required(5L)).thenReturn(asset(5L, 2L));

        assertThatThrownBy(() -> service(assetService).requireCoverOwnership(1L, 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("封面素材不属于任务目标公众号");
    }

    /** 未指定公众号（仅创建本地文章）时不校验：此时不存在「归属」这个概念。 */
    @Test
    void noAccountMeansNoValidation() {
        AssetService assetService = mock(AssetService.class);
        when(assetService.required(5L)).thenReturn(asset(5L, 2L));

        assertThatCode(() -> service(assetService).requireCoverOwnership(null, 5L))
                .doesNotThrowAnyException();
    }

    /** 无封面时不校验。 */
    @Test
    void noCoverMeansNoValidation() {
        assertThatCode(() -> service(mock(AssetService.class)).requireCoverOwnership(1L, null))
                .doesNotThrowAnyException();
    }

    /**
     * 素材查不到（已被删除）时**放行**，不在这里抛错。
     *
     * <p>那是「素材被删」而不是「归属错误」，该由后续落库路径按既有逻辑处理。
     * 在这里抛错会把一个可降级的状态（无封面交付）变成整轮失败。
     */
    @Test
    void missingAssetIsToleratedRatherThanFailingTheRun() {
        AssetService assetService = mock(AssetService.class);
        when(assetService.required(5L)).thenThrow(new BusinessException("素材不存在"));

        assertThatCode(() -> service(assetService).requireCoverOwnership(1L, 5L))
                .doesNotThrowAnyException();
    }

    /** 素材本身无归属（历史数据 accountId 为空）时不误判为越权。 */
    @Test
    void assetWithoutAccountIsNotTreatedAsForeign() {
        AssetService assetService = mock(AssetService.class);
        when(assetService.required(5L)).thenReturn(asset(5L, null));

        assertThatCode(() -> service(assetService).requireCoverOwnership(1L, 5L))
                .doesNotThrowAnyException();
    }
}
