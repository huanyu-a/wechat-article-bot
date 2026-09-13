package ink.icoding.wechat.article.article;

import ink.icoding.wechat.article.account.WechatAccount;
import ink.icoding.wechat.article.account.WechatAccountService;
import ink.icoding.wechat.article.asset.Asset;
import ink.icoding.wechat.article.asset.AssetService;
import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.common.PageResult;
import ink.icoding.wechat.article.skill.LayoutEngine;
import ink.icoding.wechat.article.wechat.WechatClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ArticleService {
    private static final Logger log = LoggerFactory.getLogger(ArticleService.class);
    private static final String LOCAL_ASSET_ORIGIN = "https://wechat-article-local.invalid";
    /**
     * 本站素材的 {@code src}：**允许带绝对域名前缀**，落库时统一归一成相对路径 {@code /uploads/…}。
     *
     * <p>为什么必须容忍绝对域名：调度链路的正文不是编辑器提交的，而是渲染服务的产物——
     * {@code MarkFlowRenderService.absoluteImageUrls} 在送渲染前把 {@code /uploads/…} 绝对化成
     * {@code {site_base_url}/uploads/…}（上游对相对路径原样透传不补域名），这个绝对地址随渲染产物
     * 一起写回 {@code CONTENT_HTML}。此前这条正则只认相对形式，于是**每一篇 MARKFLOW 成稿都把站点域名
     * 烧进了正文**：实测 2026-09-13 全库 19 篇 MARKFLOW 稿（含当天 09:29 最新一篇）无一例外带着
     * {@code http://localhost:8081/uploads/…}（见 {@code target/probe/round8_dead_links.md}），
     * 换部署 / 换域名后编辑器与预览整片裂图；同库 PROMPT 稿（走同一条 clean）则是干净的相对路径。
     *
     * <p>判据把「域名」当成可选的，与 {@link #localStorageName} 的语义一致——那里同样只看路径是不是
     * {@code /uploads/<32位hex>.<ext>}、不看挂在哪个域名下（微信同步靠它把本站图换成 CDN 地址）。
     * 命中后仍走既有的哨兵域名往返：替换成 {@link #LOCAL_ASSET_ORIGIN} 是为了让 Jsoup 白名单
     * 不把相对 URL 当非法协议剥掉（PROMPT 引擎的 {@code addProtocols("img","src","http","https")}），
     * 清洗完再统一还原成相对路径。
     */
    private static final Pattern LOCAL_ASSET_SRC = Pattern.compile(
            "(?i)(\\bsrc\\s*=\\s*)([\"'])(?:https?://[^\"'/]+)?(/uploads/[a-f0-9]{32}\\.(?:jpg|png|gif|webp))\\2");
    private static final Pattern LOCAL_ASSET_NAME = Pattern.compile(
            "(?i)[a-f0-9]{32}\\.(?:jpg|png|gif|webp)");
    /** 渲染式排版正文里必须整段移除的元素（其余标签一律保留，见 cleanMarkflowBody）。 */
    private static final String MARKFLOW_FORBIDDEN_TAGS =
            "script, iframe, object, embed, link, meta, base, form, noscript, template";
    /** 值以 javascript:/vbscript: 开头的 URI 属性（区分大小写不敏感，容忍前导空白）。 */
    private static final Pattern UNSAFE_URI_SCHEME = Pattern.compile(
            "(?i)^\\s*(?:javascript|vbscript)\\s*:");
    private final ArticleMapper mapper;
    private final ArticleRevisionMapper revisionMapper;
    private final CurrentUserService currentUserService;
    private final AssetService assetService;
    private final WechatAccountService accountService;
    private final WechatClient wechatClient;
    private final ink.icoding.wechat.article.skill.MarkFlowRenderService markFlowRenderService;
    private final ink.icoding.wechat.article.skill.SkillBindingValidator skillBindingValidator;

    public ArticleService(ArticleMapper mapper, ArticleRevisionMapper revisionMapper,
                          CurrentUserService currentUserService, AssetService assetService,
                          WechatAccountService accountService, WechatClient wechatClient,
                          ink.icoding.wechat.article.skill.MarkFlowRenderService markFlowRenderService,
                          ink.icoding.wechat.article.skill.SkillBindingValidator skillBindingValidator) {
        this.mapper = mapper;
        this.revisionMapper = revisionMapper;
        this.currentUserService = currentUserService;
        this.assetService = assetService;
        this.accountService = accountService;
        this.wechatClient = wechatClient;
        this.markFlowRenderService = markFlowRenderService;
        this.skillBindingValidator = skillBindingValidator;
    }

    public PageResult<Article> list(Long accountId, String status, String keyword, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        return new PageResult<>(mapper.findPage(accountId, status, keyword, (safePage - 1) * safeSize, safeSize),
                mapper.countPage(accountId, status, keyword), safePage, safeSize);
    }

    public Article required(Long id) {
        Article article = mapper.findById(id);
        if (article == null) throw new BusinessException(HttpStatus.NOT_FOUND, "文章不存在");
        return article;
    }

    @Transactional
    public Article create(ArticleRequest request, String sourceType) {
        return createWithUser(request, sourceType, currentUserService.required().id());
    }

    @Transactional
    public Article createForTask(ArticleRequest request, Long userId) {
        return createWithUser(request, "SCHEDULED", userId);
    }

    private Article createWithUser(ArticleRequest request, String sourceType, Long userId) {
        skillBindingValidator.validate(request.skillIds());
        Article article = new Article();
        article.setAccountId(request.accountId());
        article.setTitle(request.title() == null || request.title().isBlank() ? "未命名文章" : request.title());
        article.setAuthor(request.author());
        article.setDigest(request.digest());
        article.setContentHtml(clean(request.contentHtml(), isMarkflowRequest(request)));
        article.setContentText(Jsoup.parse(article.getContentHtml()).text());
        applyLayout(request, article);
        article.setCoverAssetId(request.coverAssetId());
        article.setCoverUrl(resolveCover(request.coverAssetId(), request.coverUrl()));
        article.setSourceUrl(request.sourceUrl());
        article.setSourceType(sourceType == null ? "MANUAL" : sourceType);
        article.setSkillIds(WechatAccountService.skillIdsOrNull(request.skillIds()));
        article.setBusinessStatus("DRAFT");
        article.setWorkflowStatus("EDITING");
        article.setWechatStatus("NOT_SYNCED");
        article.setRevision(1);
        article.setDeleted(false);
        article.setCreatedBy(userId);
        article.setCreatedAt(LocalDateTime.now());
        article.setUpdatedAt(LocalDateTime.now());
        mapper.insert(article);
        snapshot(article, "MANUAL", "创建文章", article.getCreatedBy());
        return required(article.getId());
    }

    public boolean existsBySourceUrl(String sourceUrl) {
        return sourceUrl != null && !sourceUrl.isBlank() && mapper.countBySourceUrl(sourceUrl) > 0;
    }

    @Transactional
    public Article update(Long id, ArticleRequest request, String changeSource, String summary) {
        return updateWithUser(id, request, changeSource, summary, currentUserService.required().id());
    }

    @Transactional
    public Article updateByAi(Long id, ArticleRequest request, String summary, Long userId) {
        return updateWithUser(id, request, "AI", summary, userId);
    }

    private Article updateWithUser(Long id, ArticleRequest request, String changeSource, String summary, Long userId) {
        return updateWithUser(id, request, changeSource, summary, userId, true);
    }

    /**
     * @param reconcileLayout 正文文本未变时是否保留库中已有的渲染产物（编辑器往返防降级，见
     *                        {@link #reconcileRenderedLayout}）。**重渲染必须传 false**：它的新产物
     *                        即使与旧产物文本相同（只改了主题色/模板），也应当覆盖，而不是被还原成旧版式。
     */
    private Article updateWithUser(Long id, ArticleRequest request, String changeSource, String summary,
                                   Long userId, boolean reconcileLayout) {
        Article existing = required(id);
        if (request.revision() == null) throw new BusinessException("缺少文章版本号");
        skillBindingValidator.validate(request.skillIds());
        Article article = new Article();
        article.setId(id);
        article.setAccountId(request.accountId());
        article.setTitle(request.title() == null || request.title().isBlank() ? "未命名文章" : request.title());
        article.setAuthor(request.author());
        article.setDigest(request.digest());
        article.setContentHtml(clean(request.contentHtml(), isMarkflowRequest(request)));
        article.setContentText(Jsoup.parse(article.getContentHtml()).text());
        applyLayout(request, article);
        if (LayoutEngine.MARKFLOW.name().equals(article.getLayoutEngine())) {
            // 请求没带主题色时沿用库中值：编辑器保存会原样回传，但 AI 工具编辑、回滚等路径不带，
            // 不兜底就会把主题抹成 null——「打开一次编辑器」之后重排就整篇漂色。
            if (article.getThemeAccent() == null) article.setThemeAccent(existing.getThemeAccent());
            if (article.getThemeDark() == null) article.setThemeDark(existing.getThemeDark());
        }
        if (reconcileLayout) reconcileRenderedLayout(existing, article);
        article.setCoverAssetId(request.coverAssetId());
        article.setCoverUrl(resolveCover(request.coverAssetId(), request.coverUrl()));
        article.setSourceUrl(request.sourceUrl());
        article.setSkillIds(WechatAccountService.skillIdsOrNull(request.skillIds()));
        if (sameEditableContent(existing, article)) {
            return existing;
        }
        if (mapper.updateContent(article, request.revision()) == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "文章已被其他操作修改，请刷新后重试");
        }
        Article updated = required(id);
        snapshot(updated, changeSource == null ? "MANUAL" : changeSource,
                summary == null ? "编辑文章" : summary, userId);
        return updated;
    }

    public List<ArticleRevision> revisions(Long id) {
        required(id);
        return revisionMapper.findRevisions(id);
    }

    /**
     * 回滚到指定版本：恢复该版本快照里的**全部可编辑状态**——标题、摘要、正文、作者、来源 URL、
     * 排版引擎与 Markdown 源文（此前只取标题/摘要/正文，作者与来源 URL 保留当前值，
     * 且 article_revision 没有引擎/Markdown 两列，回滚后 MARKFLOW 文章拿不回源文，属「部分合并回滚」）。
     *
     * <p>边界：封面（coverAssetId/coverUrl）与技能绑定不随版本回滚——它们是当前编辑决策与共享素材，
     * 不是某次正文修订的一部分；账号与并发版本号同理取自当前行。
     *
     * <p>升级前落库的旧版本没有 author/sourceUrl/layoutEngine/contentMarkdown 四列（值为 null），
     * 此时保留当前值而不是把字段抹成 null——「恢复不了」不应表现为「丢失数据」。
     */
    @Transactional
    public Article rollback(Long id, Integer revision) {
        Article current = required(id);
        ArticleRevision target = revisionMapper.findRevision(id, revision);
        if (target == null) throw new BusinessException("指定版本不存在");
        ArticleRequest request = new ArticleRequest(current.getAccountId(), target.getTitle(),
                restoreOr(target.getAuthor(), current.getAuthor()), target.getDigest(), target.getContentHtml(),
                current.getCoverAssetId(), current.getCoverUrl(),
                restoreOr(target.getSourceUrl(), current.getSourceUrl()), current.getRevision(),
                ink.icoding.wechat.article.account.WechatAccountService.parseSkillIds(current.getSkillIds()),
                restoreOr(target.getLayoutEngine(), current.getLayoutEngine()),
                restoreOr(target.getContentMarkdown(), current.getContentMarkdown()),
                // 旧版本快照没有这两列（值为 null）时保留当前值，与上面同一条规则
                restoreOr(target.getThemeAccent(), current.getThemeAccent()),
                restoreOr(target.getThemeDark(), current.getThemeDark()));
        return update(id, request, "ROLLBACK", "回滚到版本 " + revision);
    }

    /** 目标版本有记录则用目标值；为 null（升级前的旧版本快照）时保留当前值。 */
    private static String restoreOr(String fromRevision, String current) {
        return fromRevision != null ? fromRevision : current;
    }

    /**
     * 用留存的 Markdown 源文重新渲染并覆盖正文（I4）。
     *
     * <p>解决的问题：{@code content_markdown} 已随文章落库，但此前只有智能体交付前的自动渲染会写它，
     * 用户换了排版模板/主题色后，已有 MARKFLOW 文章无法批量重排，只能重跑一次智能体。
     *
     * <p>唯一事实来源是 {@code content_markdown}（编辑器只编辑渲染产物 HTML，不能反推回 Markdown）；
     * 重渲染会像一次普通编辑一样生成新 revision，可回滚。
     *
     * <p>主题色缺省取文章留存的那一组（{@code themeAccent/themeDark}），而不是传 null 让渲染服务按默认色渲染——
     * 后者会让「换个模板重排一下」变成「整篇文章换了个配色」。
     *
     * @param accent 可选主题色覆盖；为 null 时用文章留存的主题色（也没有留存时才交给渲染服务决定）
     * @param dark   可选暗色覆盖；为 null 时同上
     */
    @Transactional
    public Article rerender(Long id, String accent, String dark) {
        Article current = required(id);
        if (!LayoutEngine.MARKFLOW.name().equalsIgnoreCase(current.getLayoutEngine())) {
            throw new BusinessException("该文章不是渲染式排版（MARKFLOW），没有可重新渲染的 Markdown 源文");
        }
        if (current.getContentMarkdown() == null || current.getContentMarkdown().isBlank()) {
            throw new BusinessException("该文章没有留存 Markdown 源文，无法重新渲染");
        }
        String effectiveAccent = blankToNull(accent) == null ? current.getThemeAccent() : accent.trim();
        String effectiveDark = blankToNull(dark) == null ? current.getThemeDark() : dark.trim();
        ink.icoding.wechat.article.skill.MarkFlowRenderService.RenderResult rendered =
                markFlowRenderService.render(current.getContentMarkdown(), effectiveAccent, effectiveDark);
        String digest = current.getDigest() == null || current.getDigest().isBlank()
                ? rendered.summary() : current.getDigest();
        ArticleRequest request = new ArticleRequest(current.getAccountId(), current.getTitle(),
                current.getAuthor(), digest, rendered.html(), current.getCoverAssetId(), current.getCoverUrl(),
                current.getSourceUrl(), current.getRevision(),
                WechatAccountService.parseSkillIds(current.getSkillIds()),
                current.getLayoutEngine(), current.getContentMarkdown(),
                accentOrFallback(rendered.themeAccent(), effectiveAccent),
                accentOrFallback(rendered.themeDark(), effectiveDark));
        return updateWithUser(id, request, "RERENDER", rerenderSummary(id, rendered.warnings()),
                currentUserService.required().id(), false);
    }

    /**
     * 重渲染的版本说明：有降级时把条数与第一条原因写进去。
     *
     * <p>降级（组件没被识别、内容被渲染器丢掉）以前只落在服务端日志里，用户换完模板只看到版式不对、
     * 查不到为什么。写进版本说明至少能在版本历史里看到「这次重排有几处没渲染出来」。
     */
    private static String rerenderSummary(Long id, List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) return "用留存 Markdown 重新渲染排版";
        log.warn("文章 {} 重新渲染出现 {} 处版式降级：{}", id, warnings.size(), String.join("；", warnings));
        return "用留存 Markdown 重新渲染排版（" + warnings.size() + " 处版式降级：" + warnings.get(0) + "）";
    }

    /** 落库主题色取渲染服务回报的**实际生效值**；上游没回该字段时退回我们请求的值（避免把已有主题写没）。 */
    private static String accentOrFallback(String reported, String requested) {
        String value = blankToNull(reported);
        return value == null ? blankToNull(requested) : value;
    }

    /** 空白串与 null 同义（前端把「没选主题」序列化成空串而不是省略字段）。 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /*
     * Keep WeChat material uploads outside a database transaction. A remote material upload cannot
     * be rolled back; retaining its media id prevents a later draft failure from creating duplicate
     * permanent cover materials on retry.
     */
    public Article syncDraft(Long id) {
        return syncDraft(id, progress -> {});
    }

    public Article syncDraft(Long id, WechatProgressListener progress) {
        progress.onProgress(new WechatProgress("VALIDATING", "正在检查文章和公众号配置", 5));
        Article article = required(id);
        if (article.getAccountId() == null) throw new BusinessException("请先选择目标公众号");
        if (article.getCoverAssetId() == null) throw new BusinessException("同步微信草稿前必须设置封面图片");
        progress.onProgress(new WechatProgress("COVER", "正在准备微信封面素材", 15));
        String thumbMediaId = assetService.ensureWechatThumb(article.getCoverAssetId(), article.getAccountId());
        progress.onProgress(new WechatProgress("CONTENT", "正在处理正文图片和微信排版", 35));
        String wechatContent = prepareWechatContent(article, progress);
        Map<String, Object> payload = wechatPayload(article, thumbMediaId, wechatContent);
        if (article.getWechatMediaId() == null || article.getWechatMediaId().isBlank()) {
            progress.onProgress(new WechatProgress("DRAFT", "正在创建微信草稿", 72));
            String mediaId = wechatClient.addDraft(article.getAccountId(), payload);
            mapper.markWechatDraft(id, mediaId);
        } else {
            progress.onProgress(new WechatProgress("DRAFT", "正在更新微信草稿", 72));
            wechatClient.updateDraft(article.getAccountId(), article.getWechatMediaId(), payload);
            mapper.markWechatDraft(id, article.getWechatMediaId());
        }
        progress.onProgress(new WechatProgress("SAVING", "微信已响应，正在保存同步状态", 94));
        Article updated = required(id);
        progress.onProgress(new WechatProgress("DONE", "文章已同步到微信草稿箱", 100));
        return updated;
    }

    /*
     * Do not wrap draft creation and publish submission in one database transaction. They are two
     * irreversible remote operations: if publishing is unauthorized or fails, the successfully
     * created WeChat draft and its media id must remain persisted for manual publishing/retry.
     */
    public Article publish(Long id) {
        return publish(id, progress -> {});
    }

    public Article publish(Long id, WechatProgressListener progress) {
        progress.onProgress(new WechatProgress("VALIDATING", "正在检查文章发布状态", 5));
        Article article = required(id);
        if (article.getAccountId() == null) throw new BusinessException("请先选择目标公众号");
        if (article.getWechatMediaId() == null || article.getWechatMediaId().isBlank()) {
            progress.onProgress(new WechatProgress("DRAFT", "尚无微信草稿，将先完成草稿同步", 10));
            article = syncDraft(id, step -> progress.onProgress(new WechatProgress(
                    step.stage(), step.message(), Math.min(70, Math.max(10, step.percent() * 7 / 10)))));
        } else {
            progress.onProgress(new WechatProgress("DRAFT", "已找到同步的微信草稿", 65));
        }
        progress.onProgress(new WechatProgress("PUBLISH", "正在向微信提交发布任务", 80));
        String publishId = wechatClient.publish(article.getAccountId(), article.getWechatMediaId());
        progress.onProgress(new WechatProgress("SAVING", "微信已受理，正在保存发布状态", 95));
        mapper.markPublishing(id, publishId);
        Article updated = required(id);
        progress.onProgress(new WechatProgress("DONE", "发布任务已提交给微信", 100));
        return updated;
    }

    @Transactional
    public PublishStatus refreshPublishStatus(Long id) {
        Article article = required(id);
        if (article.getWechatPublishId() == null) throw new BusinessException("文章没有进行中的发布任务");
        Map<String, Object> response = wechatClient.publishStatus(article.getAccountId(), article.getWechatPublishId());
        int status = ((Number) response.getOrDefault("publish_status", 1)).intValue();
        String articleId = response.get("article_id") == null ? null : String.valueOf(response.get("article_id"));
        if (status == 0) {
            mapper.updatePublishResult(id, "PUBLISHED", "READY", "PUBLISHED", articleId, LocalDateTime.now());
        } else if (status >= 2) {
            mapper.updatePublishResult(id, "FAILED", "FAILED", "DRAFT", null, null);
        }
        return new PublishStatus(status, publishMessage(status), required(id));
    }

    public void delete(Long id) {
        required(id);
        mapper.softDelete(id);
    }

    private Map<String, Object> wechatPayload(Article article, String thumbMediaId, String wechatContent) {
        WechatAccount account = accountService.required(article.getAccountId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", article.getTitle());
        payload.put("author", blankToDefault(article.getAuthor(), account.getDefaultAuthor()));
        payload.put("digest", article.getDigest() == null ? "" : article.getDigest());
        payload.put("content", wechatContent);
        payload.put("content_source_url", article.getSourceUrl() == null ? "" : article.getSourceUrl());
        payload.put("thumb_media_id", thumbMediaId);
        payload.put("need_open_comment", 0);
        payload.put("only_fans_can_comment", 0);
        return payload;
    }

    String prepareWechatContent(Article article) {
        return prepareWechatContent(article, progress -> {});
    }

    private String prepareWechatContent(Article article, WechatProgressListener progress) {
        Document document = Jsoup.parseBodyFragment(article.getContentHtml() == null ? "" : article.getContentHtml());
        document.outputSettings().prettyPrint(false);
        Map<String, String> uploadedUrls = new HashMap<>();
        List<Element> localImages = document.select("img[src]").stream()
                .filter(image -> localStorageName(image.attr("src")) != null).toList();
        for (int index = 0; index < localImages.size(); index++) {
            Element image = localImages.get(index);
            String storageName = localStorageName(image.attr("src"));
            String wechatUrl = uploadedUrls.computeIfAbsent(storageName,
                    name -> assetService.ensureWechatContentImage(name, article.getAccountId()));
            image.attr("src", wechatUrl);
            int completed = index + 1;
            progress.onProgress(new WechatProgress("CONTENT",
                    "正在处理正文图片 " + completed + "/" + localImages.size(),
                    35 + completed * 30 / localImages.size()));
        }
        if (localImages.isEmpty()) {
            progress.onProgress(new WechatProgress("CONTENT", "正文排版已准备完成", 65));
        }
        return ArticleContentPolicy.formatForWechat(document.body().html(), layoutEngineOf(article));
    }

    /** 文章排版引擎（缺省/无法识别时按 PROMPT 处理，与落库语义一致）。 */
    private static LayoutEngine layoutEngineOf(Article article) {
        String value = article == null ? null : article.getLayoutEngine();
        return LayoutEngine.MARKFLOW.name().equalsIgnoreCase(value == null ? "" : value.trim())
                ? LayoutEngine.MARKFLOW : LayoutEngine.PROMPT;
    }

    private String localStorageName(String source) {
        try {
            String path = URI.create(source).getPath();
            if (path == null || !path.startsWith("/uploads/")) return null;
            String storageName = path.substring("/uploads/".length());
            return LOCAL_ASSET_NAME.matcher(storageName).matches() ? storageName : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void snapshot(Article article, String source, String summary, Long userId) {
        ArticleRevision revision = new ArticleRevision();
        revision.setArticleId(article.getId());
        revision.setRevision(article.getRevision());
        revision.setTitle(article.getTitle());
        revision.setAuthor(article.getAuthor());
        revision.setDigest(article.getDigest());
        revision.setContentHtml(article.getContentHtml());
        revision.setSourceUrl(article.getSourceUrl());
        revision.setLayoutEngine(article.getLayoutEngine());
        revision.setContentMarkdown(article.getContentMarkdown());
        revision.setThemeAccent(article.getThemeAccent());
        revision.setThemeDark(article.getThemeDark());
        revision.setChangeSource(source);
        revision.setChangeSummary(summary);
        revision.setCreatedBy(userId);
        revision.setCreatedAt(LocalDateTime.now());
        revisionMapper.insert(revision);
    }

    private boolean sameEditableContent(Article left, Article right) {
        return Objects.equals(left.getAccountId(), right.getAccountId())
                && Objects.equals(left.getTitle(), right.getTitle())
                && Objects.equals(left.getAuthor(), right.getAuthor())
                && Objects.equals(left.getDigest(), right.getDigest())
                && Objects.equals(left.getContentHtml(), right.getContentHtml())
                && Objects.equals(left.getLayoutEngine(), right.getLayoutEngine())
                && Objects.equals(left.getContentMarkdown(), right.getContentMarkdown())
                && Objects.equals(left.getThemeAccent(), right.getThemeAccent())
                && Objects.equals(left.getThemeDark(), right.getThemeDark())
                && Objects.equals(left.getCoverAssetId(), right.getCoverAssetId())
                && Objects.equals(left.getCoverUrl(), right.getCoverUrl())
                && Objects.equals(left.getSourceUrl(), right.getSourceUrl())
                && Objects.equals(left.getSkillIds(), right.getSkillIds());
    }

    /**
     * MARKFLOW 文章在编辑器里是「HTML 所见即所得」：编辑器加载渲染产物后重新序列化，
     * 正文多出 &lt;p&gt;/&lt;strong&gt;、内联样式被重排、表格被补 colgroup——保存一次就会把公众号版式
     * 降级成普通 HTML，而渲染产物无法反推回 Markdown。于是分两种情况：
     * 正文文本未变化（只改标题/摘要）→ 保留原有渲染产物与 Markdown 源文，避免「改个标题就丢版式」；
     * 正文文本已变化 → 尊重编辑器结果落库，同时丢弃已与正文不符的 Markdown 源文
     * （留着会让后续重排悄悄覆盖用户刚做的编辑），引擎字段保留为历史留痕。
     * 例外见「重排提交」判定：调用方显式提交了一份与库中不同的 Markdown，那它才是新产物的源文。
     */
    private void reconcileRenderedLayout(Article existing, Article incoming) {
        if (!LayoutEngine.MARKFLOW.name().equalsIgnoreCase(
                existing.getLayoutEngine() == null ? "" : existing.getLayoutEngine())) return;
        if (!Objects.equals(Jsoup.parse(existing.getContentHtml() == null ? "" : existing.getContentHtml()).text(),
                Jsoup.parse(incoming.getContentHtml() == null ? "" : incoming.getContentHtml()).text())) {
            // 「重排提交」判定：手动编辑（前端回显 article 原值）与 AI 局部编辑（save_article_draft 传原文）
            // 都会把库中已有的 Markdown 原样回传，只有「按新 Markdown 重新渲染后覆盖正文」
            // 才会带来一份不同的 Markdown——此时必须留存，否则重排一次就丢掉源文、再也无法二次调整。
            if (Objects.equals(existing.getContentMarkdown(), incoming.getContentMarkdown())) {
                incoming.setContentMarkdown(null);
            }
            return;
        }
        incoming.setContentHtml(existing.getContentHtml());
        incoming.setContentText(existing.getContentText());
        incoming.setContentMarkdown(existing.getContentMarkdown());
        incoming.setLayoutEngine(existing.getLayoutEngine());
    }

    /**
     * 落库排版元数据。MARKFLOW 文章必须同时留存 Markdown 源文：编辑器只编辑渲染产物 HTML，
     * 覆盖后无法反推回 Markdown；没有源文就只能放弃重排或降级成普通 HTML（方案 5.10.4）。
     * 引擎一旦为 MARKFLOW，后续普通保存（请求不带引擎字段）不得把它抹回 PROMPT。
     */
    private void applyLayout(ArticleRequest request, Article article) {
        boolean markflow = isMarkflowRequest(request);
        String markdown = request.contentMarkdown();
        if (!markflow && (markdown == null || markdown.isBlank())) {
            article.setLayoutEngine(LayoutEngine.PROMPT.name());
            article.setContentMarkdown(null);
            article.setThemeAccent(null);
            article.setThemeDark(null);
            return;
        }
        article.setLayoutEngine(LayoutEngine.MARKFLOW.name());
        article.setContentMarkdown(markdown);
        // 主题色只在请求显式带来时覆盖：编辑器保存会原样回传库中值，普通保存也不该把它抹成 null，
        // 否则「打开一次编辑器」就足以让后续重排丢主题（与 layoutEngine 的保留语义一致）。
        if (request.themeAccent() != null) article.setThemeAccent(blankToNull(request.themeAccent()));
        if (request.themeDark() != null) article.setThemeDark(blankToNull(request.themeDark()));
    }

    /** 请求是否声明了渲染式排版（决定是否保留 MarkFlow 配套 &lt;style&gt; 与公式组件的 aria 标记）。 */
    private static boolean isMarkflowRequest(ArticleRequest request) {
        return request != null && LayoutEngine.MARKFLOW.name().equalsIgnoreCase(
                request.layoutEngine() == null ? "" : request.layoutEngine().trim());
    }

    private String resolveCover(Long assetId, String coverUrl) {
        if (assetId == null) return coverUrl;
        Asset asset = assetService.required(assetId);
        return asset.getPublicUrl();
    }

    /**
     * 正文清洗。PROMPT 引擎沿用 Jsoup 白名单（模型手写内联样式 HTML，标签集本就有限）；
     * MARKFLOW 引擎改用黑名单，原因见 {@link #cleanMarkflowBody}。
     */
    private String clean(String html, boolean markflow) {
        if (html == null || html.isBlank()) return "<p></p>";
        Matcher matcher = LOCAL_ASSET_SRC.matcher(html);
        StringBuffer prepared = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) + matcher.group(2) + LOCAL_ASSET_ORIGIN
                    + matcher.group(3) + matcher.group(2);
            matcher.appendReplacement(prepared, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(prepared);
        String cleaned = markflow ? cleanMarkflowBody(prepared.toString()) : cleanPromptBody(prepared.toString());
        return cleaned.replace(LOCAL_ASSET_ORIGIN + "/uploads/", "/uploads/");
    }

    /** 指令式排版的正文清洗：Jsoup 白名单（不认识的标签会被解包，仅保留文字）。 */
    private static String cleanPromptBody(String html) {
        Safelist safelist = Safelist.relaxed()
                .addTags("section", "figure", "figcaption", "hr", "div")
                // aria-* / role：上游组件依赖 aria-hidden 标记；此前被 Safelist.relaxed 默认丢弃（I3）
                .addAttributes(":all", "style", "class", "data-id", "data-render-id",
                        "aria-hidden", "aria-label", "role")
                .addAttributes("img", "width", "height")
                .addProtocols("img", "src", "http", "https");
        return Jsoup.clean(html, "", safelist,
                new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false));
    }

    /**
     * 渲染式排版的正文清洗：<b>黑名单语义</b>，不认识的标签一律原样保留。
     *
     * <p>为什么不能继续用白名单：白名单对不认识的标签是「解包」——标签和它的内联样式一起消失、只留文字，
     * 于是渲染服务新加的组件（MathML/KaTeX 的 {@code <math>/<semantics>/<annotation>}、图表里的
     * {@code <svg>}、{@code <mark>/<kbd>/<del>} 等语义标签）在落库时就被拆掉，公众号侧看到的是「素版」
     * ——用户报的「精排版式没有 100% 复刻 MarkFlow 渲染能力」正是这一类。这与
     * {@link ink.icoding.wechat.article.skill.MarkFlowRenderService#sanitizeHtml} 早就定下的
     * 「黑名单语义而非严格标签白名单——上游新增组件标签不被误杀」是同一个判断，两处必须一致。
     *
     * <p>安全性：渲染服务侧已先做过一轮黑名单剥离，这里的第二道只针对「编辑器/接口直接提交 HTML」的路径，
     * 因此危险元素、事件属性与 javascript: URI 一律照旧移除，只是不再对**未知但无害**的标签动手。
     */
    private static String cleanMarkflowBody(String html) {
        org.jsoup.nodes.Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        document.select(MARKFLOW_FORBIDDEN_TAGS).remove();
        for (org.jsoup.nodes.Element element : document.getAllElements()) {
            for (org.jsoup.nodes.Attribute attribute : element.attributes().asList()) {
                String key = attribute.getKey();
                String lower = key.toLowerCase(java.util.Locale.ROOT);
                if (lower.startsWith("on") || lower.equals("srcdoc") || lower.equals("formaction")) {
                    element.removeAttr(key);
                    continue;
                }
                boolean uriAttribute = lower.equals("href") || lower.equals("src") || lower.endsWith(":href");
                if (uriAttribute && UNSAFE_URI_SCHEME.matcher(attribute.getValue()).find()) {
                    element.attr(key, "#");
                }
            }
        }
        return document.body().html();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? (fallback == null ? "" : fallback) : value;
    }

    private String publishMessage(int status) {
        return switch (status) {
            case 0 -> "发布成功";
            case 1 -> "发布中";
            case 2 -> "原创校验失败";
            case 3 -> "发布失败";
            case 4 -> "平台审核未通过";
            case 5 -> "发布成功后被用户删除";
            case 6 -> "发布成功后被平台封禁";
            default -> "未知状态 " + status;
        };
    }

    public record ArticleRequest(Long accountId, String title, String author, String digest, String contentHtml,
                                 Long coverAssetId, String coverUrl, String sourceUrl, Integer revision,
                                 java.util.List<Long> skillIds, String layoutEngine, String contentMarkdown,
                                 String themeAccent, String themeDark) {}
    public record PublishStatus(int code, String message, Article article) {}
    public record WechatProgress(String stage, String message, int percent) {}

    @FunctionalInterface
    public interface WechatProgressListener {
        void onProgress(WechatProgress progress);
    }
}
