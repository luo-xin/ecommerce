# Ecommerce Backend

基于 **Spring Boot 3.2.5** 的电商演示系统后端，同时提供 HTTP REST（端口 8082）和 gRPC（端口 19090）两套接口。

## 技术栈

| 组件 | 版本 |
|------|------|
| Java | 17 |
| Spring Boot | 3.2.5 |
| Spring Security | 无状态 JWT |
| MyBatis-Plus | 3.5.7 |
| MySQL | 8.0 |
| Redis | 7.x |
| gRPC | 1.63.0（net.devh starter 3.1.0） |
| Protobuf | 3.25.3 |

---

## 环境准备

### 1. 启动 MySQL

```bash
# Homebrew 管理的 MySQL
brew services start mysql

# 或手动启动
mysql.server start
```

首次使用需初始化数据库（**仅执行一次**）：

```bash
mysql -u root -p < sql/init.sql
```

数据库配置（`src/main/resources/application.yml`）：
- 地址：`localhost:3306`
- 库名：`ecommerce_demo`
- 用户名 / 密码：`root` / `root`

### 2. 启动 Redis

```bash
# Homebrew 管理的 Redis
brew services start redis

# 或手动启动（后台运行）
redis-server --daemonize yes

# 验证
redis-cli ping   # 返回 PONG 即正常
```

Redis 配置：`localhost:6379`，无密码，database 0。

---

## 启动后端

### 普通启动（生产模拟）

```bash
# 在 ecommerce/ 目录下执行
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  /Users/tester/.local/lib/apache-maven-3.9.6/bin/mvn spring-boot:run
```

### 启动并启用测试免密登录后门（仅自动化测试场景）

为接口自动化测试提供的"用 userId 直接换 token"通道，**仅在 `dev` / `test` profile + 显式开关下启用**：

```bash
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  /Users/tester/.local/lib/apache-maven-3.9.6/bin/mvn spring-boot:run \
  -Dspring-boot.run.profiles=test \
  -Dspring-boot.run.arguments="--ecommerce.test-backdoor.enabled=true"
```

启动成功后日志中会出现：

```
[TEST-BACKDOOR] /api/internal/test/login-as is ENABLED. This must NOT happen in production.
Will secure Or [Mvc [pattern='/api/internal/test/**']] with ...
```

