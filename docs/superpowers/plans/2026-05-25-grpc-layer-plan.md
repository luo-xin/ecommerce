# gRPC 接口层实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Spring Boot 电商系统上新增 gRPC Server（端口 9090），6 个模块全部暴露 gRPC 接口，与 HTTP REST（端口 8082）共用同一套 Service 业务层，业务逻辑零改动。

**Architecture:** `net.devh:grpc-spring-boot-starter` 驱动 gRPC Server，与 Spring MVC 同进程共存。每个模块新增 `*GrpcService` 实现类（`@GrpcService`），复用现有 Spring Service Bean。`GrpcAuthInterceptor` 实现 `ServerInterceptor`，复用 `JwtUtil`+`UserContext`+Redis 黑名单完成鉴权；公开接口通过方法全名白名单跳过鉴权。开启服务器反射供 grpcurl/Postman 免导入 proto 使用。所有"空参数/空返回值"使用标准 `google.protobuf.Empty`（避免自定义 Empty 跨文件引用问题）。

**Tech Stack:** Spring Boot 3.2.5 · Java 17 · net.devh:grpc-spring-boot-starter:3.1.0.RELEASE · io.grpc:grpc-services:1.58.0 · com.google.protobuf:protobuf-java:3.24.0 · protobuf-maven-plugin:0.6.1 · os-maven-plugin:1.7.1

---

## 文件结构

**新建文件：**
```
ecommerce/src/main/proto/
  product.proto
  user.proto
  cart.proto
  order.proto
  inventory.proto
  refund.proto

ecommerce/src/main/java/com/ecommerce/
  grpc/
    interceptor/GrpcAuthInterceptor.java   ← JWT 鉴权拦截器（全局）
    interceptor/GrpcInterceptorConfig.java ← 将拦截器注册为 @GrpcGlobalServerInterceptor Bean
  product/grpc/ProductGrpcService.java
  user/grpc/UserGrpcService.java
  cart/grpc/CartGrpcService.java
  order/grpc/OrderGrpcService.java
  inventory/grpc/InventoryGrpcService.java
  refund/grpc/RefundGrpcService.java
```

**修改文件：**
```
ecommerce/pom.xml                          ← 新增依赖 + 构建插件
ecommerce/src/main/resources/application.yml  ← 新增 grpc.server 配置
```

**不改动：** 所有 Controller、Service、Mapper、Entity、DTO、SecurityConfig

---

## Task 0: 添加 Maven 依赖和构建插件

**Files:**
- Modify: `ecommerce/pom.xml`

- [ ] **Step 1: 在 `<properties>` 块中添加版本属性**

打开 `ecommerce/pom.xml`，在现有 `<properties>` 块内（`<java.version>` 等之后）添加：

```xml
<grpc.version>1.58.0</grpc.version>
<protobuf.version>3.24.0</protobuf.version>
```

- [ ] **Step 2: 在 `<dependencies>` 中添加 gRPC 依赖**

在 `spring-boot-starter-test` 依赖之前添加（保持在 `</dependencies>` 闭合标签内）：

```xml
<!-- gRPC Spring Boot Starter（含 grpc-stub, grpc-netty-shaded 等） -->
<dependency>
    <groupId>net.devh</groupId>
    <artifactId>grpc-spring-boot-starter</artifactId>
    <version>3.1.0.RELEASE</version>
</dependency>
<!-- gRPC 服务器反射（grpcurl/Postman 自动发现服务，无需导入 proto） -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-services</artifactId>
    <version>${grpc.version}</version>
</dependency>
<!-- Protobuf Java 运行时（含 google.protobuf.Empty 等 well-known types） -->
<dependency>
    <groupId>com.google.protobuf</groupId>
    <artifactId>protobuf-java</artifactId>
    <version>${protobuf.version}</version>
</dependency>
```

- [ ] **Step 3: 在 `<build>` 块中添加 os-maven-plugin 扩展和 protobuf-maven-plugin**

找到 `<build>` 标签，在 `<plugins>` 之前插入 `<extensions>` 块，并在 `<plugins>` 内添加 protobuf 插件。最终 `<build>` 结构如下（`spring-boot-maven-plugin` 保持不变）：

```xml
<build>
    <extensions>
        <!-- 检测当前 OS/架构，供 protobuf-maven-plugin 下载对应的 protoc 二进制 -->
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.7.1</version>
        </extension>
    </extensions>
    <plugins>
        <!-- 现有的 spring-boot-maven-plugin 保持不变 -->
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <excludes>
                    <exclude>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                    </exclude>
                </excludes>
            </configuration>
        </plugin>
        <!-- 新增：编译 src/main/proto/*.proto → target/generated-sources/ -->
        <plugin>
            <groupId>org.xolstice.maven.plugins</groupId>
            <artifactId>protobuf-maven-plugin</artifactId>
            <version>0.6.1</version>
            <configuration>
                <protocArtifact>com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}</protocArtifact>
                <pluginId>grpc-java</pluginId>
                <pluginArtifact>io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}</pluginArtifact>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>compile</goal>          <!-- 编译 .proto → Java message 类 -->
                        <goal>compile-custom</goal>   <!-- 编译 .proto → gRPC stub 类 -->
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

- [ ] **Step 4: 验证 pom.xml 可解析**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  /Users/tester/.local/lib/apache-maven-3.9.6/bin/mvn validate
```

期望：`BUILD SUCCESS`（无 XML 语法错误）

- [ ] **Step 5: 提交**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
git add pom.xml
git commit -m "build: add grpc-spring-boot-starter and protobuf-maven-plugin"
```

---

## Task 1: 添加 application.yml gRPC 配置

**Files:**
- Modify: `ecommerce/src/main/resources/application.yml`

- [ ] **Step 1: 在 application.yml 末尾追加 gRPC Server 配置**

打开 `ecommerce/src/main/resources/application.yml`，在文件末尾添加（注意 YAML 缩进）：

```yaml
# gRPC Server（与 HTTP Server 同进程，独立端口）
grpc:
  server:
    port: 9090
    reflection:
      enabled: true   # 开启服务器反射，grpcurl/Postman 免导入 proto
```

- [ ] **Step 2: 提交**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
git add src/main/resources/application.yml
git commit -m "config: add grpc server port 9090 with reflection enabled"
```

---

## Task 2: 创建 6 个 Proto 文件

**Files:**
- Create: `ecommerce/src/main/proto/product.proto`
- Create: `ecommerce/src/main/proto/user.proto`
- Create: `ecommerce/src/main/proto/cart.proto`
- Create: `ecommerce/src/main/proto/order.proto`
- Create: `ecommerce/src/main/proto/inventory.proto`
- Create: `ecommerce/src/main/proto/refund.proto`

**重要说明：**
- 所有 proto 文件使用 `java_package = "com.ecommerce.grpc.proto"` + `java_multiple_files = true`
- "空参数/空返回值"使用 `google.protobuf.Empty`（import "google/protobuf/empty.proto"），避免自定义 GrpcEmpty 跨文件冲突
- proto 消息名加 `Grpc` 前缀（如 `GrpcProductItem`），避免与同包下现有 DTO 类名冲突

