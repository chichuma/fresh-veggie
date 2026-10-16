# 鲜蔬到家 FreshVeggie

> 社区生鲜电商后端,主打 **次日达 + 时段配送**:把产地直采的蔬菜从分拣仓按时段送到用户楼下。

基于 **Spring Boot 3.5 + MyBatis-Plus + Spring StateMachine** 的多模块后端服务,覆盖生鲜商品、分类、配送时段、购物车、订单状态机、地址簿、员工管理与数据报表等核心链路。**生鲜领域建模、5 态订单状态机、时段配送**是本项目的差异化核心。

```
                           ┌─────────────────────────────────────────────┐
                           │      Spring Boot 3.5.14 / JDK 21             │
                           └──────────────────────────┬──────────────────┘
                                                      │
        ┌──────────────────────────┬──────────────────┴───────────────────┬──────────────────┐
        │                          │                                      │                  │
┌───────▼────────┐          ┌──────▼──────┐                        ┌──────▼───────┐        ┌───▼─────┐
│  Controller    │  ──────► │   Service   │  ──────►  Mapper        │  StateMachine │        │ Task    │
│  /admin,/user  │          │ (业务编排)   │      (MyBatis-Plus)     │  订单状态机    │        │ 定时任务 │
└───────┬────────┘          └──────┬──────┘                        └──────┬───────┘        └───┬─────┘
        │                          │                                      │                    │
        │                          └───────────────┬──────────────────────┘                    │
        │                                          ▼                                           │
        │                                ┌───────────────────┐                                │
        └──────────────────────────────► │     MySQL 8       │ ◄───────────────────────────────┘
                                         │  fresh_veggie_db  │
                                         └───────────────────┘
        Redis: 幂等 / 店铺状态 / 商品缓存        Redisson: 分布式锁防超卖
        Aliyun OSS: 商品图片(未配置时本地兜底)   WebSocket: 新订单实时提醒(STOMP)
```

---

## 项目亮点

- **生鲜领域建模**:商品内置产地、净含量规格、单位、保质期、存储条件(冷藏/常温/冷冻)、预售等专属字段,**并全部贯通到 C 端接口**(可看、可筛、可展示)。
- **次日达 + 时段配送**:`delivery_slot` 配送时段表 + 订单记录 `delivery_date + start_time + end_time` 快照,把"按时段送达"做成可预约资源。订单存时段**起止时间**而非 `slot_id` —— 时段行改名/删除都不影响历史订单展示。
- **5 态订单状态机**:Spring StateMachine 驱动 `待支付 → 备货中 → 配送中 → 已完成 / 已取消`,状态流转集中管理,杜绝 if-else 散落。含 `4 已完成 → 5 已取消` 的管理端退款流转(`ADMIN_CANCEL`)。
- **幂等 + 防超卖**:`@Idempotent`(Redis SETNX)防重复提交;Redisson 分布式锁 + `stock >= n` 原子扣减防超卖,取消自动回补库存。
- **订单快照与金额拆分**:下单即写商品/地址/时段快照,`goods_amount / delivery_fee / total_amount` 分列,历史订单与报表口径清晰。
- **工程化**:MDC TraceId 全链路日志、MetaObjectHandler 审计字段自动填充、构造器注入(`@RequiredArgsConstructor`)统一。

## 技术栈

