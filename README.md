# 域名与 SSL 证书管理系统

面向团队使用的域名与 SSL/TLS 证书管理控制台。项目采用 Java、MySQL 和 Vue 3，提供域名公开信息查询、证书检查与管理、到期提醒、用户权限和操作审计。

## 主要能力

- **域名管理：** 登记域名并查询注册商、注册日期、到期日期、名称服务器和公开 DNS 记录。注册数据优先使用 RDAP；`.cn` 域名在适用时使用 CNNIC WHOIS 补充。公开数据源未提供的字段会显示为未知。支持手动刷新和后台查询。
- **SSL 证书：** 查询网站当前 HTTPS 证书信息；通过 ACME HTTP-01 创建证书；上传 PEM 证书链及匹配的未加密私钥；检查证书有效期、设置续期偏好并下载 PEM。上传或签发的私钥在保存前使用 AES-GCM 加密。下载私钥需要管理员或运维角色。HTTP-01 要求证书域名解析到本服务且公网 80 端口可达；当前签发流程不支持通配符证书。
- **提醒中心：** 每日检查临近到期资产，按 30、14、7、1 天档位发送邮件并记录投递状态。支持预览提醒邮件内容和发送指定收件人的测试邮件。
- **用户与系统设置：** 管理员可创建、编辑、停用和删除账户，分配管理员、运维或只读角色，重置密码并查看操作审计。管理员可维护 SMTP 设置和证书私钥加密密钥。

## 技术栈

| 部分 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 4.1.1、Spring Security、Spring Data JPA、Maven |
| 数据库 | MySQL；Flyway 负责结构迁移，Hibernate 使用 `validate` 校验结构 |
| 前端 | Vue 3、TypeScript、Vite、Lucide |
| 证书签发 | acme4j；HTTP-01 challenge |
| Web 服务 | Nginx；提供 SPA 回退和 API/challenge 反向代理配置 |

## 目录结构

```text
backend/
  src/main/java/       Spring Boot 控制器、服务、实体和数据访问
  src/main/resources/  application.yml 与 Flyway SQL 迁移
  Dockerfile
frontend/
  src/                 Vue 页面、API 客户端与样式
  vite.config.ts       本地开发服务器与 API 代理
  nginx.conf           静态页面、API 与 ACME challenge 代理
  Dockerfile
README.md
```

## 本地运行

### 环境准备

- JDK 17 和 Maven
- MySQL 服务及 pnpm 支持的 Node.js 运行时（前端容器构建基于 Node 22）
- pnpm 11.9.0（版本见 `frontend/package.json`）

创建本地数据库，并检查 `backend/src/main/resources/application.yml` 的数据源配置。Flyway 会在后端启动时自动应用迁移；当前迁移版本为 V4。

本项目按约定从 `application.yml` 读取启动配置，不使用 `.env` 配置读取流程。首次账户密码、数据库口令、ACME 联系信息及密钥等配置值**不要提交、复制到 README 或公开分享**。首次账户初始化只会在 `users` 表完全为空时执行；已有账户的密码不会因重启而被 YAML 覆盖。SMTP 设置和证书数据密钥可在系统设置页面动态维护；用于保护数据库中秘密值的根密钥仍由 YAML 提供，必须妥善备份。

### 启动后端

```sh
cd backend
mvn spring-boot:run
```

后端默认监听 `http://localhost:8080`。应用启动时会连接 MySQL、校验并运行 Flyway 迁移。

### 启动前端

在另一个终端运行：

```sh
cd frontend
pnpm install --frozen-lockfile
pnpm run dev
```

Vite 默认监听 5173 端口，并将 `/api` 和 `/actuator` 请求代理到 `localhost:8080`。端口调整时需同步修改 `frontend/vite.config.ts`。

### 构建

```sh
cd frontend
pnpm run build
```

```sh
cd backend
mvn -q -DskipTests package
```

前端构建包含 `vue-tsc --noEmit` 类型检查。当前仓库尚无后端自动化测试源码，因此后端打包成功不代表业务测试或端到端验收通过。

## 账户与角色

初始账户名为 `admin`、`operator` 和 `viewer`；首次密码从本机 `application.yml` 配置。只有在 `users` 表完全为空时，启动初始化才会创建这三个账户并以 BCrypt 哈希保存密码。数据库中已有任何账户时，初始化逻辑都会跳过；YAML 中的初始密码也不会重置数据库里的现存密码。

- **管理员：** 管理用户、角色和系统设置，并维护资产。
- **运维：** 管理域名和证书，可上传、签发及下载证书。
- **只读：** 查看资产和提醒，不执行写操作或下载私钥。

实际权限由后端 Spring Security 校验，不依赖前端隐藏按钮。

## 配置与安全

- 数据库结构使用 Flyway 迁移；不要用 Hibernate 自动创建或更新生产表。
- 用户密码只存 BCrypt 哈希。证书私钥和动态 SMTP 密码使用 AES-GCM 加密保存；API 不回显这些秘密的明文。
- 证书数据密钥变更会触发已保存证书私钥重新加密。操作前应备份数据库和 YAML 中的根保护密钥，并先在隔离环境验证。
- ACME 默认配置指向正式签发目录。首次联调请切换到 ACME staging，并确认域名解析、公网 80 端口和 challenge 代理配置后再签发。
- YAML 配置文件当前包含敏感值。对外分享前应使用不含秘密值的示例配置；如可用凭据曾被公开，应在相应服务中轮换。

## 部署状态与验证

项目包含前后端 Dockerfile 和 Nginx 配置，但当前没有 Compose 编排文件。Nginx 配置假定 API 服务可通过 `api:8080` 访问；容器网络、数据卷、完整部署和公网 challenge 转发需要部署配置补齐后再验证。后端 Dockerfile 使用 Temurin 25，而 Maven 源码目标为 Java 17；容器构建与运行仍需单独验收。

最近一次开发验证记录：

- 前端 `pnpm run build`：通过（包含 TypeScript 检查）。
- 后端 `mvn -q -DskipTests package`：通过；没有运行后端测试。
- MySQL 8.0.46 与 Flyway V4：启动验证通过，schema 已是最新版本。
- 只读用户 `/api/users/me`：曾返回 200。管理员设置接口当时因初始管理员凭据与数据库现有密码不匹配而返回 401，故设置读写和密钥轮换尚未完成运行验证。
- SMTP 实际投递、ACME 正式或 staging 签发、PEM 页面上传/下载、完整浏览器操作流程和容器部署：尚未完成端到端验证。

## 常见排查

### API 返回 404，但源码中已有对应接口

确认 8080 端口实际由哪个 Java 进程监听，并核对进程命令、工作目录和加载的构建产物。旧进程可能继续提供旧版 `target/classes`。

```sh
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

### 登录失败或设置 API 返回 401

检查数据库中的账户是否存在、是否启用、角色是否正确，以及该账户当前密码。首次初始化只在用户表为空时运行；修改 YAML 初始密码不会改写已有账户。

### 注册信息显示未知

RDAP/WHOIS 服务可能不公开某些字段，查询也可能暂时失败。查看域名查询状态和刷新时间；不要把未知日期当作零值或估算值。

### PEM 上传被拒绝

确认文件包含完整 X.509 证书链和对应私钥，私钥未加密，且格式为 PKCS#8 或受支持的 RSA PKCS#1。证书和私钥必须匹配。

### ACME 返回 502

检查后端日志中的 CA problem detail、当前 ACME directory、域名 DNS 解析、公网 80 端口及 `/.well-known/acme-challenge/` 代理。使用 staging 和专用测试域名复现；仅凭 502 状态不能判断根因。
