package ink.icoding.wechat.article.skill;

import ink.icoding.wechat.article.auth.CurrentUser;
import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Skill CRUD API（/api/skills，skills-agent-plan 5.6）。
 * 读全员、写 ADMIN/OPERATOR；preview 为接口级全员可访问。
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {
    /** preview 限流：每用户每分钟最多 30 次（MARKFLOW 技能会触发出网拉取语法 guide）。 */
    private static final int PREVIEW_MAX_PER_MINUTE = 30;

    private final SkillService service;
    private final SkillPromptAssembler assembler;
    private final CurrentUserService currentUserService;
    private final ink.icoding.wechat.article.common.RateLimiter rateLimiter;

    public SkillController(SkillService service, SkillPromptAssembler assembler,
                           CurrentUserService currentUserService,
                           ink.icoding.wechat.article.common.RateLimiter rateLimiter) {
        this.service = service;
        this.assembler = assembler;
        this.currentUserService = currentUserService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public ApiResponse<List<Skill>> list(@RequestParam(required = false) String dimension,
                                         @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.ok(service.list(dimension, enabled));
    }

    @GetMapping("/{id}")
    public ApiResponse<Skill> get(@PathVariable Long id) {
        return ApiResponse.ok(service.required(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Skill> create(@Valid @RequestBody SkillService.SkillRequest request) {
        return ApiResponse.ok(service.create(request, currentUser()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Skill> update(@PathVariable Long id,
                                     @Valid @RequestBody SkillService.SkillRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Skill> duplicate(@PathVariable Long id) {
        return ApiResponse.ok(service.duplicate(id, currentUser()));
    }

    /** 组装预览：给定时创作场景（skillIds 作为任务级技能）返回注入 prompt、生效引擎与被忽略的排版技能。 */
    @PostMapping("/preview")
    public ApiResponse<SkillPreviewModels.PreviewView> preview(
            @Valid @RequestBody SkillPreviewModels.PreviewRequest request) {
        // 限流：MARKFLOW 技能预览会调用渲染服务获取语法 guide（出网），防被反复触发
        rateLimiter.check("skills-preview:" + currentUserService.required().id(),
                PREVIEW_MAX_PER_MINUTE, java.time.Duration.ofMinutes(1));
        SkillPromptResult result = assembler.assemble(SkillPreviewModels.context(request));
        return ApiResponse.ok(new SkillPreviewModels.PreviewView(result.prompt(), result.engine().name(),
                result.ignoredLayoutSkills().stream().map(SkillPreviewModels.IgnoredSkill::of).toList()));
    }

    private Long currentUser() {
        CurrentUser user = currentUserService.required();
        return user.id();
    }
}
