package ink.icoding.wechat.article.article;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 把文章正文的长文本列从 {@code TEXT} 扩到 {@code MEDIUMTEXT}（一次性、幂等）。
 *
 * <p>为什么需要显式 DDL 而不是靠实体注解自动同步：本仓库的业务表结构由 smart-mybatis 在启动时同步，
 * 它对已存在列的处理**取决于实体声明**（见下方「字段同名共享声明」），而存量库里这些列早已是 TEXT。
 * 显式 DDL 才是确定的那条路，实体注解则负责把「声明」对齐到同一个方向（避免同步逻辑反向收窄）。
 *
 * <p>为什么是 MEDIUMTEXT：TEXT 上限 65535 **字节**，中文 UTF-8 占 3 字节，约合 2 万字正文。
 * 实测全库最大文章已 53253 字节，run#98（COORDINATOR，79 次工具调用、1655 秒）产出的富文本越过上限，
 * 报 {@code Data too long for column 'CONTENT_HTML'}，整轮作废。MEDIUMTEXT 上限 16MB，余量充足。
 *
 * <h2>为什么是 5 个列、2 张表</h2>
 * 同一份正文会落在两处，漏掉任何一处都只是把报错换个列名：
 * <ol>
 *   <li>{@code ARTICLE.CONTENT_HTML} 与 {@code ARTICLE.CONTENT_TEXT}：两者在**同一条 INSERT** 上
 *       （落库前用 Jsoup 从 HTML 抽纯文本），只扩前者只会把报错从 {@code CONTENT_HTML} 换成
 *       {@code CONTENT_TEXT}——这是扩容用例当场跑出来的，不是推断。</li>
 *   <li>{@code ARTICLE_REVISION.CONTENT_HTML}：{@code ArticleService.createWithUser} 建完文章立刻调
 *       {@code snapshot()}，把**同一份 HTML** 写进版本表。实测该列已存 62118 字节（占 TEXT 上限 95%），
 *       所以只扩 ARTICLE 等于把 run#98 的失败从主表挪到版本表，长文照样整轮作废。</li>
 *   <li>两个 {@code CONTENT_MARKDOWN}：与 HTML 同源同写（{@code snapshot()} 一次写两表）。
 *       实测最大 14677 字节虽未越界，但 Markdown 与 HTML 同向增长（当前约为 HTML 的 28%），
 *       HTML 一旦放到 250KB 量级它就会跟着越界——这正是本次要根治的那类失败，不留下同族隐患。</li>
 * </ol>
 *
 * <p>为什么是「一起」而不是逐列按需：{@code contentHtml} / {@code contentMarkdown} 这两个字段名
 * 在 Article 与 ArticleRevision 里同名，而 smart-mybatis 的列声明缓存按**字段名**共享（见下节），
 * 于是「一宽一窄」的声明必然有一个被另一个覆盖。全列统一成 MEDIUMTEXT 才能让结果与初始化顺序无关。
 *
 * <h2>字段同名共享声明（本次扩容暴露的库行为）</h2>
 * {@code MapperUtil.getColumnDeclaration(Field)} 的缓存键是 {@code field.getName()} **单键**，
 * 不含类名。因此 {@code Article.contentHtml} 与 {@code ArticleRevision.contentHtml} 共用一份列声明，
 * 谁先初始化谁说了算。实测证据：把 {@code Article.contentHtml} 声明成 MEDIUMTEXT 后，
 * 只启动上下文（不写任何数据）就会让 {@code ARTICLE_REVISION.CONTENT_HTML} 自己变成 mediumtext；
 * 另一处佐证是 {@code Asset.sourceUrl} 声明 2000、库里却是 varchar(1000)（被 Article 的同名字段收窄）。
 *
 * <p>这条库行为的后果是：实体声明与真实列宽**必须同向**。若两边相反（声明 TEXT、列已 MEDIUMTEXT），
 * 同步逻辑会发出 {@code MODIFY COLUMN ... TEXT} 把列**收窄回去**，长文再次写不进；
 * 而收窄时若已有超长行，MySQL 会直接报错，风险从「落库失败」升级为「启动期 DDL 失败」。
 * 所以本 runner 与实体注解一起改：DDL 负责存量库，注解负责让声明不再反向。
 *
 * <p>幂等与安全：先查 {@code information_schema} 的当前类型，已经够宽就什么都不做；
 * 失败只告警不影响启动（与 {@link ink.icoding.wechat.article.agent.LlmProfileMigrationRunner} 同构）。
 * 用 {@code MEDIUMTEXT} 而非 {@code LONGTEXT}：够用即可，避免无谓放大行外存储的分配。
 */
@Component
@Order(25)
public class ArticleLongTextColumnRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ArticleLongTextColumnRunner.class);

    /** 目标列类型。与实体注解里的 {@code @TableField(columnType=...)} 必须一致。 */
    public static final String TARGET_TYPE = "MEDIUMTEXT";
    /** 已经够用的类型：出现这些就跳过。 */
    private static final Set<String> SUFFICIENT_TYPES = Set.of("MEDIUMTEXT", "LONGTEXT");

    /**
     * 需要扩容的（表, 列）。表名写实体注解里的形态，大小写以库里为准（见 Mapper 的说明）。
     *
     * <p>{@code public} 是给迁移用例用的——它要逐列把类型改回 TEXT 再跑迁移，
     * 清单散在测试里就成了第二份真相，改一处忘一处就会漏验。
     */
    public static final List<Target> TARGETS = List.of(
            new Target("ARTICLE", "CONTENT_HTML"),
            new Target("ARTICLE", "CONTENT_TEXT"),
            new Target("ARTICLE", "CONTENT_MARKDOWN"),
            new Target("ARTICLE_REVISION", "CONTENT_HTML"),
            new Target("ARTICLE_REVISION", "CONTENT_MARKDOWN"));

    /** 待扩容的一列。 */
    public record Target(String table, String column) {
        @Override
        public String toString() {
            return table + "." + column;
        }
    }

    private final ArticleLongTextColumnMapper mapper;

    public ArticleLongTextColumnRunner(ArticleLongTextColumnMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Target target : TARGETS) {
            widen(target);
        }
    }

    private void widen(Target target) {
        try {
            ArticleLongTextColumnMapper.ColumnState state = mapper.columnState(target.table(), target.column());
            if (state == null) {
                // 表或列还不存在：首次启动时 smart-mybatis 会按实体注解建表，建出来就是 MEDIUMTEXT。
                log.info("{} 尚不存在，跳过扩容（首次启动由实体注解建表为 {}）", target, TARGET_TYPE);
                return;
            }
            if (SUFFICIENT_TYPES.contains(state.dataType().toUpperCase(Locale.ROOT))) {
                log.debug("{} 已是 {}，无需扩容", target, state.dataType());
                return;
            }
            mapper.modifyToMediumText(state);
            log.info("{} 已由 {} 扩容为 {}（长文不再因 64KB 上限落库失败）",
                    target, state.dataType(),
                    mapper.columnState(target.table(), target.column()).dataType());
        } catch (Exception exception) {
            log.warn("{} 扩容失败（跳过，不影响启动）：{}", target, exception.getMessage(), exception);
        }
    }
}
