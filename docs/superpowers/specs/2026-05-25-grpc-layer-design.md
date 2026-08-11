# gRPC 接口层设计文档

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在现有 Spring Boot 电商系统（HTTP REST :8082）之上，新增 gRPC Server（:9090），两套接口共用同一套 Service 业务层，业务逻辑零改动。

**Architecture:** `net.devh/grpc-spring-boot-starter` 驱动 gRPC Server，与 Spring MVC HTTP Server 共存于同一进程。每个业务模块新增一个 `*GrpcService` 实现类，通过 `@GrpcService` 注解注册。全局 `GrpcAuthInterceptor` 拦截器复用现有 `JwtUtil` + `UserContext` 处理鉴权，公开接口白名单跳过验证。

**Tech Stack:** Spring Boot 3.2.5 · Java 17 · net.devh:grpc-spring-boot-starter:3.1.0.RELEASE · protobuf-java:3.25.3 · io.grpc:grpc-services:1.63.0 · protobuf-maven-plugin:0.6.1

---

## 端口规划

| 协议 | 端口 | 说明 |
|------|------|------|
| HTTP REST | 8082 | 现有，不变 |
| gRPC | 9090 | 新增，同进程 |

## 目录结构（新增部分）

```
src/main/proto/
  ecommerce/
    product.proto
    user.proto
    cart.proto
    order.proto
    inventory.proto
    refund.proto

src/main/java/com/ecommerce/
  grpc/
    interceptor/GrpcAuthInterceptor.java
    GrpcConfig.java
  product/grpc/ProductGrpcService.java
  user/grpc/UserGrpcService.java
  cart/grpc/CartGrpcService.java
  order/grpc/OrderGrpcService.java
  inventory/grpc/InventoryGrpcService.java
  refund/grpc/RefundGrpcService.java
```

---

## Proto 文件设计

### package 约定

所有 proto 文件使用：
```protobuf
syntax = "proto3";
package ecommerce;
option java_package = "com.ecommerce.grpc.proto";
option java_multiple_files = true;
```

### product.proto

```protobuf
service ProductService {
  rpc ListProducts(ListProductsReq) returns (ListProductsResp);
  rpc GetProduct(GetProductReq) returns (ProductDetail);
  rpc ListCategories(Empty) returns (ListCategoriesResp);
}

message ListProductsReq {
  int64 category_id = 1;
  string keyword = 2;
  int32 page = 3;
  int32 size = 4;
}
message ListProductsResp {
  repeated ProductItem products = 1;
  int64 total = 2;
}
message ProductItem {
  int64 id = 1;
  string name = 2;
  string price = 3;
  string main_image = 4;
  int64 category_id = 5;
  string status = 6;
}
message GetProductReq { int64 product_id = 1; }
message ProductDetail {
  int64 id = 1;
  string name = 2;
  string price = 3;
  string description = 4;
  repeated string images = 5;
  int64 category_id = 6;
  string status = 7;
  int32 available_stock = 8;
}
message ListCategoriesResp { repeated CategoryItem categories = 1; }
message CategoryItem {
  int64 id = 1;
  string name = 2;
  int64 parent_id = 3;
  repeated CategoryItem children = 4;
}
message Empty {}
```

### user.proto

```protobuf
service UserService {
  rpc Register(RegisterReq) returns (RegisterResp);     // 公开
  rpc Login(LoginReq) returns (LoginResp);              // 公开
  rpc GetProfile(Empty) returns (UserInfo);             // 需 JWT
  rpc ListAddresses(Empty) returns (AddressListResp);   // 需 JWT
  rpc AddAddress(AddressReq) returns (AddressResp);     // 需 JWT
  rpc DeleteAddress(DeleteAddressReq) returns (Empty);  // 需 JWT
}

message RegisterReq { string phone = 1; string password = 2; string nickname = 3; }
message RegisterResp { int64 user_id = 1; }
message LoginReq { string phone = 1; string password = 2; }
message LoginResp { string token = 1; int64 user_id = 2; string role = 3; }
message UserInfo { int64 id = 1; string phone = 2; string nickname = 3; string role = 4; }
message AddressReq {
  string receiver_name = 1;
  string phone = 2;
  string province = 3;
  string city = 4;
  string district = 5;
  string detail = 6;
  bool is_default = 7;
}
message AddressResp {
  int64 id = 1;
  string receiver_name = 2;
  string phone = 3;
  string province = 4;
  string city = 5;
  string district = 6;
  string detail = 7;
  bool is_default = 8;
}
message AddressListResp { repeated AddressResp addresses = 1; }
message DeleteAddressReq { int64 address_id = 1; }
```

### cart.proto

