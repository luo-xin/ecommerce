# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 常用命令

```bash
# 启动应用（端口 8082）
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  mvn spring-boot:run

# 启动并启用测试免密登录后门（仅自动化测试）
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  mvn spring-boot:run \
  -Dspring-boot.run.profiles=test \
  -Dspring-boot.run.arguments="--ecommerce.test-backdoor.enabled=true"

# 运行全部测试（无需真实 MySQL/Redis，全部 Mock）
mvn test

# 运行单个测试类
mvn test -Dtest=OrderServiceTest

# 运行单个测试方法
mvn test -Dtest=OrderServiceTest#payOrder_alreadyPaid_throws15008

# 初始化数据库（仅执行一次）
mysql -u root -p < sql/init.sql
```

> 本机 Java 17 路径：`/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home`
> Maven 路径：`/Users/tester/.local/lib/apache-maven-3.9.6/bin/mvn`

## 架构

### 模块结构

每个业务域位于 `com.ecommerce.<模块名>` 下，结构统一：

```
controller/   HTTP 层，参数校验后转发给 Service
dto/          请求/响应对象（@Valid 校验）
entity/       MyBatis-Plus 实体，对应数据库表
mapper/       MyBatis-Plus Mapper 接口
service/      接口 + impl/ 实现类
```

横切关注点：
- `common/` — `Result<T>`、`BusinessException`、`ErrorCode`、`GlobalExceptionHandler`
- `security/` — `JwtAuthFilter`、`JwtUtil`、`UserContext`（ThreadLocal）、`UserAuthMapper`
- `config/` — `SecurityConfig`、`RedisConfig`、`MybatisPlusConfig`

### 统一响应格式

所有接口返回 `Result<T>`：`{"code": 0, "msg": "success", "data": ...}`

业务异常直接抛 `BusinessException(ErrorCode.XXX)`，由 `GlobalExceptionHandler` 统一处理并映射 HTTP 状态码。

### 认证

`JwtAuthFilter` 在每次请求前执行，从 `Authorization: Bearer <token>` 提取并校验 token，将用户信息写入 `UserContext`（ThreadLocal）。Token 包含 `passwordVersion` 字段，改密后旧 token 立即失效。退出登录将 jti 写入 Redis 黑名单 `token:blacklist:{jti}`。

路由权限：
- 公开：`POST /api/users/register`、`POST /api/users/login`、`GET /api/products/**`、`GET /api/categories/**`
- 仅管理员：`/api/admin/**`
- 其余：需登录

### 测试免密登录后门 (仅 dev/test profile)

为接口自动化测试提供：`POST /api/internal/test/login-as?userId=xxx` 直接换 token，无需密码。

- 实现：`security.testbackdoor.TestLoginBackdoorController` + `TestBackdoorSecurityConfig`（独立 SecurityFilterChain，`@Order(0)`，`securityMatcher("/api/internal/test/**").permitAll()`）
- 三重隔离：`@Profile({"dev","test"})` + `@ConditionalOnProperty("ecommerce.test-backdoor.enabled")` + `@PostConstruct` 启动校验非 prod
- 默认配置 `ecommerce.test-backdoor.enabled: false`，必须显式 `--ecommerce.test-backdoor.enabled=true` 并传 `-Dspring-boot.run.profiles=test` 启动才会启用

### 核心业务逻辑

**库存**：Redis `inventory:available:{productId}` 为权威数据源，`DECRBY` 原子扣减。启动时（`ApplicationReadyEvent`）自动从 MySQL 同步到 Redis。

**购物车**：纯 Redis Hash，键 `cart:{userId}`，field = productId，value = 数量。无数据库表，TTL 7 天。

**订单**：7 状态机 `PENDING_PAYMENT → PAID → SHIPPED → COMPLETED`，`PENDING_PAYMENT` 可取消，`PAID` 可退款。`addressSnapshot` 字段存下单时的地址 JSON 快照。

**退款**：`SELECT FOR UPDATE` 防并发重复申请，`@Transactional(propagation = REQUIRES_NEW)` 在 `InventoryService` 中原子恢复库存。

### 错误码范围

| 范围 | 模块 |
|------|------|
| 10002 | 全局：无权限 |
| 11xxx | 用户 |
| 12xxx | 商品 |
| 13xxx | 库存 |
| 14xxx | 购物车 |
| 15xxx | 订单 |
| 16xxx | 退款 |

### 测试规范

**Service 测试**：`@ExtendWith(MockitoExtension.class)` + `@Mock` / `@InjectMocks`，`@BeforeEach` 设置 `UserContext`，`@AfterEach` 清除。

**Controller 测试**：`@WebMvcTest` + `@Import(SecurityConfig.class)`，必须将所有 Mapper 和安全相关 Bean 声明为 `@MockBean`（参考 `UserControllerTest`）。

### 关键配置（application.yml）

- 服务端口：**8082**
- 数据库：`localhost:3306`，库名 `ecommerce_demo`，用户名/密码 `root/root`
- Redis：`localhost:6379`，无密码，database 0
- JWT：有效期 24 小时
- 默认管理员：手机号 `13800000000`，密码 `Admin1234`