| 类别 | 组件 | 版本 | 用途 |
| --- | --- | --- | --- |
| 语言 / 运行时 | JDK | 21 | |
| 框架 | Spring Boot | 3.5.14 | |
| 持久化 | **MyBatis-Plus** | 3.5.7 | Mapper 通用 CRUD + 内置分页插件 |
| 数据库 | MySQL | 8.x | 主存储 `fresh_veggie_db`(10 张表:9 张业务表 + 1 张压测快照表 `bench_stock_snapshot`);驱动 mysql-connector-j 9.7.0(由 Boot 托管) |
| 缓存 | Redis(Lettuce) | 7.x | 幂等键、店铺状态、商品缓存 |
| 缓存 | Caffeine | 3.2.4 | 本地热点缓存 |
| 协调 | Redisson | 3.52.0 | 分布式锁(防超卖);适配模块 `redisson-spring-data-35` |
| 安全 | Spring Security + jjwt | 6.5.10 / 0.12.7 | 密码 BCrypt、双 Token JWT |
| 业务编排 | Spring StateMachine | 4.0.2 | 订单状态机 |
| 文件 | Aliyun OSS SDK | 3.18.5 | 商品图片存储(未配置时本地磁盘兜底) |
| 实时 | Spring WebSocket (STOMP) | 3.5.14 | 新订单提醒,端点 `/ws`、广播主题 `/topic/public` |
| 文档 | SpringDoc OpenAPI | 2.8.9 | Swagger UI `/swagger-ui/index.html` |

> 依赖版本统一由父 POM 的 `<properties>` + `<dependencyManagement>` 管理,子模块不写版本号。
> 其中 `netty.version`、`commons-lang3.version` 是**覆盖 Spring Boot 托管值**的安全修复项(详见父 POM 注释)。

## 项目结构

```
fresh-veggie/
├── pom.xml                                   # 父工程(版本集中管理)
├── freshveggie.sql                           # 建库脚本(10 张表,只建结构)
├── freshveggie-seed.sql                      # 最小种子数据(staff/category/delivery_slot)
├── fresh-veggie-common/                      # 通用组件(零业务依赖)
│   └── .../annotation|aspect|config|constant|context|exception|json|properties|result|utils
├── fresh-veggie-pojo/                        # 数据契约(entity/dto/vo)
│   └── .../entity(9)|dto|dto/pageQuery|vo
└── fresh-veggie-server/                      # 启动 + 业务模块
    └── .../aspect|config|controller/{admin,user}|handler|interceptor|mapper|service|storage|task|websocket
```

模块职责:

- **fresh-veggie-common**:常量、异常、统一返回、工具(JWT/OSS/HTTP/分布式锁/距离)、`@Idempotent` 注解与切面、缓存与 OSS 配置。
- **fresh-veggie-pojo**:纯数据契约,9 张表实体 + DTO/VO,生鲜字段齐备。
- **fresh-veggie-server**:Controller → Service(业务编排/状态机)→ Mapper(MyBatis-Plus),外加定时任务、WebSocket(STOMP)、审计字段填充、TraceId 拦截器、本地存储解析。

## 核心业务能力

| 能力 | 说明 |
| --- | --- |
| 商品管理 | CRUD、图片上传(OSS/本地兜底)、上下架、预售标记;**产地/净含量/单位/保质期/存储条件全部出参**,可按存储类型/预售筛选 |
| 分类管理 | 单品分类,启停用,排序 |
| **配送时段** | 管理端维护时段(上午/下午/傍晚…),C 端拉取可预约时段;订单选择日期 + 时段 |
| 购物车 | 纯单品购物车,**只存商品 id 与数量**(`cart_item` 无商品快照列),重复加购数量叠加;展示时由 `CartItemVO` 实时回填商品名/图/规格/单价/小计 —— 商品改价后购物车金额立即跟随 |
| 订单 | 下单 = 校验(地址归属/时段/配送范围/营业状态)→ 防超卖扣库存 → 写订单 + 明细 + 快照 → 清购物车;取消自动回补库存。地址坐标落库,配送范围校验走本地 Haversine |
| 订单状态机 | 待支付 → 备货中 → 配送中 → 已完成 / 已取消(含 4→5 退款流转),5 态合法流转 + 非法操作拦截;支付与超时取消均走**条件更新**,避免"已支付却被覆盖为已取消" |
| 地址簿 | 省市区编码 + 名称、标签、默认地址 |
| 员工管理 | 角色(管理员/运营/分拣员)、BCrypt 密码、分页、启停用 |
| 数据报表 | 每日营业额、用户统计、订单统计、销量 Top10、经营数据(工作台) |

