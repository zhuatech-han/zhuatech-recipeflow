# RecipeFlow 部署、升级与恢复 / Deployment

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

## 本机与生产边界

`python3 scripts/init-env.py` 为本实例生成0600独立配置；只绑定127.0.0.1的8133端口，可用WEB_PORT覆盖。MySQL和后端没有宿主机公开端口。`docker compose config --quiet`不会打印密码，普通config输出可能含密码，应避免公开。

Local configuration is private and unique. Only frontend port8133 binds tolocalhost. MySQL/backend have no host port mappings. Use `docker compose config --quiet`; ordinary configuration output may reveal passwords.

正式商业部署须先获书面授权，并由部署人员配置HTTPS、可信代理、安全网络访问和`COOKIE_SECURE=true`。内置MySQL连接使用容器隔离网络和TLS；外部数据库需`sslMode=VERIFY_IDENTITY`、可信CA及适配网络。不要把真实配置提交到Git，不要直接暴露测试实例。默认会话30分钟，内存会话重启后登录。

Commercial deployment requires written authorization. Configure HTTPS, trusted proxies, secure network access and secure cookies. External MySQL requires verified TLS and a trusted CA. Never commit live configuration. Sessions expire after30minutes and require sign-in after restart.

## 备份与全新恢复

```bash
python3 scripts/backup.py --project recipeflow-local --output /private/path/recipeflow-backup.zip
python3 scripts/restore.py /private/path/recipeflow-backup.zip --project recipeflow-restore --env-file /private/path/restore.env
```

`--project`须与原Compose启动项目一致（建议启动时显式`docker compose -p recipeflow-local up ...`）。恢复配置使用新的独立密码及页面端口。备份脚本暂停本项目后端写入，MySQL一致性导出后启动后端；恢复脚本验证归档成员、大小和SHA，仅恢复到不存在的独立项目/卷/网络，不覆盖现有库。脚本只支持内置MySQL，不可和外部DATABASE_URL混用。

Use the actual Compose project name. Backups pause this project's backend while obtaining a consistent dump, then restart it. Restore validates members, size and hash and refuses existing projects/volumes/networks. Use unique restore credentials/ports. Scripts support bundled MySQL only.

仅恢复可信备份；SQL不是沙箱。备份包含密码散列和业务数据，0600私有保存。恢复后核对所有账号与权限、配方快照、备料、耗用、产出、审核/执行记录和CSV，再开放访问。初始化ADMIN_PASSWORD不会覆盖恢复的原密码。升级先备份并在全新隔离库演练，然后部署新版本；不得改写Flyway已执行脚本。

Only restore trusted dumps. Verify identities, scopes, recipe snapshots, preparation, actuals, events and exports before allowing access. Bootstrap configuration does not reset restored passwords. Rehearse upgrades privately; never rewrite applied migrations.

测试完成仅清理本项目：`docker compose -p YOUR_TEST_PROJECT down -v`。生产停止使用`down`且不加`-v`，保留数据；没有备份不能随意删除卷。不得停止其他项目资源。