```protobuf
service CartService {
  rpc GetCart(Empty) returns (CartResp);                    // 需 JWT
  rpc AddItem(AddCartItemReq) returns (Empty);              // 需 JWT
  rpc UpdateItem(UpdateCartItemReq) returns (Empty);        // 需 JWT
  rpc RemoveItem(RemoveCartItemReq) returns (Empty);        // 需 JWT
  rpc ClearCart(Empty) returns (Empty);                     // 需 JWT
}

message CartResp { repeated CartItemResp items = 1; int32 total_count = 2; }
message CartItemResp {
  int64 product_id = 1;
  string product_name = 2;
  string price = 3;
  int32 quantity = 4;
  string main_image = 5;
}
message AddCartItemReq { int64 product_id = 1; int32 quantity = 2; }
message UpdateCartItemReq { int64 product_id = 1; int32 quantity = 2; }
message RemoveCartItemReq { int64 product_id = 1; }
```

### order.proto

```protobuf
service OrderService {
  rpc CreateOrder(CreateOrderReq) returns (CreateOrderResp);  // 需 JWT
  rpc ListOrders(ListOrdersReq) returns (ListOrdersResp);     // 需 JWT
  rpc GetOrder(GetOrderReq) returns (OrderDetail);            // 需 JWT
  rpc PayOrder(PayOrderReq) returns (Empty);                  // 需 JWT
  rpc CancelOrder(CancelOrderReq) returns (Empty);            // 需 JWT
}

message CreateOrderReq { int64 address_id = 1; repeated OrderItemReq items = 2; }
message OrderItemReq { int64 product_id = 1; int32 quantity = 2; }
message CreateOrderResp { int64 order_id = 1; string order_no = 2; }
message ListOrdersReq { int32 page = 1; int32 size = 2; string status = 3; }
message ListOrdersResp { repeated OrderSummary orders = 1; int64 total = 2; }
message OrderSummary {
  int64 id = 1;
  string order_no = 2;
  string status = 3;
  string total_amount = 4;
  string first_product_name = 5;
  string first_product_main_image = 6;
  string created_at = 7;
}
message GetOrderReq { int64 order_id = 1; }
message OrderDetail {
  int64 id = 1;
  string order_no = 2;
  string status = 3;
  string total_amount = 4;
  repeated OrderItemDetail items = 5;
  string address_snapshot = 6;
  string created_at = 7;
}
message OrderItemDetail {
  int64 product_id = 1;
  string product_name = 2;
  string price = 3;
  int32 quantity = 4;
  string main_image = 5;
}
message PayOrderReq { int64 order_id = 1; }
message CancelOrderReq { int64 order_id = 1; string reason = 2; }
```

### inventory.proto

```protobuf
service InventoryService {
  rpc GetStock(GetStockReq) returns (StockResp);              // 公开
  rpc Restock(RestockReq) returns (RestockResp);              // 需 JWT（Admin）
  rpc GetInventoryLogs(GetLogsReq) returns (InventoryLogsResp); // 需 JWT（Admin）
}

message GetStockReq { int64 product_id = 1; }
message StockResp { int64 product_id = 1; int32 available = 2; int32 total = 3; }
message RestockReq { int64 product_id = 1; int32 quantity = 2; }
message RestockResp { int32 new_available = 1; int32 new_total = 2; }
message GetLogsReq { int64 product_id = 1; int32 page = 2; int32 size = 3; }
message InventoryLogsResp { repeated InventoryLogItem logs = 1; int64 total = 2; }
message InventoryLogItem {
  int64 id = 1;
  string type = 2;
  int32 quantity = 3;
  string created_at = 4;
}
```

### refund.proto

```protobuf
service RefundService {
  rpc ApplyRefund(ApplyRefundReq) returns (RefundResp);       // 需 JWT
  rpc GetRefund(GetRefundReq) returns (RefundResp);           // 需 JWT
  rpc ListRefunds(ListRefundsReq) returns (ListRefundsResp);  // 需 JWT（Admin）
}

message ApplyRefundReq { int64 order_id = 1; string reason = 2; }
message GetRefundReq { int64 refund_id = 1; }
message RefundResp {
  int64 id = 1;
  int64 order_id = 2;
  string status = 3;
  string amount = 4;
  string reason = 5;
  string created_at = 6;
}
message ListRefundsReq { int32 page = 1; int32 size = 2; string status = 3; }
message ListRefundsResp { repeated RefundResp refunds = 1; int64 total = 2; }
```

---

## 鉴权设计

### GrpcAuthInterceptor

实现 `io.grpc.ServerInterceptor`：