## 架构设计亮点

- **5 态订单状态机**:`OrderStateMachineConfig` 定义合法流转,5 个状态类(`PendingPayment/Preparing/Delivering/Completed/Cancelled`)各自只允许合法动作,非法抛异常;支付/配送/完成/取消均由状态机驱动,落库前从状态机读取目标态。
- **`@Idempotent` 幂等切面**:注解 + Redis SETNX 占位,业务失败主动释放,防重复下单。
- **Redisson 分布式锁**:`RedissonLockUtil.tryLock(key, wait, lease, supplier)` 一行完成加锁-执行-释放;库存用 `update ... set stock=stock-n where stock>=n` 原子扣减。
- **订单快照 + 金额拆分**:地址(收货人/电话/详址)、配送时段、商品(名/图/规格/单位/产地/单价)下单即快照;`goods_amount + delivery_fee = total_amount`。
- **MetaObjectHandler 审计填充**:MyBatis-Plus 自动注入 `create_time/update_time/create_user/update_user`(按 setter 存在性判断,兼容用户侧表),Service 零样板。
- **MyBatis-Plus 分页插件**:`PaginationInnerInterceptor` 替代 PageHelper,分页联查 + 报表聚合 SQL 保留 XML。
- **MDC TraceId 全链路**:`TraceIdInterceptor` 注入 traceId,logback `%X{traceId}` 输出,响应头 `X-Trace-Id` 回传。
- **构造器注入为主**:绝大多数类用 `@RequiredArgsConstructor` + `final` 字段。
  例外(字段注入):`IdempotentAspect`、`AliOssUtil` —— 前者是切面、后者是静态工具类,
  改造成本高于收益,暂留。

## 性能与容量

`/user/order/submit`（下单）是本项目最重的接口。**实测最大 526 QPS，推荐生产水位 470~500 QPS。**

**并发-吞吐曲线**（闭环压测，每档 12000 单、0 错误）：

| 并发 | QPS | P50 | P99 |
| ---: | ---: | ---: | ---: |
| 50 | **526** | 93 ms | 112 ms |
| 100 | 525 | 187 ms | 248 ms |
| 400 | 514 | 768 ms | 848 ms |
| 1600 | 488 | 3202 ms | 3514 ms |
| 3200 | 472 | 6442 ms | 7400 ms |

**曲线的形状就是结论**：吞吐几乎恒定，而 **P50 随并发线性增长** ——
典型的固定服务速率特征。所以**要用延迟倒推并发，不要用吞吐倒推**：
若要求 P99 < 1s，取并发 ≤ 400（~515 QPS）。

**当前瓶颈**：`product` 热行 X 锁。压测中所有订单买同一商品，
`deductStock` 在那唯一一行上串行。要突破 526 只有两条路（代价都不小）：
扣库存拆独立短事务（破坏原子性，需补偿逻辑），或业务上按库存分桶避免热点行。

> **关于压测资产**：完整的测试方法、踩坑记录与可复用工具位于本地 `bench/` 目录
> （**未纳入本仓库**：含 131 MB 的 JFR 采样与数据库备份、以及可用于冒充用户的
> JWT 令牌文件，不适合公开）。其中的关键结论已摘录到本节上方。

**已知的容量相关配置**（`application-local.yml`）：

| 项 | 值 | 说明 |
| --- | --- | --- |
| HikariCP `maximum-pool-size` | 150 | 实测有富余，不是瓶颈 |
| MySQL `max_connections` | 300 | |
| MySQL `innodb_lock_wait_timeout` | 3 | 从默认 50s 调低，让锁等待快速失败而不是拖成长尾 |
| `veggie.shop.max-delivery-lead-days` | 30 | 可预约配送的最大提前天数 |

