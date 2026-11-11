package ink.icoding.wechat.article.assistant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上游故障 → 中文提示的映射（纯函数，无 Spring）。
 *
 * <p>为什么值得单测：这段逻辑的输入是**第三方 SDK 拼出来的字符串**，格式没有契约保证。
 * 它最容易坏的方式不是抛异常，而是「认不出来 → 原样输出」——用户看到的又回到
 * 一坨 `{"error":{"code":"RateLimitExceeded…"}}`。所以这里把真实见过的报文形状
 * 固化下来，改文案时至少不会悄悄退化成原样透传。
 */
class AssistantServiceErrorTextTest {

    /** 真实报文：本次实测在模型额度耗尽时得到的就是这一条。 */
    private static final String RATE_LIMIT =
            "java.lang.RuntimeException: SSE connection failed: HTTP 429: {\"error\":{\"message\":"
                    + "\"inference exceeds tpm/rpm limit\",\"type\":\"rate_limit_error\",\"param\":\"\","
                    + "\"code\":\"RateLimitExceeded.EndpointTPMExceeded\"}}";

    @Test
    void rateLimitBecomesActionableChinese() {
        String text = AssistantService.readableError(new RuntimeException(RATE_LIMIT));

        // 状态码可以保留（排查需要线索），但原始 JSON 报文与英文错误码必须消失
        assertThat(text).doesNotContain("{").doesNotContain("rate_limit_error")
                .doesNotContain("EndpointTPMExceeded");
        assertThat(text).contains("频率").contains("额度");
        // 必须给出下一步动作，否则用户只知道"失败了"
        assertThat(text).contains("换一个模型").contains("模型档案");
    }

    @Test
    void invalidApiKeyPointsToTheProfilePage() {
        String text = AssistantService.readableError(
                new RuntimeException("HTTP 401: {\"error\":{\"code\":\"invalid_api_key\"}}"));

        assertThat(text).contains("401").contains("API Key");
        assertThat(text).contains("模型档案");
    }

    @Test
    void insufficientQuotaIsReportedAsQuotaNotAsGenericFailure() {
        String text = AssistantService.readableError(
                new RuntimeException("HTTP 403: {\"error\":{\"code\":\"insufficient_quota\"}}"));

        assertThat(text).contains("额度");
    }

    @Test
    void timeoutTellsUserToRetryOrSwitchModel() {
        String text = AssistantService.readableError(new RuntimeException("read timed out"));

        assertThat(text).contains("超时").contains("重试");
    }

    @Test
    void dnsFailurePointsAtBaseUrl() {
        String text = AssistantService.readableError(
                new RuntimeException("java.net.UnknownHostException: api.example.invalid"));

        assertThat(text).contains("baseUrl");
    }

    /** 实测形态：glm 档案返回 503，切链提示里原本吐出整段英文 JSON。 */
    @Test
    void serviceUnavailableIsReportedAsUpstreamOverload() {
        String text = AssistantService.readableError(new RuntimeException(
                "java.lang.RuntimeException: SSE connection failed: HTTP 503: {\"error\":{\"code\":\"x\"}}"));

        assertThat(text).contains("暂时不可用").contains("5xx");
        assertThat(text).doesNotContain("{").doesNotContain("SSE connection failed");
    }

    /**
     * 切链提示（brief）用的是**短原因**，不是完整操作指引。
     *
     * <p>这条守的是排版：括号里塞整句会变成「（模型供应商暂时不可用（5xx），通常是…）」，
     * 双层括号比原来的英文 JSON 还难读。所以短原因必须独立于 friendlyUpstream 的整句。
     */
    @Test
    void switchNoticeUsesAShortReasonNotTheWholeSentence() {
        String reason = AssistantService.shortReason("SSE connection failed: HTTP 503: {\"error\":{}}");

        assertThat(reason).isEqualTo("供应商暂时不可用");
        assertThat(reason).doesNotContain("请稍后").as("括号里不该带操作指引");
        assertThat(reason).doesNotContain("（").as("外面已套一层括号，短原因里不能再有括号");
    }

