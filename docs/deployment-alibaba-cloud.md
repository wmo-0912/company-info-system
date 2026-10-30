# 阿里云轻量应用服务器（Ubuntu）上线完整流程

> 适用：阿里云轻量应用服务器，2 vCPU / 1 GiB 内存，Ubuntu 24.04 LTS（22.04 亦可）。
> 架构：Nginx（静态页 + `/api/` 反代）→ Spring Boot（systemd 托管）→ PostgreSQL 17（仅监听本机）。
> 关键约束：**内存只有 1 GiB，禁止在服务器上打包前端/后端**，一律本地构建后上传产物。

---

## 0. 需要提前确认的信息

| 变量 | 说明 | 示例 |
|------|------|------|
| `<公网IP>` | 轻量服务器公网 IP | 47.x.x.x |
| `<SSH_USER>` | SSH 用户名，轻量默认 root | root |
| `<DB_PASSWORD>` | 生产库 `company_app` 的强密码 | 自己生成，勿用演示口令 |
| `<域名>` | 可选；没有就先用 IP 访问 | `demo.example.com` |

以下命令均在服务器上以 root（或 sudo）执行，本地命令会单独标注。

---

## 1. 选择镜像并创建实例

- 控制台创建实例 → 镜像类型选 **系统镜像** → **Ubuntu 24.04 LTS 64 位**（没有则选 22.04 LTS）。
- **不要选应用镜像**（宝塔/LAMP/Java 环境镜像）：预装服务会占用 200–300 MB 内存，且版本不可控。
- 创建后：设置实例密码（或绑定 SSH 密钥）。

选择 Ubuntu 的原因：apt 源可直接安装 `openjdk-17`、`nginx`、`certbot`，PG17 可用官方 PGDG 源安装；系统稳定、文档丰富。

## 2. 放行端口

轻量应用服务器控制台 → 实例 → **防火墙**（安全规则）→ 放行：

- `22/tcp`（SSH）
- `80/tcp`（HTTP）
- `443/tcp`（HTTPS，有域名时）

> 轻量的防火墙在控制台配置，不在系统内 `ufw`；两边都配置也无妨，但以控制台为准。

## 3. 连接服务器

本地 PowerShell：

```powershell
ssh <SSH_USER>@<公网IP>
```

## 4. 系统初始化

```bash
# 4.1 加 2 GiB swap：1 GiB 内存不加交换分区极易 OOM
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h        # 确认 Swap 已生效

# 4.2 安装运行依赖
sudo apt update
sudo apt install -y openjdk-17-jre-headless nginx postgresql-common
java -version
nginx -v
```

## 5. 安装 PostgreSQL 17

Ubuntu 24.04 的默认源是 PG16，项目按 PG17 交付，因此使用官方 PGDG 源：

```bash
# 5.1 添加官方源（国内网络慢时看下方镜像方案）
sudo /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh
sudo apt update && sudo apt install -y postgresql-17
psql --version
pg_lsclusters            # 应显示 17 main online 5432

# 5.2 若之前误装过 16，停用并禁止随系统启动（避免两个版本抢内存）
sudo pg_ctlcluster 16 main stop 2>/dev/null || true
sudo sed -i 's/^start/disabled/' /etc/postgresql/16/main/start.conf 2>/dev/null || true
```

国内网络慢时改用清华镜像（与上面的源脚本二选一）：

```bash
sudo tee /etc/apt/sources.list.d/pgdg.list >/dev/null <<'EOF'
deb https://mirrors.tuna.tsinghua.edu.cn/postgresql/repos/apt/ noble-pgdg main
EOF
sudo wget -qO /etc/apt/trusted.gpg.d/pgdg.asc https://mirrors.tuna.tsinghua.edu.cn/postgresql/repos/apt/ACCC4CF8.asc
sudo apt update && sudo apt install -y postgresql-17
```

### 5.3 针对 1 GiB 内存调参

```bash
sudo sed -i "s/^#\?listen_addresses.*/listen_addresses = 'localhost'/" /etc/postgresql/17/main/postgresql.conf
sudo sed -i "s/^#\?shared_buffers.*/shared_buffers = 128MB/" /etc/postgresql/17/main/postgresql.conf
sudo sed -i "s/^#\?max_connections.*/max_connections = 30/" /etc/postgresql/17/main/postgresql.conf
sudo sed -i "s/^#\?effective_cache_size.*/effective_cache_size = 256MB/" /etc/postgresql/17/main/postgresql.conf
sudo sed -i "s/^#\?work_mem.*/work_mem = 4MB/" /etc/postgresql/17/main/postgresql.conf
sudo systemctl restart postgresql
sudo -u postgres psql -c "SHOW shared_buffers;"
```