## 快速启动

环境要求:JDK 21、Maven 3.8+、MySQL 8.x、Redis 7.x。

1. **初始化数据库**(10 张表):

   ```bash
   # ① 建结构（脚本内含 DROP DATABASE，会清空同名库）
   mysql --default-character-set=utf8mb4 -uroot -p < freshveggie.sql
   # ② 灌入最小种子数据（3 个后台账号 + 8 个分类 + 5 个配送时段）
   mysql --default-character-set=utf8mb4 -uroot -p fresh_veggie_db < freshveggie-seed.sql
   ```

   数据库名:`fresh_veggie_db`。**两步都要做** —— 只建结构的话没有分类/时段/后台账号,
   商品挂不上分类、下单选不到时段、后台也登不进去。

   > 脚本共 10 张表：**9 张业务表**，外加 1 张 `bench_stock_snapshot`
   > （性能压测用的库存快照表，应用代码零访问）。保留它是因为它已是线上库结构的一部分，
   > 去掉会让"用脚本建的新库"与"真实库"不一致。

   > `--default-character-set=utf8mb4` 不能省:两个脚本都含中文数据,
   > Windows 下 mysql 客户端默认按 GBK 读取会报 `Data too long for column 'name'`。

2. **配置本地凭据**(该文件**不进仓库**):

   `application-local.yml` 已被 `.gitignore` 忽略。先复制模板:

   ```bash
   cp fresh-veggie-server/src/main/resources/application-local.yml.example \
      fresh-veggie-server/src/main/resources/application-local.yml
   ```

   然后按需注入。三项都有环境变量覆盖,不填则用本地默认值:

   ```bash
   export DB_PASSWORD=your_mysql_password
   export BAIDU_AK=your_baidu_ak                          # https://lbsyun.baidu.com/
   export JWT_USER_SECRET=$(openssl rand -base64 48)      # 部署前必须换
   export JWT_SECRET=$(openssl rand -base64 48)
   ```

   > ⚠️ **JWT 密钥必须替换成随机值再部署。** 沿用示例值等于任何人都能用
   > `JwtUtil.createJWT(...)` 伪造任意用户的 token。HS256 要求 ≥ 32 字节。
   >
   > ⚠️ **不要提交 `application-local.yml`。** 它含数据库密码、百度 AK、JWT 密钥。
   > 一旦进入 git 历史,删文件也撤不回来 —— 只能去平台轮换密钥。

3. **启动 Redis**(分布式锁与缓存依赖它):

   ```bash
   # Windows
   Start-Service Redis
   # 或 Docker
   docker run -d -p 6379:6379 redis:7
   ```

   > **Redis 未启动时应用仍可启动**,但控制台会打印醒目 error 横幅,
   > 且下单/商品缓存/幂等校验等依赖 Redis 的功能不可用。详见下方「降级行为」。

4. **启动**:

   ```bash
   mvn -pl fresh-veggie-server spring-boot:run
   ```

5. **访问接口文档**:<http://localhost:8088/swagger-ui/index.html>(端口 8088)

   > 本地联调提示:微信 `appid/secret` 默认占位,`/user/user/login` 会走不通;
   > 此时可在 `user` 表手动插入一条 openid,用项目自己的 `JwtUtil.createJWT(userSecretKey, ...)`
   > 签发测试 token(claims 需含 `userId` 与 `openid`),即可调通 C 端全部接口。

## 降级行为

项目对**外部依赖不可用**做了降级,保证开发期"起得来的先起来":

| 依赖 | 不可用时的行为 |
| --- | --- |
| Redis | 控制台打印 error 横幅(含地址与根因),应用**照常启动**,`/swagger-ui/index.html` 可访问;下单/缓存/幂等接口在调用时报错 |
| 阿里云 OSS | 未配置 `veggie.alioss.access-key-id` 时,文件上传自动落本地磁盘(由 `LocalStorageRoot` 统一解析目录) |
| 百度地图 | 地理编码失败时,**配送范围校验降级放行**并记录 ERROR,不阻断下单。距离为本地直线距离(Haversine)计算,不依赖路线规划接口 |