- [ ] **Step 1: 创建 `src/main/proto/` 目录并写 product.proto**

```bash
mkdir -p /Users/tester/PycharmProjects/Ai学习/ecommerce/src/main/proto
```

创建 `ecommerce/src/main/proto/product.proto`：

```protobuf
syntax = "proto3";
package ecommerce;
option java_package = "com.ecommerce.grpc.proto";
option java_multiple_files = true;

import "google/protobuf/empty.proto";

service ProductService {
  rpc ListProducts(ListProductsReq) returns (ListProductsResp);
  rpc GetProduct(GetProductReq) returns (GrpcProductDetail);
  rpc ListCategories(google.protobuf.Empty) returns (ListCategoriesResp);
}

message ListProductsReq {
  int64 category_id = 1;
  string keyword = 2;
  int32 page = 3;
  int32 size = 4;
}
message ListProductsResp {
  repeated GrpcProductItem products = 1;
  int64 total = 2;
}
message GrpcProductItem {
  int64 id = 1;
  string name = 2;
  string price = 3;
  string main_image = 4;
  int64 category_id = 5;
  string status = 6;
}
message GetProductReq { int64 product_id = 1; }
message GrpcProductDetail {
  int64 id = 1;
  string name = 2;
  string price = 3;
  string description = 4;
  repeated string images = 5;
  int64 category_id = 6;
  string status = 7;
}
message ListCategoriesResp { repeated GrpcCategoryItem categories = 1; }
message GrpcCategoryItem {
  int64 id = 1;
  string name = 2;
  int64 parent_id = 3;
  repeated GrpcCategoryItem children = 4;
}
```

- [ ] **Step 2: 创建 user.proto**

创建 `ecommerce/src/main/proto/user.proto`：

```protobuf
syntax = "proto3";
package ecommerce;
option java_package = "com.ecommerce.grpc.proto";
option java_multiple_files = true;

import "google/protobuf/empty.proto";

service UserService {
  rpc Register(GrpcRegisterReq) returns (GrpcRegisterResp);
  rpc Login(GrpcLoginReq) returns (GrpcLoginResp);
  rpc GetProfile(google.protobuf.Empty) returns (GrpcUserInfo);
  rpc ListAddresses(google.protobuf.Empty) returns (GrpcAddressListResp);
  rpc AddAddress(GrpcAddressReq) returns (GrpcAddressResp);
  rpc DeleteAddress(GrpcDeleteAddressReq) returns (google.protobuf.Empty);
}

message GrpcRegisterReq {
  string phone = 1;
  string password = 2;
  string username = 3;
}
message GrpcRegisterResp { int64 user_id = 1; }
message GrpcLoginReq { string phone = 1; string password = 2; }
message GrpcLoginResp { string token = 1; int64 user_id = 2; string role = 3; }
message GrpcUserInfo { int64 id = 1; string phone = 2; string username = 3; string role = 4; }
message GrpcAddressReq {
  string receiver_name = 1;
  string receiver_phone = 2;
  string province = 3;
  string city = 4;
  string district = 5;
  string detail = 6;
  bool is_default = 7;
}
message GrpcAddressResp {
  int64 id = 1;
  string receiver_name = 2;
  string receiver_phone = 3;
  string province = 4;
  string city = 5;
  string district = 6;
  string detail = 7;
  bool is_default = 8;
}
message GrpcAddressListResp { repeated GrpcAddressResp addresses = 1; }
message GrpcDeleteAddressReq { int64 address_id = 1; }
```

- [ ] **Step 3: 创建 cart.proto**

创建 `ecommerce/src/main/proto/cart.proto`：

```protobuf
syntax = "proto3";
package ecommerce;
option java_package = "com.ecommerce.grpc.proto";
option java_multiple_files = true;

import "google/protobuf/empty.proto";

service CartService {
  rpc GetCart(google.protobuf.Empty) returns (GrpcCartResp);
  rpc AddItem(GrpcAddCartItemReq) returns (google.protobuf.Empty);
  rpc UpdateItem(GrpcUpdateCartItemReq) returns (google.protobuf.Empty);
  rpc RemoveItem(GrpcRemoveCartItemReq) returns (google.protobuf.Empty);
  rpc ClearCart(google.protobuf.Empty) returns (google.protobuf.Empty);
}

message GrpcCartResp {
  repeated GrpcCartItemResp items = 1;
  int32 total_count = 2;
}
message GrpcCartItemResp {
  int64 product_id = 1;
  string product_name = 2;
  string price = 3;
  int32 quantity = 4;
  string main_image = 5;
}
message GrpcAddCartItemReq { int64 product_id = 1; int32 quantity = 2; }
message GrpcUpdateCartItemReq { int64 product_id = 1; int32 quantity = 2; }
message GrpcRemoveCartItemReq { int64 product_id = 1; }
```

- [ ] **Step 4: 创建 order.proto**

创建 `ecommerce/src/main/proto/order.proto`：

```protobuf
syntax = "proto3";
package ecommerce;
option java_package = "com.ecommerce.grpc.proto";
option java_multiple_files = true;

import "google/protobuf/empty.proto";

service OrderService {
  rpc CreateOrder(GrpcCreateOrderReq) returns (GrpcCreateOrderResp);
  rpc ListOrders(GrpcListOrdersReq) returns (GrpcListOrdersResp);
  rpc GetOrder(GrpcGetOrderReq) returns (GrpcOrderDetail);
  rpc PayOrder(GrpcPayOrderReq) returns (google.protobuf.Empty);
  rpc CancelOrder(GrpcCancelOrderReq) returns (google.protobuf.Empty);
}

// 注意：每个商品的数量来自购物车，这里只传 product_ids（与 HTTP CreateOrderReq 一致）
message GrpcCreateOrderReq {
  int64 address_id = 1;
  repeated int64 product_ids = 2;
}
message GrpcCreateOrderResp {
  int64 order_id = 1;
  string order_no = 2;
}
message GrpcListOrdersReq {
  int32 page = 1;
  int32 size = 2;
  string status = 3;
}
message GrpcListOrdersResp {
  repeated GrpcOrderSummary orders = 1;
  int64 total = 2;
}
message GrpcOrderSummary {
  int64 id = 1;
  string order_no = 2;
  string status = 3;
  string total_amount = 4;
  string first_product_name = 5;
  string first_product_main_image = 6;
  string created_at = 7;
}
message GrpcGetOrderReq { int64 order_id = 1; }
message GrpcOrderDetail {
  int64 id = 1;
  string order_no = 2;
  string status = 3;
  string total_amount = 4;
  repeated GrpcOrderItemDetail items = 5;
  string address_snapshot = 6;
  string created_at = 7;
}
message GrpcOrderItemDetail {
  int64 product_id = 1;
  string product_name = 2;
  string price = 3;
  int32 quantity = 4;
  string main_image = 5;
}
message GrpcPayOrderReq { int64 order_id = 1; }
message GrpcCancelOrderReq { int64 order_id = 1; string reason = 2; }
```