    @Test
    void shortReasonCoversTheSameCategoriesAsTheLongText() {
        assertThat(AssistantService.shortReason("HTTP 429 rate_limit")).contains("限流");
        assertThat(AssistantService.shortReason("HTTP 401 invalid_api_key")).contains("鉴权");
        assertThat(AssistantService.shortReason("HTTP 403 insufficient_quota")).contains("额度");
        assertThat(AssistantService.shortReason("read timed out")).isEqualTo("等待响应超时");
        assertThat(AssistantService.shortReason("Connection refused")).isEqualTo("连不上供应商地址");
        assertThat(AssistantService.shortReason("某个没见过的错误")).isNull();
    }

    /** 实测形态：工具执行成功后上游把 SSE 流掐了，面板上原本只剩一行英文。 */
    @Test
    void droppedConnectionIsExplainedAndSuggestsRetry() {
        String text = AssistantService.readableError(new RuntimeException(
                "java.lang.RuntimeException: SSE connection failed: HTTP 200, Socket closed"));

        assertThat(text).contains("中断").contains("重试");
        // 特征词可以留（排查要认出是哪一类断流），要去掉的是异常类名与 SDK 前缀
        assertThat(text).doesNotContain("SSE connection failed")
                .doesNotContain("java.lang.RuntimeException");
    }

    /**
     * 连接级瞬断的识别：**决定要不要原地重试**，判错会把「已改过数据」的轮次重跑一遍。
     *
     * <p>这几条都是实测遇到过的形态。注意 {@code goaway}（HTTP/2）与 {@code premature end}
     * 看起来像别的错误，本质同样是连接层被掐断，必须归到同一类。
     */
    @Test
    void transientConnectionFailuresAreRecognized() {
        assertThat(AssistantService.isTransientConnectionFailure(
                new RuntimeException("SSE connection failed: HTTP 200, Socket closed"))).isTrue();
        assertThat(AssistantService.isTransientConnectionFailure(
                new java.io.IOException("Connection reset"))).isTrue();
        assertThat(AssistantService.isTransientConnectionFailure(
                new RuntimeException("unexpected end of stream"))).isTrue();
        assertThat(AssistantService.isTransientConnectionFailure(
                new RuntimeException("Broken pipe"))).isTrue();
        // 包装在 cause 链里也要认出来
        assertThat(AssistantService.isTransientConnectionFailure(
                new RuntimeException("调用失败", new java.io.IOException("Socket closed")))).isTrue();
        // 不能误伤：限流/鉴权/额度这类要换档案，不该原地重试
        assertThat(AssistantService.isTransientConnectionFailure(
                new RuntimeException("HTTP 429 rate limit"))).isFalse();
        assertThat(AssistantService.isTransientConnectionFailure(
                new RuntimeException("HTTP 401 invalid_api_key"))).isFalse();
        assertThat(AssistantService.isTransientConnectionFailure(null)).isFalse();
    }

    @Test
    void shortReasonCoversDroppedConnection() {
        assertThat(AssistantService.shortReason("HTTP 200, Socket closed")).isEqualTo("连接被中断");
    }

    /**
     * 认不出来的错误保持原样（截断），**不能**被映射成一句「服务异常」。
     *
     * <p>理由：落库的失败记录是排查的唯一线索。把未知错误统一压成一句没有细节的话，
     * 等于把「为什么坏了」这件事从系统里抹掉。
     */
    @Test
    void unknownErrorKeepsItsOwnMessage() {
        String text = AssistantService.readableError(new IllegalStateException("序列化失败：x"));

        assertThat(text).isEqualTo("序列化失败：x");
    }

    @Test
    void unknownErrorIsTruncatedSoItCannotFloodThePanel() {
        String text = AssistantService.readableError(new IllegalStateException("坏".repeat(500)));

        assertThat(text).hasSizeLessThanOrEqualTo(201);
        assertThat(text).endsWith("…");
    }

    /** BusinessException 的文案是给用户看的，必须原样透出（不能被上游映射覆盖）。 */
    @Test
    void businessMessagePassesThroughUntouched() {
        String text = AssistantService.readableError(
                new ink.icoding.wechat.article.common.BusinessException("所选模型档案不存在或已被删除"));

        assertThat(text).isEqualTo("所选模型档案不存在或已被删除");
    }
}