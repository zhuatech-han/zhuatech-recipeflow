# RecipeFlow 验收 / Verification

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

## 检查步骤 / Gates

1. 后端格式与全部单元、HTTP集成测试；前端格式、ESLint、请求安全测试与生产构建。
2. Compose配置和完整镜像构建，构建执行后端及前端测试。
3. 独立空MySQL8.4卷首次启动，V1/V2迁移及结构验证、三个健康检查、管理员登录。
4. `scripts/smoke.py`在隔离实例核对配方审批、子配方、备料发布、耗用/产出、完工、冻结历史、当前报价、跨厨房/角色/指定人权限与并发版本冲突。
5. 浏览器实际登录、配方审批、厨房执行登记、导出与页面/手机核验；截图不得含密码、客户资料或系统权限弹窗。
6. 采集所有目录、配方与版本、备料、实际、业务事件、非登录审计和CSV；重启后精确比较，再私有备份恢复到另一个全新卷精确比较。
7. 发布静态检查：链接、真实截图、两个原始二维码、LOGO、许可、品牌及凭据扫描；Git差异检查。逐平台验证公开状态、文件树和最终SHA。

H2 tests cover transactional behavior, but real MySQL startup and workflow checks are required separately. Browser screenshots must represent the running code and contain fictitious test data only. Backup/restore comparison excludes new LOGIN audit entries, while retaining every business event and non-login audit entry.

## 结果 / Results

2026-10-11，本版本完成以下实际检查：

- 后端53项（21项数值/文本规则、32项HTTP/Security/JPA/Flyway集成），0失败、0错误、0跳过；前端5项请求安全测试、格式、ESLint和生产构建通过。Python脚本Black及编译检查通过。
- Docker镜像构建执行全部测试；最终源码在全新MySQL8.4卷启动，V1/V2、Hibernate结构验证、三个健康检查与初始化管理员登录通过。
- 空库真实HTTP业务121项检查通过：食材报价、配方独立审核、子配方、供餐备料、实际耗用/产出、完工冻结、改价对比、角色/厨房/指定人隔离、CSRF和并发200/409。
- 浏览器实际审批和完工通过；厨房实际估值37.08元、计划差异0.68元，与服务端结果一致。实际下载CSV与授权历史导出265字节完全一致。表单版本冲突有可见提示且保留输入；后台弹窗修正后复验通过。
- 子配方批次实测：7份成品需要8.75份子配方，成本15.93元，制作说明随计划冻结。手机390×844无横向页面溢出。
- 全部目录、3份配方及5个历史版本、3个备料批次、实际耗用/产出、业务事件、非登录审计与CSV共31组数据，重启及独立全新恢复各75项检查通过，与私有基线精确一致；LOGIN审计因重新登录而单独排除。
- 11张当前运行截图已实拍检查；未见Apple密码弹窗或真实客户资料、密码与密钥。两张原始微信二维码及正式LOGO按原字节保留，README互链、相对图片、联系方式和非商业LICENSE检查通过。
- npm官方审计在检查时返回0条已知漏洞；不构成零安全风险保证。

The checks above cover isolated local deployments and fictitious TEST data. They do not certify production suitability, food safety, nutrition, inventory accuracy or financial profit. H2 tests were supplemented by actual MySQL startup, business, restart and restore checks. Third-party/POS integrations remain unimplemented.