- [ ] **Step 5: 创建 inventory.proto**

创建 `ecommerce/src/main/proto/inventory.proto`：

```protobuf
syntax = "proto3";
package ecommerce;
option java_package = "com.ecommerce.grpc.proto";
option java_multiple_files = true;

service InventoryService {
  rpc GetStock(GrpcGetStockReq) returns (GrpcStockResp);
  rpc Restock(GrpcRestockReq) returns (GrpcRestockResp);
  rpc GetInventoryLogs(GrpcGetLogsReq) returns (GrpcInventoryLogsResp);
}

message GrpcGetStockReq { int64 product_id = 1; }
message GrpcStockResp { int64 product_id = 1; int32 available = 2; }
message GrpcRestockReq { int64 product_id = 1; int32 quantity = 2; string remark = 3; }
message GrpcRestockResp { int32 before_stock = 1; int32 after_stock = 2; }
message GrpcGetLogsReq { int64 product_id = 1; int32 page = 2; int32 size = 3; }
message GrpcInventoryLogsResp {
  repeated GrpcInventoryLogItem logs = 1;
  int64 total = 2;
}
message GrpcInventoryLogItem {
  int64 id = 1;
  string change_type = 2;
  int32 change_quantity = 3;
  string created_at = 4;
}
```

- [ ] **Step 6: 创建 refund.proto**

创建 `ecommerce/src/main/proto/refund.proto`：

```protobuf
syntax = "proto3";
package ecommerce;
option java_package = "com.ecommerce.grpc.proto";
option java_multiple_files = true;

service RefundService {
  rpc ApplyRefund(GrpcApplyRefundReq) returns (GrpcRefundResp);
  rpc GetRefund(GrpcGetRefundReq) returns (GrpcRefundDetailResp);
  rpc ListRefunds(GrpcListRefundsReq) returns (GrpcListRefundsResp);
}

message GrpcApplyRefundReq { int64 order_id = 1; string reason = 2; }
message GrpcGetRefundReq { int64 refund_id = 1; }
message GrpcRefundResp {
  int64 refund_id = 1;
  string refund_no = 2;
  string refund_amount = 3;
  string status = 4;
}
message GrpcRefundDetailResp {
  int64 refund_id = 1;
  int64 order_id = 2;
  string refund_no = 3;
  string status = 4;
  string refund_amount = 5;
  string reason = 6;
  string created_at = 7;
}
message GrpcListRefundsReq { int32 page = 1; int32 size = 2; string status = 3; }
message GrpcListRefundsResp {
  repeated GrpcRefundDetailResp refunds = 1;
  int64 total = 2;
}
```

- [ ] **Step 7: 提交**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
git add src/main/proto/
git commit -m "feat: add proto definitions for all 6 modules"
```

---

## Task 3: 验证 Proto 编译

**Files:** 无新建文件，验证生成代码

- [ ] **Step 1: 运行 protobuf 代码生成**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  /Users/tester/.local/lib/apache-maven-3.9.6/bin/mvn generate-sources -q
```

期望：`BUILD SUCCESS`，无报错

- [ ] **Step 2: 验证生成的文件存在**

```bash
ls target/generated-sources/protobuf/java/com/ecommerce/grpc/proto/ | head -10
ls target/generated-sources/protobuf/grpc-java/com/ecommerce/grpc/proto/ | head -10
```

期望：看到 `ProductServiceGrpc.java`、`UserServiceGrpc.java`、`GrpcProductItem.java` 等文件

- [ ] **Step 3: 完整编译验证（含 main 源码）**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  /Users/tester/.local/lib/apache-maven-3.9.6/bin/mvn compile -q
```

期望：`BUILD SUCCESS`

---

## Task 4: 实现 GrpcAuthInterceptor

**Files:**
- Create: `ecommerce/src/main/java/com/ecommerce/grpc/interceptor/GrpcAuthInterceptor.java`
- Create: `ecommerce/src/main/java/com/ecommerce/grpc/interceptor/GrpcInterceptorConfig.java`

- [ ] **Step 1: 创建目录并写 GrpcAuthInterceptor**

```bash
mkdir -p /Users/tester/PycharmProjects/Ai学习/ecommerce/src/main/java/com/ecommerce/grpc/interceptor
```

创建 `ecommerce/src/main/java/com/ecommerce/grpc/interceptor/GrpcAuthInterceptor.java`：

```java
package com.ecommerce.grpc.interceptor;

import com.ecommerce.security.JwtUtil;
import com.ecommerce.security.UserAuthMapper;
import com.ecommerce.security.UserContext;
import io.grpc.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 全局 gRPC 鉴权拦截器：
 * - 白名单方法直接放行
 * - 其余方法从 Metadata 读取 "authorization: Bearer <token>"
 * - 复用 JwtUtil 解析 Token，检查 Redis 黑名单，验证 passwordVersion
 * - 通过则写入 UserContext；失败则返回 UNAUTHENTICATED
 *
 * 注意：不加 @Component，通过 GrpcInterceptorConfig 的 @Bean 方法注册，
 * @GrpcGlobalServerInterceptor 标注在 @Bean 方法上（net.devh starter 要求）。
 */
@RequiredArgsConstructor
public class GrpcAuthInterceptor implements ServerInterceptor {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final UserAuthMapper userAuthMapper;

    /** 无需鉴权的 gRPC 方法全名（package.Service/Method） */
    private static final Set<String> PUBLIC_METHODS = Set.of(
            "ecommerce.ProductService/ListProducts",
            "ecommerce.ProductService/GetProduct",
            "ecommerce.ProductService/ListCategories",
            "ecommerce.UserService/Register",
            "ecommerce.UserService/Login",
            "ecommerce.InventoryService/GetStock"
    );

    private static final Metadata.Key<String> AUTH_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String fullMethodName = call.getMethodDescriptor().getFullMethodName();

        // 白名单：直接放行，不做任何鉴权
        if (PUBLIC_METHODS.contains(fullMethodName)) {
            return next.startCall(call, headers);
        }