## 6. 建库并迁移数据

### 6.1 服务器上创建用户与空库

```bash
sudo -u postgres psql -c "CREATE USER company_app WITH PASSWORD '<DB_PASSWORD>';"
sudo -u postgres createdb -O company_app company_info
```

### 6.2 本地导出（PowerShell，本机已装 PG17）

```powershell
cd d:\code\project\company-info-system
pg_dump -U company_app -h 127.0.0.1 -Fc company_info -f company_info.dump
# 若 pg_dump 不在 PATH，用完整路径，例如：
# & "C:\Program Files\PostgreSQL\17\bin\pg_dump.exe" -U company_app -h 127.0.0.1 -Fc company_info -f company_info.dump
```

### 6.3 上传并导入

```powershell
scp company_info.dump <SSH_USER>@<公网IP>:/tmp/
```

```bash
sudo -u postgres pg_restore -d company_info --no-owner --no-privileges /tmp/company_info.dump
sudo -u postgres psql -d company_info -c "\dt"     # 应看到 16 张表
sudo -u postgres psql -d company_info -c "select count(*) from sys_user;"
```

> dump 已包含表结构与数据，**不要再执行 `database/schema.sql`**。
> 若从零建库（不迁移旧数据），则执行 `psql -d company_info -f database/schema.sql`，并按 docs/demo-accounts.md 或手工插入管理员账号。

## 7. 本地构建产物

```powershell
cd d:\code\project\company-info-system\backend
.\mvnw.cmd clean package -DskipTests
# 产物：target\company-info-system-0.0.1-SNAPSHOT.jar

cd ..\frontend
npm.cmd ci
npm.cmd run build
# 产物：frontend\dist
```

## 8. 上传产物并落地目录

```powershell
cd d:\code\project\company-info-system
scp backend\target\company-info-system-0.0.1-SNAPSHOT.jar <SSH_USER>@<公网IP>:/tmp/app.jar
scp -r frontend\dist <SSH_USER>@<公网IP>:/tmp/dist
```

```bash
sudo mkdir -p /opt/company-info-system/frontend /etc/company-info-system /var/log/company-info-system
sudo mv /tmp/app.jar /opt/company-info-system/app.jar
sudo cp -r /tmp/dist/* /opt/company-info-system/frontend/
sudo chmod -R a+r /opt/company-info-system/frontend
```

## 9. 配置环境变量

```bash
sudo tee /etc/company-info-system/env >/dev/null <<EOF
DB_URL=jdbc:postgresql://127.0.0.1:5432/company_info
DB_USERNAME=company_app
DB_PASSWORD=<DB_PASSWORD>
JWT_SECRET=$(openssl rand -base64 48)
SEED_DEMO_DATA=false
EOF
sudo chmod 600 /etc/company-info-system/env
sudo cat /etc/company-info-system/env     # 核对内容
```

要点：

- `JWT_SECRET` 少于 32 字节应用会启动失败（有意设计），务必用上面的命令生成。
- 生产 `SEED_DEMO_DATA=false`：不会自动插入演示账号。
- 文件权限 600，禁止提交进 Git。

## 10. systemd 托管后端（限制堆内存）

```bash
sudo tee /etc/systemd/system/company-info-system.service >/dev/null <<'EOF'
[Unit]
Description=Company Info System
After=network.target postgresql.service

[Service]
EnvironmentFile=/etc/company-info-system/env
ExecStart=/usr/bin/java -Xms128m -Xmx384m -XX:+UseSerialGC -jar /opt/company-info-system/app.jar
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now company-info-system
sleep 20
sudo systemctl status company-info-system --no-pager
curl -s http://127.0.0.1:8080/api/v1/health
```

预期返回：`{"code":"OK","message":"success","data":{"status":"UP"},...}`

## 11. 配置 Nginx

