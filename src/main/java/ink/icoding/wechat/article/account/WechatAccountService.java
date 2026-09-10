package ink.icoding.wechat.article.account;

import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.common.CryptoService;
import ink.icoding.wechat.article.wechat.WechatClient;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;

@Service
public class WechatAccountService {
    private static final java.util.Set<String> ACCOUNT_TYPES = java.util.Set.of("SERVICE", "SUBSCRIPTION");
    private static final java.util.Set<String> STATUSES = java.util.Set.of("ACTIVE", "DISABLED");
    private final WechatAccountMapper mapper;
    private final CryptoService cryptoService;
    private final WechatClient wechatClient;

    public WechatAccountService(WechatAccountMapper mapper, CryptoService cryptoService, WechatClient wechatClient) {
        this.mapper = mapper;
        this.cryptoService = cryptoService;
        this.wechatClient = wechatClient;
    }

    public List<AccountView> list() {
        return mapper.findAll().stream().map(this::view).toList();
    }

    public AccountView get(Long id) {
        return view(required(id));
    }

    @Transactional
    public AccountView create(AccountRequest request) {
        if (request.appSecret() == null || request.appSecret().isBlank()) {
            throw new BusinessException("AppSecret 不能为空");
        }
        WechatAccount account = new WechatAccount();
        apply(account, request);
        account.setAppSecretEncrypted(cryptoService.encrypt(request.appSecret().trim()));
        // 未显式传状态时默认启用；显式传入的值（已过白名单校验）必须被尊重，
        // 否则接口对 status 的语义是「收下但忽略」，与 UI/文档不一致。
        if (account.getStatus() == null) account.setStatus("ACTIVE");
        account.setConnectionStatus("UNCHECKED");
        account.setCapabilities("DRAFT,PUBLISH,MATERIAL,FOLLOWER");
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        mapper.insert(account);
        return view(required(account.getId()));
    }

    @Transactional
    public AccountView update(Long id, AccountRequest request) {
        WechatAccount account = required(id);
        apply(account, request);
        if (request.appSecret() != null && !request.appSecret().isBlank()) {
            account.setAppSecretEncrypted(cryptoService.encrypt(request.appSecret().trim()));
        }
        mapper.update(account);
        return view(required(id));
    }

    public AccountView test(Long id) {
        wechatClient.forceRefreshToken(id);
        return view(required(id));
    }

    @Transactional
    public void delete(Long id) {
        required(id);
        mapper.delete(id);
    }

    public WechatAccount required(Long id) {
        WechatAccount account = mapper.findById(id);
        if (account == null) throw new BusinessException("公众号不存在");
        return account;
    }

    private void apply(WechatAccount account, AccountRequest request) {
        account.setName(request.name().trim());
        account.setAppId(request.appId().trim());
        account.setOriginalId(request.originalId());
        String accountType = request.accountType() == null ? "SERVICE" : request.accountType().strip();
        if (!ACCOUNT_TYPES.contains(accountType)) {
            throw new BusinessException("不支持的账号类型：" + request.accountType());
        }
        account.setAccountType(accountType);
        account.setVerified(Boolean.TRUE.equals(request.verified()));
        account.setAvatarUrl(request.avatarUrl());
        account.setDefaultAuthor(request.defaultAuthor());
        account.setDefaultStyle(request.defaultStyle());
        account.setSkillIds(skillIdsOrNull(request.skillIds()));
        if (request.status() != null && !request.status().isBlank()) {
            String status = request.status().strip();
            // status 参与 WechatClient 的「公众号已停用」判断，任意值会静默停用账号，必须白名单校验
            if (!STATUSES.contains(status)) throw new BusinessException("不支持的公众号状态：" + request.status());
            account.setStatus(status);
        }
    }

    public static String skillIdsOrNull(java.util.List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) return null;
        String joined = skillIds.stream().filter(java.util.Objects::nonNull).map(String::valueOf)
                .reduce((a, b) -> a + "," + b).orElse("");
        return joined.isBlank() ? null : joined;
    }

    private AccountView view(WechatAccount account) {
        return new AccountView(account.getId(), account.getName(), account.getAppId(), account.getOriginalId(),
                account.getAccountType(), account.getVerified(), account.getAvatarUrl(), account.getDefaultAuthor(),
                account.getDefaultStyle(), parseSkillIds(account.getSkillIds()), account.getStatus(),
                account.getConnectionStatus(), account.getCapabilities(),
                account.getTokenExpiresAt(), account.getLastCheckedAt(), account.getCreatedAt(), account.getUpdatedAt());
    }

    /** DB 逗号分隔字符串 → List&lt;Long&gt;（脏数据容错：非法段静默丢弃）。 */
    public static java.util.List<Long> parseSkillIds(String skillIds) {
        if (skillIds == null || skillIds.isBlank()) return java.util.List.of();
        java.util.List<Long> result = new java.util.ArrayList<>();
        for (String part : skillIds.split(",")) {
            try {
                result.add(Long.valueOf(part.strip()));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    public record AccountRequest(@NotBlank String name, @NotBlank String appId, String appSecret, String originalId,
                                 String accountType, Boolean verified, String avatarUrl, String defaultAuthor,
                                 String defaultStyle, java.util.List<Long> skillIds, String status) {}

    public record AccountView(Long id, String name, String appId, String originalId, String accountType,
                              Boolean verified, String avatarUrl, String defaultAuthor, String defaultStyle,
                              java.util.List<Long> skillIds, String status, String connectionStatus, String capabilities,
                              java.time.LocalDateTime tokenExpiresAt, java.time.LocalDateTime lastCheckedAt,
                              java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt) {}
}
