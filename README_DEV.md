# E-Commerce Demo System — 本地开发部署指南

## 前置条件

| 工具 | 版本 | 说明 |
|------|------|------|
| Java | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| MySQL | 8.0+ | 本地运行，端口 3306 |
| Redis | 7.x | 本地运行，端口 6379 |

## 1. 初始化数据库

```bash
# 进入项目根目录
mysql -u root -p < sql/init.sql
```

执行后会创建数据库 `ecommerce_demo`，包含 12 张表和初始数据：
- 默认管理员账号：手机号 `13800000000`，密码 `Admin1234`
- 默认分类数据（5个分类）

## 2. 修改配置

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    username: root       # 改为你的MySQL用户名
    password: root       # 改为你的MySQL密码
  data:
    redis:
      password:          # Redis密码（无密码留空）
```

## 3. 启动应用

```bash
mvn spring-boot:run
```

应用启动后：
- 服务地址：http://localhost:8080
- 启动日志中会显示 "Inventory Redis cache initialized: N products"

## 4. 快速验证

### 管理员登录

```bash
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800000000","password":"Admin1234"}'
```

响应：
```json
{"code":0,"msg":"success","data":{"token":"eyJ...","userId":1,"role":"ADMIN"}}
```

### 用户注册

```bash
curl -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"phone":"13912345678","password":"User1234","confirmPassword":"User1234","username":"测试用户"}'
```

### 查询分类树

```bash
curl http://localhost:8080/api/categories/tree
```

### 创建商品（需ADMIN Token）

```bash
export TOKEN="eyJ..."   # 替换为登录返回的token

curl -X POST http://localhost:8080/api/admin/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"categoryId":3,"name":"iPhone 15","description":"Apple iPhone 15","price":5999.00}'
```

### 初始化并补充库存

```bash
# 初始化库存（productId=1，首次）
curl -X POST http://localhost:8080/api/admin/inventory \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":1,"quantity":100}'

# 上架商品
curl -X PUT http://localhost:8080/api/admin/products/1/on-sale \
  -H "Authorization: Bearer $TOKEN"
```

### 完整购物流程

```bash
USER_TOKEN="..."   # 用户登录后的Token

# 1. 加入购物车
curl -X POST http://localhost:8080/api/cart/items \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d '{"productId":1,"quantity":2}'

# 2. 添加收货地址
curl -X POST http://localhost:8080/api/users/addresses \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d '{"receiverName":"张三","receiverPhone":"13812345678","province":"广东省","city":"深圳市","district":"南山区","detail":"科技园路1号","isDefault":true}'

# 3. 创建订单
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d '{"productIds":[1],"addressId":1}'

# 4. 模拟付款
curl -X PUT http://localhost:8080/api/orders/1/pay \
  -H "Authorization: Bearer $USER_TOKEN"

# 5. 管理员发货
curl -X PUT http://localhost:8080/api/admin/orders/1/ship \
  -H "Authorization: Bearer $TOKEN"

# 6. 确认收货
curl -X PUT http://localhost:8080/api/orders/1/confirm \
  -H "Authorization: Bearer $USER_TOKEN"
```

## 5. 运行所有测试

```bash
mvn test
```

> 注意：单元测试使用 Mockito，不需要真实的 MySQL 和 Redis 连接。

## 6. 模块说明

| 模块 | 包 | 关键技术 |
|------|-----|---------|
| 用户体系 | `com.ecommerce.user` | JWT, BCrypt, Redis黑名单 |
| 商品管理 | `com.ecommerce.product` | 状态机, 二级分类树 |
| 库存管理 | `com.ecommerce.inventory` | Redis DECRBY原子扣减 |
| 购物车 | `com.ecommerce.cart` | 纯Redis Hash, TTL 7天 |
| 订单流程 | `com.ecommerce.order` | 7状态机, 24位订单号 |
| 退款流程 | `com.ecommerce.refund` | SELECT FOR UPDATE, REQUIRES_NEW |

## 7. 错误码速查

| 范围 | 模块 |
|------|------|
| 10002 | 全局：无权限操作 |
| 11xxx | 用户体系 |
| 12xxx | 商品管理 |
| 13xxx | 库存管理 |
| 14xxx | 购物车 |
| 15xxx | 订单流程 |
| 16xxx | 退款流程 |