详细使用见下文 [测试免密登录后门](#测试免密登录后门-仅-devtest) 章节。

### 启动成功标志

```
Started EcommerceApplication in X.XXX seconds
Grpc Server started, listening on address: *, port: 19090
```

| 服务 | 地址 |
|------|------|
| HTTP REST API | http://localhost:8082 |
| gRPC Server | localhost:19090 |

验证 HTTP：
```bash
curl "http://localhost:8082/api/products?page=0&size=1"
# 返回 {"code":0,"msg":"success","data":{...}}
```

验证 gRPC（需安装 grpcurl）：
```bash
grpcurl -plaintext localhost:19090 list
# 列出所有 gRPC 服务
```

---

## 代码覆盖率采集（配合测试平台）

接口/功能测试是打一个**单独运行的本服务**（前端点击 / api_test 调 8082）。要采集这种覆盖率，必须给**运行中的服务进程**挂 **JaCoCo agent**——不是 `mvn test`（那只采单元测试）。

用 `scripts/run-with-coverage.sh` 启动（agent 以 tcpserver 暴露在 `localhost:6300`，服务仍在 8082）：

```bash
export JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$HOME/.local/lib/apache-maven-3.9.6/bin:$PATH"   # 任一可用 maven
git checkout <被测分支>          # 跑哪条分支就 checkout 哪条；commit 要与测试平台运行时所选分支一致
bash scripts/run-with-coverage.sh
```

出现 `[TEST-BACKDOOR] ... ENABLED`、`Started EcommerceApplication`，且 `6300` 在监听即成功。

流程：
1. 上面方式启动（挂 agent）
2. 正常做功能测试（前端点击 / 跑 api_test，都打 8082），覆盖率在该服务进程内累积
3. 到测试平台「代码覆盖率」对该项目点「运行」——平台 `jacoco:dump`（从 6300 拉数据）+ `jacoco:report` 生成报告，并算相对 master 的增量

前置：`pom.xml` 已配 `jacoco-maven-plugin`；JDK 必须 17。详见测试平台 README 的「代码覆盖率」章节。

---

## 常用命令

```bash
# 运行全部测试（无需真实 MySQL/Redis，全部 Mock）
mvn test

# 运行单个测试类
mvn test -Dtest=OrderServiceTest

# 运行单个测试方法
mvn test -Dtest=OrderServiceTest#payOrder_alreadyPaid_throws15008

# 打包 JAR
mvn package -DskipTests
```

---

## 默认账号

| 角色 | 手机号 | 密码 |
|------|--------|------|
| 管理员 | `13800000000` | `Admin1234` |

---

## 测试免密登录后门 (仅 dev/test)

> ⚠️ **仅供接口自动化测试使用。三重保险隔离 prod：`@Profile({"dev","test"})` + `@ConditionalOnProperty` + 启动期 prod profile 校验。**

### 启用条件（同时满足）

1. `spring.profiles.active` 是 `dev` 或 `test`
2. `ecommerce.test-backdoor.enabled=true`

任一不满足，接口路由不存在（403/404）。默认全部关闭。

### 接口契约

```
POST /api/internal/test/login-as?userId={userId}
无需 Authorization 头；无需密码
```

成功响应（与 `/api/users/login` 完全一致）：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "token": "eyJhbGc...",
    "userId": 1,
    "username": "admin",
    "role": "ADMIN"
  }
}
```

失败响应：

| 场景 | HTTP | code |
|------|------|------|
| 用户不存在 | 400 | 11011 |
| 用户被禁用 (`status != 1`) | 401 | 11005 |

### 使用示例

```bash
# 拿 admin 的 token
TOKEN=$(curl -s -X POST "http://localhost:8082/api/internal/test/login-as?userId=1" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 用 token 调任意业务接口
curl http://localhost:8082/api/users/me -H "Authorization: Bearer $TOKEN"
```

### 安全约束

- ❌ **prod profile 严禁启用**——`TestLoginBackdoorController.guardProd()` 在 `@PostConstruct` 阶段会抛 `IllegalStateException` 阻止启动
- ❌ 切勿将 `ecommerce.test-backdoor.enabled=true` 写入 `application.yml` 默认值（默认必须为 `false`）
- ✅ 推荐通过 CLI 参数 / 环境变量 / `application-test.yml` 等仅测试环境配置启用
- ✅ 内网网关应屏蔽 `/api/internal/**` 不暴露到外网

---

## gRPC 认证

需要登录的 gRPC 接口须在 Metadata 中传递 JWT：

```bash
# 1. 登录获取 token
TOKEN=$(grpcurl -plaintext \
  -d '{"phone":"13800000000","password":"Admin1234"}' \
  localhost:19090 ecommerce.UserService/Login \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# 2. 带 token 调用受保护接口
grpcurl -plaintext \
  -H "authorization: Bearer $TOKEN" \
  localhost:19090 ecommerce.CartService/GetCart
```

无需 token 的公开接口：`ListProducts`、`GetProduct`、`ListCategories`、`Register`、`Login`、`GetStock`。

---

## 模块结构

```
src/main/java/com/ecommerce/
├── user/         用户模块（注册、登录、地址）
├── product/      商品 & 分类模块
├── inventory/    库存模块（Redis 为权威数据源）
├── cart/         购物车模块（纯 Redis Hash）
├── order/        订单模块（7 状态机）
├── refund/       退款模块
├── grpc/         gRPC 拦截器 & 配置
├── common/       统一响应、异常、错误码
├── security/     JWT 认证过滤器、UserContext
└── config/       Security、Redis、MyBatis-Plus 配置

src/main/proto/   Protobuf 定义文件（6 个服务）
```
