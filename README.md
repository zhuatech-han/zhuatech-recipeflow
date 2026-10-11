# RecipeFlow · 知华菜谱成本与厨房备料协同

[中文](README.md) | [English](README.en.md)

![知华科技](frontend/public/brand/logo.jpg)

知华科技（上海如静知华信息科技有限公司）提供的**公开源码学习版／非商业源码版**。使用 Java 21、Spring Boot、Vue 3、MySQL 8.4，将标准配方、食材报价、独立审核、供餐备料与厨房实际耗用连接起来。适用于连锁餐饮研发厨房、团餐备料团队和中央厨房的流程学习与非商业评估。官网：[https://www.zhuatech.cn/](https://www.zhuatech.cn/)。未经书面授权不得商用。

## 从一份配方到一批供餐

1. 维护食材采购数量、单位、采购总价和净料率。
2. 研发人员编写配方，可引用已批准的半成品／子配方版本。
3. 指定独立审核人批准，冻结该版配方、食材报价和成本。
4. 计划人员选择批准配方和供餐份数，生成汇总备料需求并指定执行人。
5. 厨房登记真实毛料耗用、其中损耗及实际产出；完工冻结结果，查看计划差异。

**历史成本不会随报价修改而变化。** 当前报价试算另行展示；批准配方要修订时先转入草稿、再次送审。已发布备料计划继续使用原快照。

## 已实现的工作台

| 角色／业务域 | 当前功能 |
| --- | --- |
| 配方研发端 | 食材分类与报价维护、净料率、直接食材和批准子配方、份数换算、配方草稿与归档、当前报价试算、历史版本与 CSV |
| 独立审核端 | 指定待审配方、批准或退回、历史编辑人隔离、报价变化检查、说明与记录 |
| 计划端 | 批准配方选取、供餐日期与份数、食材合并、指定厨房执行人、草稿修改、发布及执行前取消 |
| 厨房执行端 | 本人指定计划、制作说明、开始执行、原子保存逐食材耗用与损耗、逐菜产出、说明、完工冻结、CSV |
| 管理端 | 账号、BCrypt 密码、角色权限、厨房与 IANA 时区、菜单开关与排序、食材分类、容量参数、最后管理员保护、审计 |
| 查询 | 名称／编号搜索、状态／分类筛选、排序与分页、批准配方和完工统计、冻结价格耗用估值与成本差异 |

页面同时支持中文和英文，手机可查阅及登记厨房执行。读权限不自动赋予写权限；食材改价另需 `quote`，配方编辑需 `design`，备料编制需 `plan`，厨房办理需 `execute`。

## 当前运行页面

截图使用独立测试厨房的虚构数据，不代表客户案例。

### 登录

![登录：使用独立厨房工作账号](docs/screenshots/01-login.jpg)

### 配方研发工作台

![用户端首页：配方搜索、状态和指定审核](docs/screenshots/02-recipes.jpg)

### 批准配方与成本

![核心业务：冻结净料率、食材需求和版本成本](docs/screenshots/03-cost.jpg)

### 厨房备料执行

![厨房执行：供餐需求、实际耗用和完工差异](docs/screenshots/04-preparation.jpg)

### 成本与执行统计

![统计：完工食材估值、份数和状态](docs/screenshots/05-reports.jpg)

### 后台账号

![管理端：工作账号、厨房和角色](docs/screenshots/06-accounts.jpg)

### 角色与权限

![权限：配方、报价、计划与执行分别授权](docs/screenshots/07-roles.jpg)

[完工差异](docs/screenshots/10-completion.jpg) · [子配方制作步骤](docs/screenshots/11-components.jpg) · [英文页面](docs/screenshots/08-english.jpg) · [手机执行页面](docs/screenshots/09-mobile.jpg)

## 计算口径与业务边界

- 单币种人民币，仅核算食材。**不含人工、能源、租金、税费，不是完整利润。** 实际耗用按计划毛料加权单价估值，不是实际采购成本或库存过账。
- kg/g、l/ml、pc 分别对应重量、体积、计数，只能同维度换算，不推定密度。食材稳定编号、所属厨房和计量维度不可改写。
- 用量／采购数量／实际耗用最多3位小数，报价4位，净料率2位，范围0.01%–100%。毛料＝净料÷净料率；服务端以十进制计算，净料6位、毛料6位向上舍入、单价12位、行成本6位，合计最后到分。详见[计算规则](docs/architecture.md)。
- 子配方引用批准版本的结构和份数；送审时刷新所有展开食材当前报价和净料率。最多40行、展开100种食材、5层依赖、200项子配方制作步骤，拒绝跨版本循环。备料最多20种菜品，每种1–100000份。
- 送审后报价变动须退回重送。指定审核人不能是任何历史编辑人；管理员也不能代替指定审核人或执行人。
- 发布后需求不可改动，执行中不能取消。完工要求完整耗用和产出；零耗用、损耗、产出差异需说明。损耗是耗用中的子集，不重复计价。
- 一家公司、多个内部厨房、单实例低容量部署。全部写事务串行并校验版本，支持并发冲突拒绝，不承诺高吞吐多租户 SaaS。
- **未实现**收银同步、库存、采购、支付、营养计算、过敏原判断、食品安全认证、自动推荐或第三方集成。不依赖 AI、外部账号、密钥或付费服务；没有预留接口冒充集成。

## 架构与目录

浏览器 → Nginx 同源代理 → Spring Security 会话／CSRF → 事务服务／JPA → MySQL。Flyway 管理迁移，Hibernate 只验证结构。账号、角色和厨房范围每次请求重新核验；停用或重置密码撤销旧会话。

```text
zhuatech-recipeflow/
├── backend/              Java 21 / Spring Boot 4.0.7 / Security / JPA
│   └── src/main/resources/db/migration/  V1身份、V2配方与备料
├── frontend/             Vue 3.5.43 / Vite 8.1.5 / 中英文工作台
├── docs/                 操作、计算、接口、安全、部署、运行截图
├── scripts/              独立配置、业务验收、私有备份与恢复
├── compose.yaml          MySQL 8.4 + backend + frontend
├── README.en.md
└── LICENSE               自定义非商业源码许可证
```

环境：Docker Engine 及 Compose v2；本地开发另需 JDK21、Maven3.9+、Node.js24.19.0+、npm。镜像使用 Temurin21、Node24.19.0、Nginx1.29。前端没有脱离后端的模拟成功模式。

## 安装与启动

```bash
python3 scripts/init-env.py
docker compose config --quiet
docker compose up --build -d --wait
```

访问 **[http://127.0.0.1:8133/](http://127.0.0.1:8133/)**；健康检查 **[http://127.0.0.1:8133/actuator/health](http://127.0.0.1:8133/actuator/health)**。首次用户名由 `ADMIN_USERNAME` 指定，默认 `admin`；密码由配置脚本随机生成到权限为0600的 `.env`，请在本机查看 `ADMIN_PASSWORD`。**没有共享演示密码**，脚本拒绝覆盖已有 `.env`。

默认只绑定本机。端口冲突时：

```bash
WEB_PORT=18133 docker compose up --build -d --wait
```

首次 Flyway 在**空数据库卷**建立 V1、V2，随后初始化角色、分类、菜单、参数和一个管理员，业务数据为空。先增加独立审核账号和厨房执行账号，再维护食材与配方。重启不会重设管理员或生成业务事实。不要把生产数据放入测试脚本。

本地前后端开发：启动独立 MySQL8.4，按 `.env.example` 配置环境变量（Maven不会自动读 `.env`），设置 `DATABASE_URL`、`DATABASE_USER`、`DATABASE_PASSWORD`、`ADMIN_PASSWORD` 后执行：

```bash
cd backend
mvn spring-boot:run
# 另一个终端，在 frontend 目录
npm ci
npm run dev
```

开发页面按 Vite 终端地址访问，默认代理本机8080后端；多个本地项目并行时应为后端端口及开发代理使用独立配置。正式前端始终使用同源 `/api`。

## 配置、数据库与升级

| 变量 | 用途 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` | 内置 MySQL 初始化强密码，仅空卷使用 |
| `DATABASE_PASSWORD` | 应用数据库强密码，必填 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 空身份库的初始管理员，密码12–72 UTF-8字节，含大小写和数字 |
| `WEB_PORT` / `BIND_ADDRESS` | 页面端口与绑定地址，默认8133／127.0.0.1 |
| `COOKIE_SECURE` | HTTPS环境设为true，默认本机HTTP为false |
| `DATABASE_URL` / `DATABASE_USER` | 可选外部MySQL；外部连接需校验TLS和独立凭据 |

数据库 `zhuatech_recipeflow`，`mysql-data` 卷持久化。迁移位于 `backend/src/main/resources/db/migration/`；账号和业务表、索引、外键由迁移建立。升级先私有备份、在独立恢复实例验证，再更新代码并启动；不要改写已执行迁移或用删卷代替升级。参阅[部署与恢复](docs/deployment.md)。备份包含密码散列与业务数据，不得上传 Git。

## 测试与验收

图片与脚本检查另需独立Python环境：`python3 -m venv .venv`，然后`.venv/bin/python -m pip install -r scripts/requirements-quality.txt`；使用该环境执行发布检查。

```bash
mvn -B -f backend/pom.xml spotless:check clean verify
cd frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
# 回到项目根目录
docker compose config --quiet
.venv/bin/python scripts/release-check.py
git diff --check
```

后端单元测试覆盖计量、净料率、成本舍入、份数和加权价格；HTTP集成测试覆盖迁移、认证、状态、历史冻结、审核独立性、损耗约束与数据范围。集成测试使用H2兼容模式，**还须实际空 MySQL 卷验收**。Docker Maven构建执行完整测试，不跳过。真实部署的测试方法和结果见[验收说明](docs/verification.md)。

独立测试库可以运行 `python3 scripts/smoke.py --help` 中的业务验收；它会创建明确标注 TEST 的账号和记录，私有状态文件包含测试凭据，不得提交。用独立 Compose 项目隔离，不在生产库运行。

## 安全、部署与故障

正式部署应由获授权的部署人员配置 HTTPS反向代理、可信代理边界、`COOKIE_SECURE=true`、访问控制和私有备份。数据库及后端不公开端口；账号密码不打印、不放日志，上传大小受限，CSV做公式前缀防护。会话为内存会话，重启重新登录；请勿当作分布式身份服务。应用历史保留不能抵抗数据库管理员篡改。[安全说明](docs/security.md)。

- 启动失败：查看 `docker compose logs backend`，核对配置、数据库健康和迁移；不要跳过失败测试。
- 登录失败：使用本实例首次设置的密码；更改 `.env` 不会重设已有账号，密码由系统账号页重置。
- 送审/批准失败：核对指定独立审核人、历史编辑关系、启用食材和报价版本。
- 版本冲突：刷新确认记录后再操作；网络中断时提交结果未知，不自动重放。
- 无批准配方：先走完整配方审核；草稿不能编制备料。

[操作手册](docs/operations.md) · [接口](docs/api.md) · [架构与数值规则](docs/architecture.md)

## 授权、贡献与联系知华科技

自有源码采用 [LICENSE](LICENSE)：仅限个人学习交流，未经上海如静知华信息科技有限公司书面授权不得商用；不是 MIT、Apache 等允许免费商用的协议。第三方组件继续遵守原许可，见 [NOTICE](NOTICE)。源码按现状提供，不构成食品安全、营养、财务或生产适用性保证。

本项目由知华科技（上海如静知华信息科技有限公司）提供公开源码学习版本，主要用于个人学习、技术研究与非商业交流。未经书面授权不得商用。企业信息化建设、中小企业数字化转型、中小企业 AI 转型、私有化部署、软件外包、软件项目外包、软件实施、FDE 外包、OPC 技术支持及深度定制开发，请访问知华科技官网 <https://www.zhuatech.cn/>，或添加微信 zhuatech、zhuatech2 咨询。

商业授权或深度定制开发请联系知华科技。问题反馈请提交仓库 Issue；贡献请附业务说明、复现及测试，勿提交客户数据或凭据。安全漏洞请通过私密联系方式反馈，不在公开 Issue 披露凭据和可利用细节。

| 微信 zhuatech | 微信 zhuatech2 |
| :---: | :---: |
| <img src="docs/images/wechat-zhuatech.png" width="200" alt="知华科技微信 zhuatech"> | <img src="docs/images/wechat-zhuatech2.png" width="200" alt="知华科技微信 zhuatech2"> |