```bash
sudo tee /etc/nginx/sites-available/company-info-system >/dev/null <<'EOF'
server {
    listen 80 default_server;
    server_name _;                       # 有域名后改成你的域名
    root /opt/company-info-system/frontend;
    index index.html;
    client_max_body_size 10m;

    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;
    gzip_min_length 1k;

    # 后端 API（路径保持不变，后端前缀就是 /api/v1）
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 生产不对外暴露 Swagger
    location ~ ^/(swagger-ui|v3/api-docs) {
        return 404;
    }

    # SPA 路由回退
    location / {
        try_files $uri $uri/ /index.html;
    }
}
EOF

sudo rm -f /etc/nginx/sites-enabled/default
sudo ln -sf /etc/nginx/sites-available/company-info-system /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

Nginx 版本无特殊要求，Ubuntu 自带 1.24 足够（`proxy_pass`、`try_files`、`gzip` 都是长期稳定能力）。

## 12. 验证上线

```bash
curl -s http://127.0.0.1:8080/api/v1/health      # 本机直连后端
curl -s http://<公网IP>/api/v1/health            # 经 Nginx
```

浏览器访问 `http://<公网IP>/`：

1. 用管理员账号登录（见 docs/demo-accounts.md）。
2. **立即在「系统管理 → 用户管理」重置管理员密码**。
3. 逐项走查：部门树、员工列表、入转调离、项目组、绩效、系统管理、右键头像 → 个人中心。
4. 用普通员工登录，确认菜单与数据范围受限。

## 13. 绑定域名与 HTTPS（可选）

1. 域名 DNS 添加 A 记录指向 `<公网IP>`。
2. Nginx 里把 `server_name _` 改为你的域名。
3. 签发证书：

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d <域名>
sudo systemctl list-timers | grep certbot      # 自动续期定时器
```

## 14. 后续更新发布流程

```powershell
# 本地
cd d:\code\project\company-info-system
git fetch origin && git checkout online && git merge master && git push origin online
cd backend; .\mvnw.cmd clean package -DskipTests
cd ..\frontend; npm.cmd run build
scp backend\target\company-info-system-0.0.1-SNAPSHOT.jar <SSH_USER>@<公网IP>:/tmp/app.jar
scp -r frontend\dist <SSH_USER>@<公网IP>:/tmp/dist
```

```bash
# 服务器
sudo systemctl stop company-info-system
sudo mv /tmp/app.jar /opt/company-info-system/app.jar
sudo rm -rf /opt/company-info-system/frontend/*
sudo cp -r /tmp/dist/* /opt/company-info-system/frontend/
sudo systemctl start company-info-system
sleep 15 && curl -s http://127.0.0.1:8080/api/v1/health
```

## 15. 备份

```bash
# 手工备份
sudo -u postgres pg_dump -Fc company_info > /root/backup/company_info_$(date +%F).dump

# 每日 2 点自动备份（crontab -e）
0 2 * * * mkdir -p /root/backup && sudo -u postgres pg_dump -Fc company_info > /root/backup/company_info_$(date +\%F).dump
```

## 16. 常见问题排查

| 现象 | 排查 |
|------|------|
| 浏览器访问超时 | 控制台防火墙是否放行 80；`sudo systemctl status nginx` |
| 页面能开、接口 500 | `sudo journalctl -u company-info-system -n 100 --no-pager`；多为数据库连不上或 `JWT_SECRET` 为空 |
| 应用启动即退出 | `sudo journalctl -u company-info-system -n 200 --no-pager`，重点看 `jwtTokenProvider` 与 JDBC 报错 |
| 登录报 401 | 确认库里 `sys_user` 有账号且 `status='ACTIVE'`；密码是否已被重置 |
| 服务偶发被杀 | `dmesg \| grep -i oom`，检查 swap 与 `-Xmx`；必要时把库迁到阿里云 RDS |
| Nginx 502 | 后端未启动或端口占用：`ss -lntp \| grep 8080` |
| 中文/时间不对 | 应用已固定 `Asia/Shanghai`，检查系统时区 `timedatectl` |

## 17. 上线前安全检查清单

- [ ] 管理员默认密码已修改，演示账号已删除或禁用
- [ ] `/etc/company-info-system/env` 权限 600，`JWT_SECRET` 为随机生成
- [ ] `SEED_DEMO_DATA=false`
- [ ] Swagger 已对外返回 404
- [ ] 数据库仅监听 `127.0.0.1`，未暴露 5432
- [ ] 已配置 HTTPS（有域名时）
- [ ] 已配置每日数据库备份并验证可恢复
- [ ] 控制台防火墙只放行 22/80/443