1. 读取 Metadata key `authorization`（小写），格式 `Bearer <token>`
2. 检查 `FullMethodName` 是否在白名单 → 在则放行
3. 不在白名单但无 token → 返回 `Status.UNAUTHENTICATED`
4. 有 token → `JwtUtil.parseToken(token)`，异常则返回 `Status.UNAUTHENTICATED`
5. 成功 → `UserContext.set(userId, role)`，在 `ServerCall.Listener.onComplete()` 和 `onCancel()` 中清除

**白名单（完整 gRPC 方法名）：**
```
ecommerce.ProductService/ListProducts
ecommerce.ProductService/GetProduct
ecommerce.ProductService/ListCategories
ecommerce.UserService/Register
ecommerce.UserService/Login
ecommerce.InventoryService/GetStock
```

### GrpcConfig

```java
@Configuration
public class GrpcConfig {
    @Bean
    public GrpcAuthInterceptor grpcAuthInterceptor(JwtUtil jwtUtil) {
        return new GrpcAuthInterceptor(jwtUtil);
    }
}
```

用 `@GrpcGlobalServerInterceptor` 标注 `GrpcAuthInterceptor`，使其自动应用到所有 gRPC 服务。

---

## Maven 配置变更

### pom.xml 新增依赖

```xml
<properties>
  <grpc.version>1.63.0</grpc.version>
  <protobuf.version>3.25.3</protobuf.version>
</properties>

<!-- gRPC Spring Boot Starter -->
<dependency>
  <groupId>net.devh</groupId>
  <artifactId>grpc-spring-boot-starter</artifactId>
  <version>3.1.0.RELEASE</version>
</dependency>
<!-- gRPC 反射（grpcurl/Postman 自动发现服务） -->
<dependency>
  <groupId>io.grpc</groupId>
  <artifactId>grpc-services</artifactId>
  <version>${grpc.version}</version>
</dependency>
<!-- Protobuf Java 运行时 -->
<dependency>
  <groupId>com.google.protobuf</groupId>
  <artifactId>protobuf-java</artifactId>
  <version>${protobuf.version}</version>
</dependency>
```

### pom.xml 新增构建插件

```xml
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
        <goal>compile</goal>
        <goal>compile-custom</goal>
      </goals>
    </execution>
  </executions>
</plugin>
<!-- OS 检测插件（供 protoc 下载正确平台二进制） -->
<plugin>
  <groupId>kr.motd.maven</groupId>
  <artifactId>os-maven-plugin</artifactId>
  <version>1.7.1</version>
  <executions>
    <execution>
      <phase>initialize</phase>
      <goals><goal>detect</goal></goals>
    </execution>
  </executions>
</plugin>
```

---

## application.yml 变更

```yaml
grpc:
  server:
    port: 9090
    reflection:
      enabled: true
```

---

## 测试方案

### 安装 grpcurl
```bash
brew install grpcurl
```

### 验证反射
```bash
grpcurl -plaintext localhost:9090 list
# 期望输出：
# ecommerce.ProductService
# ecommerce.UserService
# ecommerce.CartService
# ecommerce.OrderService
# ecommerce.InventoryService
# ecommerce.RefundService
```

### 公开接口测试
```bash
# 商品列表
grpcurl -plaintext -d '{"page":0,"size":3}' \
  localhost:9090 ecommerce.ProductService/ListProducts

# 商品详情
grpcurl -plaintext -d '{"product_id":1}' \
  localhost:9090 ecommerce.ProductService/GetProduct

# 登录
grpcurl -plaintext \
  -d '{"phone":"13800000000","password":"Admin1234"}' \
  localhost:9090 ecommerce.UserService/Login
```

### 需鉴权接口测试
```bash
# 将登录返回的 token 存为变量
TOKEN="<登录返回的token>"

# 查购物车
grpcurl -plaintext \
  -H "authorization: Bearer $TOKEN" \
  -d '{}' localhost:9090 ecommerce.CartService/GetCart

# 查订单列表
grpcurl -plaintext \
  -H "authorization: Bearer $TOKEN" \
  -d '{"page":0,"size":5}' \
  localhost:9090 ecommerce.OrderService/ListOrders
```

### Postman 测试
1. 新建请求，选择 gRPC 协议
2. URL 填 `localhost:9090`
3. 点击 "Use Server Reflection" 自动加载所有服务
4. 选择方法，填写参数，Headers 里加 `authorization: Bearer <token>`

---

## 不改动范围

- 所有 HTTP Controller（`*Controller.java`）保持不变
- 所有 Service 接口和实现（`*Service.java`、`*ServiceImpl.java`）保持不变
- 所有 Mapper、Entity、DTO 保持不变
- Spring Security 配置（`SecurityConfig.java`）保持不变
- MySQL / Redis 数据层保持不变
