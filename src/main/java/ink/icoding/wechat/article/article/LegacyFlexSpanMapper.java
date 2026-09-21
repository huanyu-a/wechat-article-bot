package ink.icoding.wechat.article.article;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import ink.icoding.wechat.article.skill.LayoutEngine;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The rows the legacy "split flex column" merge works on (D49 leftover, see
 * {@link MarkflowFlexSpanNormalizer}).
 *
 * <p>Why a dedicated mapper instead of a method on {@link ArticleMapper}: this is a maintenance
 * migration over stored content, not an article business operation (the same reasoning that put
 * {@code ArticleLongTextColumnMapper} next to {@code ArticleLongTextColumnRunner}).
 *
 * <p>Why there is no SQL pre-filter such as {@code content_html LIKE '%flex:1%'}: the stored shape
 * comes out of a browser serialization, where the shorthand is expanded to {@code flex: 1 1 0%}
 * (see {@code target/probe/browser/r39_article8_editor-exit.html}). A literal pre-filter would miss
 * exactly the documents this migration exists for, so the candidates are selected by engine and the
 * shape is decided in memory. The cost of that choice is that every start-up reads the MARKFLOW
 * bodies; a pre-filter written narrow enough to be cheap would skip legacy stock, which is the one
 * thing this runner exists to find.
 *
 * <p>Why only MARKFLOW articles: the split form is a render artifact. A PROMPT article's HTML is
 * hand-written by the model, and two adjacent {@code flex:1} spans there are two intentional
 * columns - merging them would silently destroy the layout.
 */
@Mapper
public interface LegacyFlexSpanMapper extends SmartMapper<Article>, LegacyFlexSpanMergeRunner.Store {

    @Override
    default List<LegacyFlexSpanMergeRunner.Store.ArticleRow> markflowFlexCandidates() {
        return select(Where.where(Article::getDeleted).eq(false)
                        .and(Article::getLayoutEngine).eq(LayoutEngine.MARKFLOW.name()))
                .stream()
                .map(article -> new LegacyFlexSpanMergeRunner.Store.ArticleRow(
                        article.getId(), article.getTitle(), article.getContentHtml()))
                .toList();
    }

    @Override
    default int writeMergedContentHtml(Long id, String contentHtml) {
        if (id == null || contentHtml == null) return 0;
        Article article = selectById(id);
        if (article == null) return 0;
        article.setContentHtml(contentHtml);
        article.setUpdatedAt(LocalDateTime.now());
        return updateById(article);
    }
}
