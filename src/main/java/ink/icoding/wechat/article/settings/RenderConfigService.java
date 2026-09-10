package ink.icoding.wechat.article.settings;

import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.common.CryptoService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 排版渲染服务配置（单行表，实时读取热生效，模式沿用 LlmConfigService）。
 * 令牌优先级：环境变量 MARKFLOW_RENDER_TOKEN &gt; 设置页存储（与 APP_SECRET_KEY 等 Docker 部署惯例一致）。
 */
@Service
public class RenderConfigService {
    static final String ENV_TOKEN_NAME = "MARKFLOW_RENDER_TOKEN";
    private static final String DEFAULT_BASE_URL = "https://www.bx9y.com.cn";
    private static final int DEFAULT_SYNTAX_CACHE_TTL_SECONDS = 21600;

    private final RenderConfigMapper mapper;
    private final CryptoService cryptoService;
    private final CurrentUserService currentUserService;

    public RenderConfigService(RenderConfigMapper mapper, CryptoService cryptoService,
                               CurrentUserService currentUserService) {
        this.mapper = mapper;
        this.cryptoService = cryptoService;
        this.currentUserService = currentUserService;
    }

    public ConfigView get() {
        return view(required());
    }

    @Transactional
    public ConfigView update(UpdateRequest request) {
        RenderConfig config = required();
        config.setBaseUrl(normalizeBaseUrl(request.baseUrl()));
        config.setSiteBaseUrl(blankToNull(request.siteBaseUrl()) == null
                ? null : normalizeBaseUrl(request.siteBaseUrl()));
        config.setSyntaxCacheTtlSeconds(request.syntaxCacheTtlSeconds() == null
                ? DEFAULT_SYNTAX_CACHE_TTL_SECONDS : request.syntaxCacheTtlSeconds());
        config.setEnabled(Boolean.TRUE.equals(request.enabled()));
        // token 传空串/不传 = 不修改；仅非空白时覆盖（clearToken 显式清空设置页存储值）
        if (Boolean.TRUE.equals(request.clearToken())) config.setTokenEncrypted(null);
        if (request.token() != null && !request.token().isBlank()) {
            config.setTokenEncrypted(cryptoService.encrypt(request.token().trim()));
        }
        if (Boolean.TRUE.equals(config.getEnabled())
                && envToken().isBlank() && config.getTokenEncrypted() == null) {
            throw new BusinessException("启用排版渲染服务前必须配置渲染令牌");
        }
        config.setUpdatedBy(currentUserService.required().id());
        config.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(config);
        return view(config);
    }

    public RuntimeConfig runtime() {
        RenderConfig config = required();
        // 环境变量令牌优先于设置页存储值
        String envToken = envToken().trim();
        String token = !envToken.isBlank() ? envToken
                : config.getTokenEncrypted() == null ? null : cryptoService.decrypt(config.getTokenEncrypted());
        return new RuntimeConfig(Boolean.TRUE.equals(config.getEnabled()), config.getProvider(),
                normalizeBaseUrl(config.getBaseUrl()), token,
                blankToNull(config.getSiteBaseUrl()),
                config.getSyntaxCacheTtlSeconds() == null
                        ? DEFAULT_SYNTAX_CACHE_TTL_SECONDS : config.getSyntaxCacheTtlSeconds());
    }

    /** 语法缓存 TTL（秒），供渲染服务构建缓存。 */
    public int syntaxCacheTtlSeconds() {
        return runtime().syntaxCacheTtlSeconds();
    }

    private synchronized RenderConfig required() {
        RenderConfig config = mapper.current();
        if (config == null) {
            LocalDateTime now = LocalDateTime.now();
            config = new RenderConfig();
            config.setProvider("MARKFLOW");
            config.setBaseUrl(DEFAULT_BASE_URL);
            config.setEnabled(false);
            config.setSyntaxCacheTtlSeconds(DEFAULT_SYNTAX_CACHE_TTL_SECONDS);
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            mapper.insert(config);
        }
        return config;
    }

    private String envToken() {
        String value = System.getenv(ENV_TOKEN_NAME);
        return value == null ? "" : value;
    }

    private ConfigView view(RenderConfig config) {
        String envToken = envToken().trim();
        boolean envInjected = !envToken.isBlank();
        // 令牌掩码取解密后明文的后 4 位；环境变量注入时以「已由环境变量注入」代替掩码
        String storedToken = config.getTokenEncrypted() == null
                ? null : cryptoService.decrypt(config.getTokenEncrypted());
        String masked = storedToken == null || storedToken.isBlank()
                ? "未配置" : "••••••••" + storedToken.substring(storedToken.length() - 4);
        return new ConfigView(config.getProvider(), normalizeBaseUrl(config.getBaseUrl()),
                config.getTokenEncrypted() != null || envInjected,
                envInjected ? "已由环境变量注入" : masked, envInjected,
                config.getSiteBaseUrl(), config.getSyntaxCacheTtlSeconds(), config.getEnabled(),
                config.getUpdatedAt());
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Base URL 不能为空");
        }
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        if (!result.startsWith("http://") && !result.startsWith("https://")) {
            throw new BusinessException("Base URL 必须以 http:// 或 https:// 开头");
        }
        try {
            java.net.URI.create(result);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("Base URL 格式无效：" + result);
        }
        return result;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record UpdateRequest(@NotBlank String baseUrl, String token, Boolean clearToken,
                                String siteBaseUrl,
                                @Min(60) @Max(604800) Integer syntaxCacheTtlSeconds,
                                Boolean enabled) {}

    public record ConfigView(String provider, String baseUrl, boolean hasToken, String tokenMasked,
                             boolean envInjected, String siteBaseUrl, Integer syntaxCacheTtlSeconds,
                             Boolean enabled, LocalDateTime updatedAt) {}

    public record RuntimeConfig(boolean enabled, String provider, String baseUrl, String token,
                                String siteBaseUrl, int syntaxCacheTtlSeconds) {
        public boolean available() {
            return enabled && token != null && !token.isBlank();
        }
    }
}
