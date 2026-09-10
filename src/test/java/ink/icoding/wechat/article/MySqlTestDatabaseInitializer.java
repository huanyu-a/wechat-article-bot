package ink.icoding.wechat.article;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 在 Spring 创建任何业务 Bean 前清空专用 MySQL 测试库，保证集成测试可重复执行。
 *
 * 实现要点（2026-09-10 自检修订）：**清空数据而不是 DROP TABLE**。
 * DROP 版本与 smart-mybatis 自动建表存在时序竞争——同一 JVM 内每个
 * {@code @SpringBootTest} 类各建一个上下文（叠加 {@code @DirtiesContext} 后更频繁），
 * 每次上下文启动都会触发本初始化器；一旦上游判定「表结构已同步」而跳过 DDL，
 * DROP 掉的表就再也补不回来，表现为测试运行中随机报 {@code Table 'xxx' doesn't exist}。
 * 改为逐表 DELETE 后：表结构只由 smart-mybatis 建一次，数据在每次上下文启动前清空，
 * 既保证用例隔离，又不会出现「表消失」的窗口。
 *
 * Quartz 表额外用脚本重建一次（脚本为 CREATE TABLE IF NOT EXISTS，可重复执行），
 * 避免 Quartz 自身的 schema 初始化与清库叠加时出现缺表。
 *
 * 并发保护：测试库是共享资源。若同时跑两个 Maven 进程（开发者开两个终端或 CI 并发），
 * 第二个进程的清库会抹掉第一个进程正在使用的数据，表现为随机的 401、自增 ID 错位、种子缺失。
 * 这里用**跨进程文件锁**把整个测试 JVM 串行化：锁在首次初始化时获取并持有到 JVM 退出
 * （静态字段持有，防止 GC 释放），因此第二个测试 JVM 会阻塞等待，直到第一个退出。
 */
public class MySqlTestDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final String TEST_DATABASE = "wechat-article-test";
    private static final String QUARTZ_SCHEMA = "db/quartz/schema-mysql.sql";
    private static final String LOCK_FILE = System.getProperty("java.io.tmpdir")
            + java.io.File.separator + "wechat-article-test-db.lock";
    /** 持有到 JVM 退出的跨进程锁（静态字段避免被 GC 提前释放）。 */
    private static java.io.RandomAccessFile lockHandle;
    private static java.nio.channels.FileLock jvmLock;

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();
        String url = environment.getRequiredProperty("spring.datasource.url");
        String username = environment.getRequiredProperty("spring.datasource.username");
        String password = environment.getRequiredProperty("spring.datasource.password");

        try {
            acquireJvmLock();
            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                if (!"MySQL".equals(connection.getMetaData().getDatabaseProductName())) {
                    throw new IllegalStateException("测试数据源必须是 MySQL");
                }
                if (!TEST_DATABASE.equals(connection.getCatalog())) {
                    throw new IllegalStateException("拒绝清理非测试库：" + connection.getCatalog());
                }
                clearAllTables(connection);
                recreateQuartzSchema(connection);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("初始化 MySQL 测试库失败", exception);
        }
    }

    /**
     * 获取并持有跨进程锁直到 JVM 退出：保证同一时刻只有一个测试 JVM 在操作共享测试库。
     * 第二个 JVM 会在此阻塞，避免其清库动作抹掉第一个 JVM 正在使用的数据。
     */
    private static synchronized void acquireJvmLock() throws Exception {
        if (jvmLock != null && jvmLock.isValid()) return;
        java.io.RandomAccessFile handle = new java.io.RandomAccessFile(LOCK_FILE, "rw");
        java.nio.channels.FileLock lock = handle.getChannel().lock();
        lockHandle = handle;
        jvmLock = lock;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (jvmLock != null && jvmLock.isValid()) jvmLock.release();
                if (lockHandle != null) lockHandle.close();
            } catch (Exception ignored) {
            }
        }, "test-db-lock-release"));
    }

    /**
     * 清空所有已存在表的数据；表不存在则跳过（由 smart-mybatis 在上下文启动时建表）。
     * 用 TRUNCATE 而非 DELETE：既保留表结构（避免「表消失」窗口），又重置自增 ID
     * （集成测试按固定 ID 断言，DELETE 会让新文章从上次最大值继续自增）。
     */
    private void clearAllTables(Connection connection) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        List<String> tables = new ArrayList<>();
        try (ResultSet resultSet = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) tables.add(resultSet.getString("TABLE_NAME"));
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : tables) {
                try {
                    statement.execute("TRUNCATE TABLE `" + table.replace("`", "``") + "`");
                } catch (Exception ignored) {
                    // 个别表结构不兼容时跳过，不影响本轮用例
                }
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    /** 重建 Quartz 表（脚本为 CREATE TABLE IF NOT EXISTS，重复执行安全）。 */
    private void recreateQuartzSchema(Connection connection) throws Exception {
        String script = readClasspath(QUARTZ_SCHEMA);
        try (Statement statement = connection.createStatement()) {
            for (String sql : splitStatements(script)) {
                statement.execute(sql);
            }
        }
    }

    private static String readClasspath(String path) throws Exception {
        ClassLoader loader = MySqlTestDatabaseInitializer.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("找不到 Quartz schema 脚本：" + path);
            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.stripLeading().startsWith("--")) continue; // 跳过注释行
                    text.append(line).append('\n');
                }
            }
            return text.toString();
        }
    }

    private static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        for (String part : script.split(";")) {
            String sql = part.strip();
            if (!sql.isEmpty()) statements.add(sql);
        }
        return statements;
    }
}
