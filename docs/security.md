# RecipeFlow 安全说明 / Security

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

- BCrypt12密码；12–72UTF-8字节、大小写和数字；禁止公开真实配置。登录失败8次进入5分钟窗口；此单实例限制不替代网关防护。
- 服务端会话HttpOnly、SameSite Strict；写接口CSRF。每次重新读账号、密码指纹、角色和厨房范围。停用、密码重置撤销旧身份。
- 身份目录需具体权限及ALL范围；最后完整管理员不可移除。配方编辑与审核独立，执行仅指定账号。菜单隐藏不能替代服务端权限。
- 数值、字段、长度、状态、版本、量纲、依赖、归属、CSV与实际行校验由服务端执行。固定实体查询和绑定参数，无客户端SQL、路径下载或任意文件读取入口。
- Nginx限制128KB请求正文、CSP、禁止框架嵌入、nosniff；数据库和后端仅容器内访问。没有文件上传接口。
- 审计只保存身份、动作和对象，业务说明另存业务事件。无密码或完整请求日志。备份、测试状态、客户数据不得发布。
- 历史JSON摘要检查只发现不一致，数据库管理员可同时改数据与摘要，不宣称不可篡改。结果估值不构成食品、过敏原、营养或财务认证。
- 商业部署应补充组织网络、TLS、访问审计、备份与事故流程。公开页面只使用虚构业务数据。

The controls above apply to a single instance. Secure network boundaries, verified TLS, private backups and incident procedures remain the deployer's responsibility. Report vulnerabilities privately via the official contact channels; do not publish exploitable details or credentials in public issues.