        // 读取 Authorization header
        String authHeader = headers.get(AUTH_KEY);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            call.close(Status.UNAUTHENTICATED.withDescription("缺少 Authorization Token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtUtil.parseToken(token);
            String jti = claims.getId();

            // 检查 Redis 黑名单（退出登录后的 token 立即失效）
            if (Boolean.TRUE.equals(redisTemplate.hasKey("token:blacklist:" + jti))) {
                call.close(Status.UNAUTHENTICATED.withDescription("Token 已失效（已退出登录）"), new Metadata());
                return new ServerCall.Listener<>() {};
            }

            Long userId = claims.get("userId", Long.class);
            String role = claims.get("role", String.class);
            Integer tokenPV = claims.get("passwordVersion", Integer.class);

            // 验证 passwordVersion（改密后旧 token 立即失效）
            Integer dbPV = userAuthMapper.selectPasswordVersion(userId);
            if (dbPV == null || !dbPV.equals(tokenPV)) {
                call.close(Status.UNAUTHENTICATED.withDescription("Token 已失效（密码已修改）"), new Metadata());
                return new ServerCall.Listener<>() {};
            }

            // 设置 UserContext，并在调用结束时清除（ThreadLocal 清理）
            UserContext.set(new UserContext(userId, role, jti));
            ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
                @Override
                public void onComplete() {
                    UserContext.clear();
                    super.onComplete();
                }

                @Override
                public void onCancel() {
                    UserContext.clear();
                    super.onCancel();
                }
            };

        } catch (JwtException e) {
            call.close(Status.UNAUTHENTICATED.withDescription("Token 无效或已过期"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
    }
}
```

- [ ] **Step 2: 创建配置类，将拦截器注册为 @GrpcGlobalServerInterceptor Bean**

创建 `ecommerce/src/main/java/com/ecommerce/grpc/interceptor/GrpcInterceptorConfig.java`：

```java
package com.ecommerce.grpc.interceptor;

import com.ecommerce.security.JwtUtil;
import com.ecommerce.security.UserAuthMapper;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class GrpcInterceptorConfig {

    /**
     * @GrpcGlobalServerInterceptor 标注在 @Bean 方法上（不是类上），
     * net.devh starter 会自动将此 Bean 注册为全局拦截器，应用到所有 gRPC 服务。
     */
    @GrpcGlobalServerInterceptor
    @Bean
    public GrpcAuthInterceptor grpcAuthInterceptor(JwtUtil jwtUtil,
                                                    StringRedisTemplate redisTemplate,
                                                    UserAuthMapper userAuthMapper) {
        return new GrpcAuthInterceptor(jwtUtil, redisTemplate, userAuthMapper);
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  /Users/tester/.local/lib/apache-maven-3.9.6/bin/mvn compile -q
```

期望：`BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
git add src/main/java/com/ecommerce/grpc/
git commit -m "feat: add GrpcAuthInterceptor with JWT, Redis blacklist, and passwordVersion check"
```

---

## Task 5: 实现 ProductGrpcService

**Files:**
- Create: `ecommerce/src/main/java/com/ecommerce/product/grpc/ProductGrpcService.java`

调用链：`ProductGrpcService` → `ProductService.listProducts()` / `getProductDetail()` + `CategoryService.getCategoryTree()`

- [ ] **Step 1: 创建目录并写 ProductGrpcService**

```bash
mkdir -p /Users/tester/PycharmProjects/Ai学习/ecommerce/src/main/java/com/ecommerce/product/grpc
```

创建 `ecommerce/src/main/java/com/ecommerce/product/grpc/ProductGrpcService.java`：

```java
package com.ecommerce.product.grpc;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.dto.PageResult;
import com.ecommerce.grpc.proto.*;
import com.ecommerce.product.dto.CategoryTreeResp;
import com.ecommerce.product.dto.ProductDetailResp;
import com.ecommerce.product.dto.ProductListItem;
import com.ecommerce.product.service.CategoryService;
import com.ecommerce.product.service.ProductService;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private final ProductService productService;
    private final CategoryService categoryService;

    @Override
    public void listProducts(ListProductsReq req, StreamObserver<ListProductsResp> responseObserver) {
        try {
            Long categoryId = req.getCategoryId() == 0 ? null : req.getCategoryId();
            String keyword = req.getKeyword().isBlank() ? null : req.getKeyword();
            int size = req.getSize() == 0 ? 20 : Math.min(req.getSize(), 100);

            PageResult<ProductListItem> result = productService.listProducts(categoryId, keyword, req.getPage(), size);

            List<GrpcProductItem> items = result.getItems().stream()
                    .map(p -> GrpcProductItem.newBuilder()
                            .setId(p.getProductId())
                            .setName(p.getName() != null ? p.getName() : "")
                            .setPrice(p.getPrice() != null ? p.getPrice().toPlainString() : "0")
                            .setMainImage(p.getMainImage() != null ? p.getMainImage() : "")
                            .setStatus(p.getStatus() != null ? p.getStatus() : "")
                            .build())
                    .collect(Collectors.toList());

            responseObserver.onNext(ListProductsResp.newBuilder()
                    .addAllProducts(items)
                    .setTotal(result.getTotal())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getProduct(GetProductReq req, StreamObserver<GrpcProductDetail> responseObserver) {
        try {
            ProductDetailResp detail = productService.getProductDetail(req.getProductId());

            List<String> imageUrls = detail.getImages() != null
                    ? detail.getImages().stream()
                        .map(img -> img.getUrl() != null ? img.getUrl() : "")
                        .collect(Collectors.toList())
                    : List.of();

            responseObserver.onNext(GrpcProductDetail.newBuilder()
                    .setId(detail.getProductId())
                    .setName(detail.getName() != null ? detail.getName() : "")
                    .setPrice(detail.getPrice() != null ? detail.getPrice().toPlainString() : "0")
                    .setDescription(detail.getDescription() != null ? detail.getDescription() : "")
                    .addAllImages(imageUrls)
                    .setCategoryId(detail.getCategoryId() != null ? detail.getCategoryId() : 0)
                    .setStatus(detail.getStatus() != null ? detail.getStatus() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listCategories(Empty req, StreamObserver<ListCategoriesResp> responseObserver) {
        try {
            List<CategoryTreeResp> tree = categoryService.getCategoryTree();
            List<GrpcCategoryItem> grpcItems = tree.stream()
                    .map(this::toGrpcCategory)
                    .collect(Collectors.toList());

            responseObserver.onNext(ListCategoriesResp.newBuilder()
                    .addAllCategories(grpcItems)
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private GrpcCategoryItem toGrpcCategory(CategoryTreeResp c) {
        GrpcCategoryItem.Builder builder = GrpcCategoryItem.newBuilder()
                .setId(c.getCategoryId())
                .setName(c.getName() != null ? c.getName() : "");
        if (c.getChildren() != null) {
            c.getChildren().stream().map(this::toGrpcCategory).forEach(builder::addChildren);
        }
        return builder.build();
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  /Users/tester/.local/lib/apache-maven-3.9.6/bin/mvn compile -q
```

期望：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
git add src/main/java/com/ecommerce/product/grpc/
git commit -m "feat: add ProductGrpcService (ListProducts, GetProduct, ListCategories)"
```

---

## Task 6: 实现 UserGrpcService

**Files:**
- Create: `ecommerce/src/main/java/com/ecommerce/user/grpc/UserGrpcService.java`

调用链：`UserGrpcService` → `UserService.register()` / `login()` / `getMe()` / `getAddresses()` / `addAddress()` / `deleteAddress()`

- [ ] **Step 1: 创建目录并写 UserGrpcService**

```bash
mkdir -p /Users/tester/PycharmProjects/Ai学习/ecommerce/src/main/java/com/ecommerce/user/grpc
```

创建 `ecommerce/src/main/java/com/ecommerce/user/grpc/UserGrpcService.java`：

```java
package com.ecommerce.user.grpc;

import com.ecommerce.common.BusinessException;
import com.ecommerce.grpc.proto.*;
import com.ecommerce.user.dto.*;
import com.ecommerce.user.service.UserService;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserService userService;

    @Override
    public void register(GrpcRegisterReq req, StreamObserver<GrpcRegisterResp> responseObserver) {
        try {
            RegisterReq serviceReq = new RegisterReq();
            serviceReq.setPhone(req.getPhone());
            serviceReq.setPassword(req.getPassword());
            serviceReq.setConfirmPassword(req.getPassword()); // gRPC 不单独传 confirmPassword，复用 password
            serviceReq.setUsername(req.getUsername().isBlank() ? null : req.getUsername());

            Object result = userService.register(serviceReq);
            // register() 返回 Map.of("userId", id)
            long userId = 0L;
            if (result instanceof Map<?, ?> map && map.get("userId") instanceof Number n) {
                userId = n.longValue();
            }
            responseObserver.onNext(GrpcRegisterResp.newBuilder().setUserId(userId).build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void login(GrpcLoginReq req, StreamObserver<GrpcLoginResp> responseObserver) {
        try {
            LoginReq serviceReq = new LoginReq();
            serviceReq.setPhone(req.getPhone());
            serviceReq.setPassword(req.getPassword());

            LoginResp resp = userService.login(serviceReq);
            responseObserver.onNext(GrpcLoginResp.newBuilder()
                    .setToken(resp.getToken() != null ? resp.getToken() : "")
                    .setUserId(resp.getUserId())
                    .setRole(resp.getRole() != null ? resp.getRole() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.UNAUTHENTICATED.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getProfile(Empty req, StreamObserver<GrpcUserInfo> responseObserver) {
        try {
            UserInfoResp info = userService.getMe();
            responseObserver.onNext(GrpcUserInfo.newBuilder()
                    .setId(info.getUserId())
                    .setPhone(info.getPhone() != null ? info.getPhone() : "")
                    .setUsername(info.getUsername() != null ? info.getUsername() : "")
                    .setRole(info.getRole() != null ? info.getRole() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listAddresses(Empty req, StreamObserver<GrpcAddressListResp> responseObserver) {
        try {
            List<AddressResp> addresses = userService.getAddresses();
            List<GrpcAddressResp> grpcAddresses = addresses.stream()
                    .map(a -> GrpcAddressResp.newBuilder()
                            .setId(a.getAddressId())
                            .setReceiverName(a.getReceiverName() != null ? a.getReceiverName() : "")
                            .setReceiverPhone(a.getReceiverPhone() != null ? a.getReceiverPhone() : "")
                            .setProvince(a.getProvince() != null ? a.getProvince() : "")
                            .setCity(a.getCity() != null ? a.getCity() : "")
                            .setDistrict(a.getDistrict() != null ? a.getDistrict() : "")
                            .setDetail(a.getDetail() != null ? a.getDetail() : "")
                            .setIsDefault(Boolean.TRUE.equals(a.getIsDefault()))
                            .build())
                    .collect(Collectors.toList());

            responseObserver.onNext(GrpcAddressListResp.newBuilder()
                    .addAllAddresses(grpcAddresses).build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void addAddress(GrpcAddressReq req, StreamObserver<GrpcAddressResp> responseObserver) {
        try {
            AddressReq serviceReq = new AddressReq();
            serviceReq.setReceiverName(req.getReceiverName());
            serviceReq.setReceiverPhone(req.getReceiverPhone());
            serviceReq.setProvince(req.getProvince());
            serviceReq.setCity(req.getCity());
            serviceReq.setDistrict(req.getDistrict());
            serviceReq.setDetail(req.getDetail());
            serviceReq.setIsDefault(req.getIsDefault());

            Long addressId = userService.addAddress(serviceReq);
            responseObserver.onNext(GrpcAddressResp.newBuilder()
                    .setId(addressId)
                    .setReceiverName(req.getReceiverName())
                    .setReceiverPhone(req.getReceiverPhone())
                    .setProvince(req.getProvince())
                    .setCity(req.getCity())
                    .setDistrict(req.getDistrict())
                    .setDetail(req.getDetail())
                    .setIsDefault(req.getIsDefault())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void deleteAddress(GrpcDeleteAddressReq req, StreamObserver<Empty> responseObserver) {
        try {
            userService.deleteAddress(req.getAddressId());
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  /Users/tester/.local/lib/apache-maven-3.9.6/bin/mvn compile -q
```

期望：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
git add src/main/java/com/ecommerce/user/grpc/
git commit -m "feat: add UserGrpcService (Register, Login, GetProfile, Addresses)"
```

---

## Task 7: 实现 CartGrpcService

**Files:**
- Create: `ecommerce/src/main/java/com/ecommerce/cart/grpc/CartGrpcService.java`

调用链：`CartGrpcService` → `CartService.getCart()` / `addItem()` / `updateItem(productId, req)` / `deleteItem(productId)` / `clearCart()`

- [ ] **Step 1: 先确认 AddCartItemReq、UpdateCartItemReq 字段**

```bash
cat /Users/tester/PycharmProjects/Ai学习/ecommerce/src/main/java/com/ecommerce/cart/dto/AddCartItemReq.java
cat /Users/tester/PycharmProjects/Ai学习/ecommerce/src/main/java/com/ecommerce/cart/dto/UpdateCartItemReq.java
```

记录：`AddCartItemReq` 有 `productId`、`quantity` 字段；`UpdateCartItemReq` 有 `quantity` 字段（productId 通过方法参数传入）。

- [ ] **Step 2: 创建目录并写 CartGrpcService**

```bash
mkdir -p /Users/tester/PycharmProjects/Ai学习/ecommerce/src/main/java/com/ecommerce/cart/grpc
```

创建 `ecommerce/src/main/java/com/ecommerce/cart/grpc/CartGrpcService.java`：

```java
package com.ecommerce.cart.grpc;

import com.ecommerce.cart.dto.*;
import com.ecommerce.cart.service.CartService;
import com.ecommerce.common.BusinessException;
import com.ecommerce.grpc.proto.*;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
public class CartGrpcService extends CartServiceGrpc.CartServiceImplBase {

    private final CartService cartService;

    @Override
    public void getCart(Empty req, StreamObserver<GrpcCartResp> responseObserver) {
        try {
            CartResp cart = cartService.getCart();
            List<GrpcCartItemResp> grpcItems = cart.getItems() != null
                    ? cart.getItems().stream()
                        .map(item -> GrpcCartItemResp.newBuilder()
                                .setProductId(item.getProductId())
                                .setProductName(item.getProductName() != null ? item.getProductName() : "")
                                .setPrice(item.getPrice() != null ? item.getPrice().toPlainString() : "0")
                                .setQuantity(item.getQuantity() != null ? item.getQuantity() : 0)
                                .setMainImage(item.getMainImage() != null ? item.getMainImage() : "")
                                .build())
                        .collect(Collectors.toList())
                    : List.of();

            int totalCount = grpcItems.stream().mapToInt(GrpcCartItemResp::getQuantity).sum();

            responseObserver.onNext(GrpcCartResp.newBuilder()
                    .addAllItems(grpcItems)
                    .setTotalCount(totalCount)
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void addItem(GrpcAddCartItemReq req, StreamObserver<Empty> responseObserver) {
        try {
            AddCartItemReq serviceReq = new AddCartItemReq();
            serviceReq.setProductId(req.getProductId());
            serviceReq.setQuantity(req.getQuantity());
            cartService.addItem(serviceReq);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void updateItem(GrpcUpdateCartItemReq req, StreamObserver<Empty> responseObserver) {
        try {
            UpdateCartItemReq serviceReq = new UpdateCartItemReq();
            serviceReq.setQuantity(req.getQuantity());
            cartService.updateItem(req.getProductId(), serviceReq);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void removeItem(GrpcRemoveCartItemReq req, StreamObserver<Empty> responseObserver) {
        try {
            cartService.deleteItem(req.getProductId());
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void clearCart(Empty req, StreamObserver<Empty> responseObserver) {
        try {
            cartService.clearCart();
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  /Users/tester/.local/lib/apache-maven-3.9.6/bin/mvn compile -q
```

期望：`BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
git add src/main/java/com/ecommerce/cart/grpc/
git commit -m "feat: add CartGrpcService (GetCart, AddItem, UpdateItem, RemoveItem, ClearCart)"
```

---

## Task 8: 实现 OrderGrpcService

**Files:**
- Create: `ecommerce/src/main/java/com/ecommerce/order/grpc/OrderGrpcService.java`

调用链：`OrderGrpcService` → `OrderService.createOrder()` / `listOrders()` / `getOrderDetail()` / `payOrder()` / `cancelOrder()`

注意：`AddressSnapshot` 是 Java 对象，需用 `ObjectMapper` 序列化为 JSON 字符串后放入 proto string 字段。

- [ ] **Step 1: 创建目录并写 OrderGrpcService**

```bash
mkdir -p /Users/tester/PycharmProjects/Ai学习/ecommerce/src/main/java/com/ecommerce/order/grpc
```

创建 `ecommerce/src/main/java/com/ecommerce/order/grpc/OrderGrpcService.java`：

```java
package com.ecommerce.order.grpc;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.dto.PageResult;
import com.ecommerce.grpc.proto.*;
import com.ecommerce.order.dto.*;
import com.ecommerce.order.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class OrderGrpcService extends OrderServiceGrpc.OrderServiceImplBase {

    private final OrderService orderService;
    private final ObjectMapper objectMapper; // Spring Boot 自动注入，用于序列化 AddressSnapshot

    @Override
    public void createOrder(GrpcCreateOrderReq req, StreamObserver<GrpcCreateOrderResp> responseObserver) {
        try {
            CreateOrderReq serviceReq = new CreateOrderReq();
            serviceReq.setAddressId(req.getAddressId());
            serviceReq.setProductIds(req.getProductIdsList());

            CreateOrderResp resp = orderService.createOrder(serviceReq);
            responseObserver.onNext(GrpcCreateOrderResp.newBuilder()
                    .setOrderId(resp.getOrderId())
                    .setOrderNo(resp.getOrderNo() != null ? resp.getOrderNo() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listOrders(GrpcListOrdersReq req, StreamObserver<GrpcListOrdersResp> responseObserver) {
        try {
            String status = req.getStatus().isBlank() ? null : req.getStatus();
            int size = req.getSize() == 0 ? 10 : Math.min(req.getSize(), 100);
            PageResult<OrderListItem> result = orderService.listOrders(status, req.getPage(), size);

            List<GrpcOrderSummary> summaries = result.getItems().stream()
                    .map(o -> GrpcOrderSummary.newBuilder()
                            .setId(o.getOrderId())
                            .setOrderNo(o.getOrderNo() != null ? o.getOrderNo() : "")
                            .setStatus(o.getStatus() != null ? o.getStatus() : "")
                            .setTotalAmount(o.getTotalAmount() != null ? o.getTotalAmount().toPlainString() : "0")
                            .setFirstProductName(o.getFirstProductName() != null ? o.getFirstProductName() : "")
                            .setFirstProductMainImage(o.getFirstProductMainImage() != null ? o.getFirstProductMainImage() : "")
                            .setCreatedAt(o.getCreatedAt() != null ? o.getCreatedAt().toString() : "")
                            .build())
                    .collect(Collectors.toList());

            responseObserver.onNext(GrpcListOrdersResp.newBuilder()
                    .addAllOrders(summaries)
                    .setTotal(result.getTotal())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getOrder(GrpcGetOrderReq req, StreamObserver<GrpcOrderDetail> responseObserver) {
        try {
            OrderDetailResp detail = orderService.getOrderDetail(req.getOrderId());

            List<GrpcOrderItemDetail> grpcItems = detail.getItems() != null
                    ? detail.getItems().stream()
                        .map(item -> GrpcOrderItemDetail.newBuilder()
                                .setProductId(item.getProductId())
                                .setProductName(item.getProductName() != null ? item.getProductName() : "")
                                .setPrice(item.getPrice() != null ? item.getPrice().toPlainString() : "0")
                                .setQuantity(item.getQuantity() != null ? item.getQuantity() : 0)
                                .setMainImage(item.getMainImage() != null ? item.getMainImage() : "")
                                .build())
                        .collect(Collectors.toList())
                    : List.of();

            // 将 AddressSnapshot 对象序列化为 JSON 字符串
            String addressSnapshotJson = "";
            if (detail.getAddressSnapshot() != null) {
                try {
                    addressSnapshotJson = objectMapper.writeValueAsString(detail.getAddressSnapshot());
                } catch (JsonProcessingException ex) {
                    log.warn("序列化 AddressSnapshot 失败: {}", ex.getMessage());
                }
            }

            responseObserver.onNext(GrpcOrderDetail.newBuilder()
                    .setId(detail.getOrderId())
                    .setOrderNo(detail.getOrderNo() != null ? detail.getOrderNo() : "")
                    .setStatus(detail.getStatus() != null ? detail.getStatus() : "")
                    .setTotalAmount(detail.getTotalAmount() != null ? detail.getTotalAmount().toPlainString() : "0")
                    .addAllItems(grpcItems)
                    .setAddressSnapshot(addressSnapshotJson)
                    .setCreatedAt(detail.getCreatedAt() != null ? detail.getCreatedAt().toString() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void payOrder(GrpcPayOrderReq req, StreamObserver<Empty> responseObserver) {
        try {
            orderService.payOrder(req.getOrderId());
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void cancelOrder(GrpcCancelOrderReq req, StreamObserver<Empty> responseObserver) {
        try {
            CancelOrderReq serviceReq = new CancelOrderReq();
            serviceReq.setCancelReason(req.getReason());
            orderService.cancelOrder(req.getOrderId(), serviceReq);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  /Users/tester/.local/lib/apache-maven-3.9.6/bin/mvn compile -q
```

期望：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
git add src/main/java/com/ecommerce/order/grpc/
git commit -m "feat: add OrderGrpcService (CreateOrder, ListOrders, GetOrder, Pay, Cancel)"
```

---

## Task 9: 实现 InventoryGrpcService

**Files:**
- Create: `ecommerce/src/main/java/com/ecommerce/inventory/grpc/InventoryGrpcService.java`

调用链：`InventoryGrpcService` → `InventoryService.queryStock()` / `restock()` / `getLogs()`

- [ ] **Step 1: 创建目录并写 InventoryGrpcService**

```bash
mkdir -p /Users/tester/PycharmProjects/Ai学习/ecommerce/src/main/java/com/ecommerce/inventory/grpc
```

创建 `ecommerce/src/main/java/com/ecommerce/inventory/grpc/InventoryGrpcService.java`：

```java
package com.ecommerce.inventory.grpc;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.dto.PageResult;
import com.ecommerce.grpc.proto.*;
import com.ecommerce.inventory.dto.*;
import com.ecommerce.inventory.service.InventoryService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryService inventoryService;

    @Override
    public void getStock(GrpcGetStockReq req, StreamObserver<GrpcStockResp> responseObserver) {
        try {
            StockResp stock = inventoryService.queryStock(req.getProductId());
            responseObserver.onNext(GrpcStockResp.newBuilder()
                    .setProductId(stock.getProductId())
                    .setAvailable(stock.getAvailableStock() != null ? stock.getAvailableStock() : 0)
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void restock(GrpcRestockReq req, StreamObserver<GrpcRestockResp> responseObserver) {
        try {
            RestockReq serviceReq = new RestockReq();
            serviceReq.setQuantity(req.getQuantity());
            serviceReq.setRemark(req.getRemark().isBlank() ? null : req.getRemark());

            RestockResp resp = inventoryService.restock(req.getProductId(), serviceReq);
            responseObserver.onNext(GrpcRestockResp.newBuilder()
                    .setBeforeStock(resp.getBeforeStock() != null ? resp.getBeforeStock() : 0)
                    .setAfterStock(resp.getAfterStock() != null ? resp.getAfterStock() : 0)
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getInventoryLogs(GrpcGetLogsReq req, StreamObserver<GrpcInventoryLogsResp> responseObserver) {
        try {
            int size = req.getSize() == 0 ? 10 : Math.min(req.getSize(), 100);
            PageResult<InventoryLogItem> result = inventoryService.getLogs(req.getProductId(), req.getPage(), size);

            List<GrpcInventoryLogItem> grpcLogs = result.getItems().stream()
                    .map(log -> GrpcInventoryLogItem.newBuilder()
                            .setId(log.getLogId())
                            .setChangeType(log.getChangeType() != null ? log.getChangeType() : "")
                            .setChangeQuantity(log.getChangeQuantity() != null ? log.getChangeQuantity() : 0)
                            .setCreatedAt(log.getCreatedAt() != null ? log.getCreatedAt().toString() : "")
                            .build())
                    .collect(Collectors.toList());

            responseObserver.onNext(GrpcInventoryLogsResp.newBuilder()
                    .addAllLogs(grpcLogs)
                    .setTotal(result.getTotal())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  /Users/tester/.local/lib/apache-maven-3.9.6/bin/mvn compile -q
```

期望：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
git add src/main/java/com/ecommerce/inventory/grpc/
git commit -m "feat: add InventoryGrpcService (GetStock, Restock, GetInventoryLogs)"
```

---

## Task 10: 实现 RefundGrpcService

**Files:**
- Create: `ecommerce/src/main/java/com/ecommerce/refund/grpc/RefundGrpcService.java`

调用链：`RefundGrpcService` → `RefundService.applyRefund()` / `getRefundDetail()` / `adminListRefunds()`

- [ ] **Step 1: 创建目录并写 RefundGrpcService**

```bash
mkdir -p /Users/tester/PycharmProjects/Ai学习/ecommerce/src/main/java/com/ecommerce/refund/grpc
```

创建 `ecommerce/src/main/java/com/ecommerce/refund/grpc/RefundGrpcService.java`：

```java
package com.ecommerce.refund.grpc;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.dto.PageResult;
import com.ecommerce.grpc.proto.*;
import com.ecommerce.refund.dto.*;
import com.ecommerce.refund.service.RefundService;
import com.ecommerce.security.UserContext;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
public class RefundGrpcService extends RefundServiceGrpc.RefundServiceImplBase {

    private final RefundService refundService;

    @Override
    public void applyRefund(GrpcApplyRefundReq req, StreamObserver<GrpcRefundResp> responseObserver) {
        try {
            ApplyRefundReq serviceReq = new ApplyRefundReq();
            serviceReq.setReason(req.getReason());

            ApplyRefundResp resp = refundService.applyRefund(req.getOrderId(), serviceReq);
            responseObserver.onNext(GrpcRefundResp.newBuilder()
                    .setRefundId(resp.getRefundId())
                    .setRefundNo(resp.getRefundNo() != null ? resp.getRefundNo() : "")
                    .setRefundAmount(resp.getRefundAmount() != null ? resp.getRefundAmount().toPlainString() : "0")
                    .setStatus(resp.getStatus() != null ? resp.getStatus() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getRefund(GrpcGetRefundReq req, StreamObserver<GrpcRefundDetailResp> responseObserver) {
        try {
            RefundDetailResp detail = refundService.getRefundDetail(req.getRefundId());
            responseObserver.onNext(GrpcRefundDetailResp.newBuilder()
                    .setRefundId(detail.getRefundId())
                    .setOrderId(detail.getOrderId())
                    .setRefundNo(detail.getRefundNo() != null ? detail.getRefundNo() : "")
                    .setStatus(detail.getStatus() != null ? detail.getStatus() : "")
                    .setRefundAmount(detail.getRefundAmount() != null ? detail.getRefundAmount().toPlainString() : "0")
                    .setReason(detail.getReason() != null ? detail.getReason() : "")
                    .setCreatedAt(detail.getCreatedAt() != null ? detail.getCreatedAt().toString() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listRefunds(GrpcListRefundsReq req, StreamObserver<GrpcListRefundsResp> responseObserver) {
        try {
            // ListRefunds 仅限管理员
            if (!UserContext.isAdmin()) {
                responseObserver.onError(Status.PERMISSION_DENIED
                        .withDescription("仅管理员可查询退款列表").asRuntimeException());
                return;
            }

            String status = req.getStatus().isBlank() ? null : req.getStatus();
            int size = req.getSize() == 0 ? 10 : Math.min(req.getSize(), 100);
            PageResult<RefundListItem> result = refundService.adminListRefunds(status, null, req.getPage(), size);

            List<GrpcRefundDetailResp> grpcRefunds = result.getItems().stream()
                    .map(r -> GrpcRefundDetailResp.newBuilder()
                            .setRefundId(r.getRefundId())
                            .setOrderId(r.getOrderId())
                            .setRefundNo(r.getRefundNo() != null ? r.getRefundNo() : "")
                            .setStatus(r.getStatus() != null ? r.getStatus() : "")
                            .setRefundAmount(r.getRefundAmount() != null ? r.getRefundAmount().toPlainString() : "0")
                            .setReason(r.getReason() != null ? r.getReason() : "")
                            .setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : "")
                            .build())
                    .collect(Collectors.toList());

            responseObserver.onNext(GrpcListRefundsResp.newBuilder()
                    .addAllRefunds(grpcRefunds)
                    .setTotal(result.getTotal())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  /Users/tester/.local/lib/apache-maven-3.9.6/bin/mvn compile -q
```

期望：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
git add src/main/java/com/ecommerce/refund/grpc/
git commit -m "feat: add RefundGrpcService (ApplyRefund, GetRefund, ListRefunds)"
```

---

## Task 11: 启动应用并用 grpcurl 端到端验证

**Files:** 无新建文件

- [ ] **Step 1: 运行全部现有单元测试确保未破坏原有功能**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  /Users/tester/.local/lib/apache-maven-3.9.6/bin/mvn test -q
```

期望：`BUILD SUCCESS`，所有现有测试通过（gRPC 层无单元测试，全部绿灯）

- [ ] **Step 2: 确认 grpcurl 已安装**

```bash
which grpcurl || brew install grpcurl
grpcurl --version
```

期望：输出 `grpcurl v1.x.x`

- [ ] **Step 3: 启动应用（需要 MySQL + Redis 已在运行）**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home \
  /Users/tester/.local/lib/apache-maven-3.9.6/bin/mvn spring-boot:run \
  > /tmp/ecommerce-grpc.log 2>&1 &
echo "Spring Boot PID: $!"
```

等待 20 秒，然后检查是否正常启动：

```bash
sleep 20 && grep -E "Started EcommerceApplication|gRPC Server started|Netty started on port" /tmp/ecommerce-grpc.log | tail -5
```

期望：看到 `Started EcommerceApplication` 以及 gRPC 在 9090 端口启动的日志

- [ ] **Step 4: 验证服务反射**

```bash
grpcurl -plaintext localhost:9090 list
```

期望输出（顺序可能不同）：
```
ecommerce.CartService
ecommerce.InventoryService
ecommerce.OrderService
ecommerce.ProductService
ecommerce.RefundService
ecommerce.UserService
grpc.reflection.v1alpha.ServerReflection
```

- [ ] **Step 5: 测试公开接口——商品列表**

```bash
grpcurl -plaintext \
  -d '{"page": 0, "size": 3}' \
  localhost:9090 ecommerce.ProductService/ListProducts
```

期望：返回 JSON，包含 `products` 数组（非空）和 `total` 字段

- [ ] **Step 6: 测试公开接口——库存查询**

```bash
grpcurl -plaintext \
  -d '{"product_id": 1}' \
  localhost:9090 ecommerce.InventoryService/GetStock
```

期望：返回 `{"productId": "1", "available": <数字>}`

- [ ] **Step 7: 测试鉴权拦截——无 Token 访问受保护接口应返回 UNAUTHENTICATED**

```bash
grpcurl -plaintext \
  -d '{}' \
  localhost:9090 ecommerce.CartService/GetCart
```

期望：
```
ERROR:
  Code: Unauthenticated
  Message: 缺少 Authorization Token
```

- [ ] **Step 8: 登录获取 Token**

```bash
GRPC_RESP=$(grpcurl -plaintext \
  -d '{"phone": "13800000000", "password": "Admin1234"}' \
  localhost:9090 ecommerce.UserService/Login)
echo "$GRPC_RESP"
TOKEN=$(echo "$GRPC_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
echo "Token prefix: ${TOKEN:0:40}..."
```

期望：`token` 字段为 `eyJ...` 开头的 JWT 字符串

- [ ] **Step 9: 测试鉴权接口——购物车**

```bash
grpcurl -plaintext \
  -H "authorization: Bearer $TOKEN" \
  -d '{}' \
  localhost:9090 ecommerce.CartService/GetCart
```

期望：返回 JSON，包含 `items`（数组，可能为空）和 `totalCount`

- [ ] **Step 10: 测试鉴权接口——订单列表**

```bash
grpcurl -plaintext \
  -H "authorization: Bearer $TOKEN" \
  -d '{"page": 0, "size": 5}' \
  localhost:9090 ecommerce.OrderService/ListOrders
```

期望：返回 JSON，包含 `orders` 数组和 `total` 字段

- [ ] **Step 11: 测试公开接口——分类树**

```bash
grpcurl -plaintext \
  -d '{}' \
  localhost:9090 ecommerce.ProductService/ListCategories
```

期望：返回 JSON，包含 `categories` 数组

- [ ] **Step 12: 验证原有 HTTP 接口未受影响**

```bash
curl -s "http://localhost:8082/api/products?size=3" | python3 -m json.tool | head -8
```

期望：正常返回 HTTP JSON 响应，`"code": 0`

- [ ] **Step 13: 提交最终状态**

```bash
cd /Users/tester/PycharmProjects/Ai学习/ecommerce
git add -A
git commit -m "feat: gRPC layer complete — 6 modules, auth interceptor, server reflection on :9090"
```

---

## Spec 覆盖检查

| Spec 要求 | 对应 Task |
|-----------|-----------|
| gRPC Server :9090 | Task 1 |
| HTTP :8082 不变 | 不改动，Task 11 Step 12 验证 |
| net.devh starter 3.1.0.RELEASE | Task 0 |
| os-maven-plugin 扩展 + protobuf-maven-plugin | Task 0 |
| 6 个 proto 文件（含 google.protobuf.Empty） | Task 2 |
| proto 编译验证 | Task 3 |
| GrpcAuthInterceptor（JWT + Redis 黑名单 + passwordVersion） | Task 4 |
| 公开白名单 6 个方法 | Task 4 |
| @GrpcGlobalServerInterceptor 标注在 @Bean 方法上 | Task 4 |
| ProductGrpcService | Task 5 |
| UserGrpcService | Task 6 |
| CartGrpcService | Task 7 |
| OrderGrpcService（ObjectMapper 序列化 AddressSnapshot） | Task 8 |
| InventoryGrpcService | Task 9 |
| RefundGrpcService（ListRefunds Admin 鉴权） | Task 10 |
| grpc.server.reflection.enabled | Task 1 |
| grpcurl 端到端验证（反射、公开、鉴权、无 token 拦截） | Task 11 |
| 现有单元测试全部通过 | Task 11 Step 1 |
