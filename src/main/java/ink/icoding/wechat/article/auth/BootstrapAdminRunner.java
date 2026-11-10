package ink.icoding.wechat.article.auth;

import ink.icoding.wechat.article.auth.model.SystemUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public BootstrapAdminRunner(UserMapper userMapper, PasswordEncoder passwordEncoder,
                                @Value("${app.bootstrap.admin-username}") String username,
                                @Value("${app.bootstrap.admin-password}") String password) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.count() > 0) return;
        // 口令为空时**不创建**账号，而不是创建一个空/默认口令的管理员（2026-11-10 工程复查 C-1）。
        // 改造前 application.yaml 给了众所周知的默认口令：忘记注入的部署会静默得到一个
        // 用公开口令就能登录的 ADMIN，且日志只提示「请尽快修改默认密码」——太弱。
        // 现在把选择权交回运维：要么注入 ADMIN_PASSWORD，要么就没有管理员（且日志明确说明怎么办）。
        if (password == null || password.isBlank()) {
            log.error("未配置 ADMIN_PASSWORD，跳过初始管理员创建：库里没有任何用户，"
                    + "在注入 ADMIN_PASSWORD 并重启前将无法登录。"
                    + "（这是有意为之：不使用默认口令创建管理员）");
            return;
        }
        SystemUser user = new SystemUser();
        user.setUsername(username);
        user.setDisplayName("系统管理员");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("ADMIN");
        user.setStatus("ACTIVE");
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userMapper.insert(user);
        log.info("已创建初始管理员账号 {}（口令来自 ADMIN_PASSWORD），请妥善保管", username);
    }
}
