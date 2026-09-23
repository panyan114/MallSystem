package com.mall.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试基类：整套测试跑在真实 MySQL 8.0 + Redis 上，镜像与 docker-compose 保持一致。
 *
 * <p>为什么不用 Mockito 把 Mapper 打桩：{@code OrderServiceImpl} 的核心保证全部落在 SQL 上——
 * 「{@code UPDATE ... WHERE stock >= n} 影响 0 行即判定超卖」、状态流转的条件更新、
 * 并发下只有一方能抢到行锁。把 Mapper 换成 mock，等于把这些保证连同被测对象一起替换掉，
 * 测试只能验证 Java 层的 if-else，跑绿了也说明不了生产环境不会超卖。
 *
 * <p><b>容器必须用单例模式启停，不能交给 {@code @Container} 的按类生命周期管理。</b>
 * 原因是 Spring 的 ApplicationContext 跨测试类缓存：{@code @DynamicPropertySource} 注入的
 * 容器端口只在首次建 Context 时求值一次，之后所有测试类复用同一个 Context。而
 * {@code @Container} 静态字段是「每个测试类启一次、跑完就停」——于是第一个测试类结束后
 * 容器被销毁，后续测试类虽然又起了新容器，Context 却仍指向已经死掉的旧端口，
 * 表现为 Redis 命令超时 + Hikari「No operations allowed after connection closed」，
 * 最终整批测试挂死。所以这里在静态块里手动 start 且永不 stop，
 * 让容器随 JVM 存活（退出时由 Ryuk 回收）。
 *
 * <p>顺带也省掉了每个测试类一次约 20 秒的 MySQL 冷启动。
 *
 * <p>Docker 没运行时 {@code disabledWithoutDocker = true} 会让这些测试整体跳过，
 * 而不是抛出一堆连接失败——在没装 Docker 的机器上 {@code mvn test} 依然应该是绿的。
 * 该跳过由 JUnit 的 ExecutionCondition 在实例化测试类之前判定，因此静态块不会被执行到。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    /** 测试专用签名密钥。与生产 .env 里的密钥无关，可以安全地硬编码在测试里。 */
    protected static final String TEST_JWT_SECRET =
            "integration-test-secret-key-at-least-32-chars-long";

    protected static final int REDIS_PORT = 6379;

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("mall_db")
            // 必须用 root：Testcontainers 默认创建的账号只被授予 mall_db.* 的权限，
            // 而 init.sql 开头就有 CREATE DATABASE，非 root 会直接被拒。
            .withUsername("root")
            .withPassword("test")
            .withUrlParam("characterEncoding", "utf-8")
            // 直接消费生产用的那份建库脚本（通过 pom 的 testResources 挂到 classpath）。
            // 这样「init.sql 能否建出实体类预期的表结构」就成了每次 mvn test 的隐式断言——
            // 历史上正是这份脚本缺 deleted 列才导致所有带 @TableLogic 的查询全部报错。
            .withInitScript("init.sql");

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    static {
        // 见类注释：手动启动、永不停止，容器随 JVM 存活，退出时由 Ryuk 回收。
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("jwt.secret", () -> TEST_JWT_SECRET);
    }
}
