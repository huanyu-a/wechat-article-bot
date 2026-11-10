package ink.icoding.wechat.article.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 主密钥缺失时必须 **fail-fast**（2026-11-10 工程复查 C-1）。
 *
 * <p>为什么这条值得钉死：改造前 {@code application.yaml} 给 {@code app.security.secret-key}
 * 一个众所周知的默认值，而 {@code SHA-256} 对空串也能算出合法 AES 密钥。于是「运维忘记注入
 * APP_SECRET_KEY」的表现是——<b>应用正常启动、功能全部正常</b>，只是库里的
 * {@code api_key_encrypted}（所有 LLM/图片 API Key）与微信 AppSecret 全部被一个可猜的密钥保护着，
 * 拿到库备份的人可离线解密。这类缺陷不会在测试里变红，只会在被利用时显形。
 *
 * <p>所以这里断言的不是「能加解密」，而是<b>拒绝启动</b>：宁可起不来，也不能带着弱密钥起来。
 * 若有人日后把这段校验删掉（或把默认值加回去），本用例立刻变红。
 */
class CryptoServiceSecretRequiredTest {

    @Test
    void blankSecretIsRejectedInsteadOfSilentlyUsed() {
        assertThatThrownBy(() -> new CryptoService(""))
                .as("空密钥必须拒绝启动：静默使用等于让全部密文失去保护")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_SECRET_KEY");
    }

    @Test
    void whitespaceOnlySecretIsRejected() {
        assertThatThrownBy(() -> new CryptoService("   "))
                .as("只有空白的密钥与空密钥等价，必须同样拒绝")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_SECRET_KEY");
    }

    @Test
    void nullSecretIsRejected() {
        assertThatThrownBy(() -> new CryptoService(null))
                .as("null 密钥必须拒绝（配置项缺失时的兜底路径）")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_SECRET_KEY");
    }
}