## 缓存策略

项目里有**两套互不相干的缓存**,不要混淆:

| 缓存 | 位置 | 范围 | 当前用途 |
| --- | --- | --- | --- |
| **Redis Hash** | `veg:shop:category:products` | 分布式(多实例共享) | C 端「按分类查商品」`ProductServiceImpl#getProductVoListByCategoryId` 手工读写 |
| **Caffeine 本地** | `CacheConfig.caffeineCacheManager()` | 进程内 | `@Cacheable` 注解的唯一后端 |

Caffeine 侧注册了 3 个缓存名,但**实际只有 1 个在用**:

| 缓存名 | 状态 |
| --- | --- |
| `deliverySlotCache` | **正在使用** —— `DeliverySlotServiceImpl` 的 `@Cacheable`/`@CachePut`/`@CacheEvict` 共 7 处 |
| `categoryCache` | 预留,当前**零引用** |
| `hotProductCache` | 预留,当前**零引用** |

> ⚠️ **商品与分类的数据目前不走这两个 Caffeine 缓存**,走的是上面的 Redis Hash。
> 所以商品缓存是分布式的,而这两个预留项是进程内的 —— 将来迁移前先确认
> 能否接受"多实例各存一份"的语义变化。
>
> ⚠️ `CacheConfig` 里的 `caffeineCacheManager` 必须是容器里**唯一**的 `CacheManager`。
> 2026-09-28 之前 `AliOssConfig` 也注册了一个 `redisCacheManager`,缓存切面注入了后者
> (它只认识显式创建过的缓存),导致 `checkDeliverySlot` 每次都抛
> 「Cannot find cache named 'deliverySlotCache'」—— **整个下单链路 100% 失败**。
> 加第二个 `CacheManager` bean 会重现这个问题。

## API 文档

- 浏览器可视化:**`http://localhost:8088/swagger-ui/index.html`**(SpringDoc Swagger UI)
- OpenAPI JSON:**`http://localhost:8088/v3/api-docs`**

Controller 分组:

- **管理端** `/admin/**`:Staff / Category / Product / Order / Report / Shop / Workspace / **DeliverySlot** / Common
- **用户端** `/user/**`:User / Category / Product / Cart / Order / Address / Shop / **DeliverySlot**
- **实时** `/ws`:STOMP over WebSocket,订阅 `/topic/public` 接收新订单提醒

## 后续规划

- **批次库存** `product_batch`:批次号 / 入库时间 / 效期,FIFO 出库与临期预警(届时 `product.stock` 由批次聚合派生)。
- **预售策略细化**:预售单支付时限、发货日 = 配送日的规则落地。
- **免配送费阈值**:店铺配置 `free_delivery_amount`,下单时动态计算配送费。
- **销量榜缓存**:`sales_count` 聚合改为定时任务 / 事件驱动刷新,报表不直查明细表。
  说明:`product.sales_count` 已于 2026-10-01 接入维护逻辑
  （`OrderStockService`：支付成功 +n、退款/取消 -n），不再是零维护状态。
  下一步可考虑把它改成"由 `order_item` 定时聚合刷新"，避免冗余列与明细不一致。
- **审计字段填充**:已于 2026-10-01 打通 —— `Category/Product/Staff/DeliverySlot`
  四个实体的 `createTime/updateTime/createUser/updateUser` 都补上了
  `@TableField(fill = ...)`，`AuditMetaObjectHandler` 不再是死代码。
  （此前全工程零个 fill 标注，MyBatis-Plus 不会调用填充器，审计人恒为 NULL。）
- **测试重建**:测试目录当前为空,待按新的模块边界重写单元与集成测试。

## License

MIT License。
