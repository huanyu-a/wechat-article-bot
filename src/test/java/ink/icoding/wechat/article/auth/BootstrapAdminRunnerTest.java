package ink.icoding.wechat.article.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 初始管理员不得用空/默认口令创建（2026-11-10 工程复查 C-1）。
 *
 * <p>改造前：{@code admin-password} 在 {@code application.yaml} 里有众所周知的默认值，
 * 于是「运维忘记注入 ADMIN_PASSWORD」会**静默**产出一个可用公开口令登录的 ADMIN，
 * 日志只提示「请登录后尽快修改默认密码」。把默认值置空后，若这里不判空，
 * 就会变成用<b>空口令</b>创建管理员 —— 比默认口令更糟。
 *
 * <p>因此断言：口令为空时<b>一行都不写库</b>，把「没有管理员」这个明确状态交回运维，
 * 而不是造一个弱口令账号。
 */
class BootstrapAdminRunnerTest {

    @Test
    void blankPasswordSkipsAdminCreationEntirely() {
        UserMapper mapper = mock(UserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(mapper.count()).thenReturn(0L);

        new BootstrapAdminRunner(mapper, encoder, "admin", "").run(null);
        new BootstrapAdminRunner(mapper, encoder, "admin", "   ").run(null);
        new BootstrapAdminRunner(mapper, encoder, "admin", null).run(null);

        verify(mapper, never()).insert(any());
        verify(encoder, never()).encode(any());
    }

    @Test
    void configuredPasswordStillCreatesTheAdmin() {
        UserMapper mapper = mock(UserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(mapper.count()).thenReturn(0L);
        when(encoder.encode("a-real-password")).thenReturn("$2a$hash");

        new BootstrapAdminRunner(mapper, encoder, "admin", "a-real-password").run(null);

        verify(mapper).insert(any());
    }

    @Test
    void existingUsersAreNeverTouched() {
        UserMapper mapper = mock(UserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(mapper.count()).thenReturn(1L);

        new BootstrapAdminRunner(mapper, encoder, "admin", "a-real-password").run(null);

        verify(mapper, never()).insert(any());
    }
}
