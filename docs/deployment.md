# 部署文档

生产环境建议同源部署：Nginx 统一入口，静态托管前端 `dist`，`/api/` 反向代理到后端。

## 1. 构建前端

```bash
cd frontend
npm ci
npm run build
# 产物在 frontend/dist/
```

## 2. 后端打包

```bash
cd backend
./mvnw clean package -DskipTests
# 产物 backend/target/company-info-system-0.0.1-SNAPSHOT.jar
```

## 3. 配置环境变量

```bash
export DB_URL="jdbc:postgresql://127.0.0.1:5432/company_info"
export DB_USERNAME="company_app"
export DB_PASSWORD="<强密码>"
export JWT_SECRET="<至少32字节的随机字符串>"
export SEED_DEMO_DATA=false
```

注意：

- `JWT_SECRET` 不足 32 字节时应用启动直接失败，这是有意设计。
- 生产环境 `SEED_DEMO_DATA` 必须为 `false`，不会自动创建任何账号。
- 不要把上述值提交到 Git；可使用 systemd `EnvironmentFile`、K8s Secret 等注入。

## 4. 初始化数据库

```bash
# 一次性执行， PostgreSQL 不暴露公网
psql -U postgres -c "CREATE USER company_app WITH PASSWORD '<强密码>';"
psql -U postgres -c "CREATE DATABASE company_info OWNER company_app;"
psql -U postgres -d company_info -f database/schema.sql
```

首个管理员账号的创建方式：在数据库中手动插入一条 `sys_user`（BCrypt 哈希）与 `sys_user_role` 绑定，或用一次性脚本生成。哈希可用以下方式生成：

```bash
# 任选其一
htpasswd -bnBC 10 "" '<密码>' | tr -d ':\n'   # 输出 BCrypt 哈希
```

插入时请显式设置 `must_change_password = TRUE`（列默认值为 `FALSE`）：这样首个管理员登录后会被强制修改初始化密码，避免部署文档里的示例口令长期留在生产库中。

## 5. 启动后端

```bash
nohup java -jar backend/target/company-info-system-0.0.1-SNAPSHOT.jar \
  --spring.config.location=classpath:/application.yml \
  > /var/log/company-info-system.log 2>&1 &
```

默认监听 `8080`。建议以 systemd 托管：

```ini
[Unit]
Description=Company Info System
After=network.target postgresql.service

[Service]
EnvironmentFile=/etc/company-info-system/env
ExecStart=/usr/bin/java -jar /opt/company-info-system/app.jar
SuccessExitStatus=143
Restart=always

[Install]
WantedBy=multi-user.target
```

## 6. 配置 Nginx

完整示例见 `docs/nginx.conf.example`，要点：

- `/` → 前端 `dist` 静态文件，SPA fallback 到 `index.html`
- `/api/` → `http://127.0.0.1:8080/api/`（保持路径不变）
- `/swagger-ui/`、`/v3/api-docs/` 生产建议不对外暴露或加访问控制
- 生产必须启用 HTTPS（证书 + `ssl` 配置）
- 合理的 `client_max_body_size` 与超时

## 7. 健康检查与日志排查

```bash
# 健康检查（无需鉴权）
curl http://127.0.0.1:8080/api/v1/health
# {"code":"OK","message":"success","data":{"status":"UP"}, ...}

# 经 Nginx 验证
curl https://your-domain/api/v1/health
```

排查顺序：

1. `journalctl -u company-info-system -f` 或应用日志文件
2. 数据库连通性：`psql "$DB_URL"` 或 `pg_isready`
3. Nginx 错误日志 `/var/log/nginx/error.log`
4. 常见 401/403 为鉴权问题，先看前端请求头是否带 `Authorization: Bearer <token>`

日志中不会打印密码、JWT、完整紧急联系人信息；如发现泄露请立即视为事故处理。

## 8. 数据库备份与恢复

```bash
# 备份（每日 cron 示例）
pg_dump -U company_app -Fc company_info > /backup/company_info_$(date +%F).dump

# 恢复
pg_restore -U company_app -d company_info --clean /backup/company_info_<日期>.dump
```

- 备份文件加密存储，访问受控
- 恢复演练至少每季度一次
- 业务表采用逻辑删除，误删数据可在保留期内直接恢复 `deleted` 标记
