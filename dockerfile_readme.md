# PlayEdu Dockerfile 说明

本文档基于当前工作区中的 Dockerfile，说明每条指令的作用、镜像构建流程及使用时的注意事项。

项目中共有 4 个 Dockerfile：

| 文件 | 用途 | 建议的构建上下文 |
| --- | --- | --- |
| `Dockerfile` | 同时构建管理端、PC 端、H5 端和 Java API，并打包为一个一体化镜像 | 项目根目录 |
| `playedu-api/Dockerfile` | 从源码构建并运行 Java API | `playedu-api` 目录 |
| `playedu-api/Dockerfile.local` | 使用本地已经编译好的 JAR 快速制作 API 镜像 | `playedu-api` 目录 |
| `docker/mysql/Dockerfile` | 在 MySQL 8.1 基础镜像中加入项目的 MySQL 配置 | `docker/mysql` 目录 |

## 一、根目录 `Dockerfile`

这是一个多阶段构建文件，依次完成前端构建、后端构建和最终运行镜像组装。多阶段构建可以避免把 Node.js、Maven、源码和构建缓存全部带入最终镜像。

### 1. 前端构建阶段

```dockerfile
FROM registry.cn-hangzhou.aliyuncs.com/hzbs/node:20-alpine AS node-builder
```

- 使用基于 Alpine Linux 的 Node.js 20 镜像。
- `AS node-builder` 把该阶段命名为 `node-builder`，后续阶段可通过 `COPY --from=node-builder` 取出构建结果。
- 此阶段只负责生成三个前端项目的 `dist` 目录，不会直接成为最终镜像。

```dockerfile
WORKDIR /app
```

- 将容器内当前工作目录设置为 `/app`。
- 后续相对路径指令默认以该目录为基础；如果目录不存在，Docker 会自动创建。

```dockerfile
COPY playedu-admin/package.json playedu-admin/pnpm-lock.yaml /app/admin/
COPY playedu-pc/package.json    playedu-pc/pnpm-lock.yaml    /app/pc/
COPY playedu-h5/package.json    playedu-h5/pnpm-lock.yaml    /app/h5/
```

- 先只复制三个前端项目的依赖清单和 pnpm 锁文件。
- 这样可以利用 Docker 的分层缓存：只要依赖清单未变化，即使业务源码改变，下面的依赖安装层仍可复用。
- 构建命令必须在项目根目录执行，因为这些源路径都是相对于根目录构建上下文的。

```dockerfile
RUN cd /app/admin && pnpm i
RUN cd /app/pc    && pnpm i
RUN cd /app/h5    && pnpm i
```

- 分别安装管理端、PC 端和 H5 端依赖。
- 三条命令会形成三个独立镜像层，某个项目依赖发生变化时，不必使另外两个项目的依赖层全部失效。
- `pnpm i` 会使用各自的 `pnpm-lock.yaml`；若希望 CI 构建严格禁止锁文件被更新，可考虑使用 `pnpm install --frozen-lockfile`。

```dockerfile
COPY playedu-admin /app/admin
RUN cd /app/admin && VITE_APP_URL=/api/ pnpm build
```

- 将管理端完整源码复制到 `/app/admin`。
- 执行 `package.json` 中的 `build` 脚本，即 `tsc && vite build`，构建结果默认位于 `/app/admin/dist`。
- `VITE_APP_URL=/api/` 是仅对本次构建命令生效的环境变量。Vite 会在打包时把 API 基础路径写入静态资源；它不是容器运行时动态配置。

```dockerfile
COPY playedu-pc /app/pc
RUN cd /app/pc && VITE_APP_URL=/api/ pnpm build
```

- 复制并构建 PC 学员端源码。
- 构建结果位于 `/app/pc/dist`，API 请求使用 `/api/` 前缀。

```dockerfile
COPY playedu-h5 /app/h5
RUN cd /app/h5 && VITE_APP_URL=/api/ pnpm build
```

- 复制并构建 H5 移动端源码。
- 构建结果位于 `/app/h5/dist`，API 请求同样使用 `/api/` 前缀。

### 2. Java 后端构建阶段

```dockerfile
FROM registry.cn-hangzhou.aliyuncs.com/hzbs/eclipse-temurin:17 AS java-builder
```

- 开启新的构建阶段，使用 Java 17 的 Eclipse Temurin 镜像。
- 该阶段命名为 `java-builder`，负责通过 Maven Wrapper 编译多模块 Spring Boot 项目。
- 开启新阶段后，前一个阶段的文件不会自动保留，只能通过 `COPY --from=...` 显式获取。

```dockerfile
WORKDIR /app
```

- 将 Java 构建阶段的工作目录设为 `/app`。

```dockerfile
COPY playedu-api/mvnw          /app/mvnw
COPY playedu-api/.mvn          /app/.mvn
RUN sed -i 's/\r$//' /app/mvnw && chmod +x /app/mvnw
```

- 复制 Maven Wrapper 启动脚本和 Wrapper 配置，使镜像无需预装系统 Maven。
- `sed -i 's/\r$//'` 去掉 Windows CRLF 行尾中的 `\r`，避免 Linux 执行脚本时出现解释器路径错误。
- `chmod +x` 为 `mvnw` 增加可执行权限。

```dockerfile
COPY playedu-api/pom.xml                     /app/pom.xml
COPY playedu-api/playedu-api/pom.xml         /app/playedu-api/pom.xml
COPY playedu-api/playedu-common/pom.xml      /app/playedu-common/pom.xml
COPY playedu-api/playedu-course/pom.xml      /app/playedu-course/pom.xml
COPY playedu-api/playedu-resource/pom.xml    /app/playedu-resource/pom.xml
COPY playedu-api/playedu-system/pom.xml      /app/playedu-system/pom.xml
```

- 先复制父 POM 以及 5 个 Maven 子模块的 POM。
- 父 POM 声明模块结构和公共依赖，各子模块 POM 声明模块自己的依赖与构建配置。
- 此时不复制 Java 源码，是为了把依赖下载做成可缓存的独立层。

```dockerfile
RUN /app/mvnw -B -DskipTests dependency:go-offline
```

- `-B` 使用 Maven 批处理模式，适合无交互的 Docker/CI 环境。
- `-DskipTests` 在相关生命周期中跳过测试运行。
- `dependency:go-offline` 尽量提前下载构建所需的依赖和插件。POM 未变化时，源码修改不会触发该层重新下载依赖。
- “离线准备”不一定覆盖所有插件在后续生命周期中动态解析的内容，因此后续 `package` 在少数情况下仍可能访问 Maven 仓库。

```dockerfile
COPY playedu-api /app
```

- 将整个后端工程源码复制到 `/app`。
- 项目根目录的 `.dockerignore` 排除了 `target`、日志、IDE 配置等内容，减少发送到 Docker 守护进程的数据量，也避免本地产物污染镜像构建。

```dockerfile
RUN /app/mvnw -B -Dmaven.test.skip=true package
```

- 编译所有 Maven 模块并执行 `package`，生成可运行的 Spring Boot JAR。
- `-Dmaven.test.skip=true` 同时跳过测试源码编译和测试执行，比 `-DskipTests` 跳过得更彻底。
- 最终需要的文件是 `/app/playedu-api/target/playedu-api.jar`。

### 3. 最终运行阶段

```dockerfile
FROM registry.cn-hangzhou.aliyuncs.com/hzbs/eclipse-temurin:17 AS base
```

- 开启最终镜像阶段，继续使用 Java 17 基础镜像。
- 前两个构建阶段中的源码、`node_modules`、Maven 缓存等不会进入最终镜像。
- 该 Dockerfile 后续会启动 `nginx`，因此这里使用的定制镜像必须已经包含 Nginx；如果换成官方纯 Temurin 镜像，需要自行安装 Nginx 并确保配置目录存在。

```dockerfile
COPY --from=java-builder /app/playedu-api/target/playedu-api.jar /app/api/app.jar
```

- 只从 Java 构建阶段复制最终 JAR，并在运行镜像中命名为 `/app/api/app.jar`。

```dockerfile
COPY --from=node-builder /app/admin/dist /app/admin
COPY --from=node-builder /app/pc/dist /app/pc
COPY --from=node-builder /app/h5/dist /app/h5
```

- 从前端构建阶段复制三个项目的静态产物。
- 最终镜像中的 `/app/admin`、`/app/pc`、`/app/h5` 分别作为管理端、PC 端、H5 端的 Nginx 站点根目录。

```dockerfile
COPY docker/nginx/conf/nginx.conf /etc/nginx/sites-enabled/default
```

- 将项目的 Nginx 配置复制为默认站点配置。
- 该配置创建三个站点：PC 端监听 `9800`、H5 端监听 `9801`、管理端监听 `9900`。
- 三个站点都把 `/api/` 请求反向代理到同一容器内的 Java 服务 `127.0.0.1:9898`。
- `try_files $uri /index.html` 用于支持 React 单页应用的前端路由回退。

```dockerfile
EXPOSE 9898
EXPOSE 9800
EXPOSE 9801
EXPOSE 9900
```

- 声明镜像预期使用的端口：API `9898`、PC `9800`、H5 `9801`、管理端 `9900`。
- `EXPOSE` 只是镜像元数据，不会自动把端口发布到宿主机；运行时仍需使用 `docker run -p` 或 Compose 的 `ports` 配置。

```dockerfile
CMD nginx; echo "Waiting for MySQL to start..."; sleep 15; java -jar /app/api/app.jar
```

- 容器启动时先启动 Nginx，然后输出等待提示，固定等待 15 秒，最后以前台方式启动 Java API。
- 该命令使用 shell 形式，Java 进程不是直接的 PID 1；容器停止信号的传递和优雅退出可能不如 exec 形式可靠。
- 固定休眠只能延迟启动，不能确认 MySQL 已经可用。更稳妥的方案是使用健康检查、重试机制或专用入口脚本检测数据库连接。
- 一个容器同时运行 Nginx 和 Java 两个服务；如果 Nginx 异常退出而 Java 仍在运行，容器可能不会随之退出。

### 4. 一体化镜像的数据流

```text
三个前端源码 -> Node.js/pnpm/Vite -> admin、pc、h5 静态文件 --+
                                                           |
Java 多模块源码 -> Maven/Java 17 -> playedu-api.jar --------+-> 最终镜像
                                                           |
Nginx 配置 -------------------------------------------------+
```

可从项目根目录构建：

```bash
docker build -t playedu:local -f Dockerfile .
```

示例运行命令（数据库等环境变量需要按实际部署配置补充）：

```bash
docker run --rm \
  -p 9898:9898 \
  -p 9800:9800 \
  -p 9801:9801 \
  -p 9900:9900 \
  playedu:local
```

## 二、`playedu-api/Dockerfile`

该文件只构建和运行 Java API，也采用“构建阶段 + 运行阶段”的多阶段模式。构建上下文应为 `playedu-api` 目录。

```dockerfile
FROM registry.cn-hangzhou.aliyuncs.com/hzbs/eclipse-temurin:17 AS builder
```

- 使用 Java 17 镜像并将后端构建阶段命名为 `builder`。

```dockerfile
WORKDIR /app
```

- 设置后续命令的工作目录为 `/app`。

```dockerfile
COPY mvnw          /app/mvnw
COPY .mvn          /app/.mvn
COPY pom.xml                    /app/pom.xml
COPY playedu-api/pom.xml        /app/playedu-api/pom.xml
COPY playedu-common/pom.xml     /app/playedu-common/pom.xml
COPY playedu-course/pom.xml     /app/playedu-course/pom.xml
COPY playedu-resource/pom.xml   /app/playedu-resource/pom.xml
COPY playedu-system/pom.xml     /app/playedu-system/pom.xml
```

- 复制 Maven Wrapper、父 POM 和全部模块 POM。
- 与根 Dockerfile 相同，先复制依赖描述文件是为了提高 Docker 构建缓存命中率。
- 这里没有显式执行换行符转换或 `chmod +x /app/mvnw`，因此要求仓库中的 `mvnw` 在构建上下文里具有 Linux 可执行属性且使用兼容行尾；否则下一步可能失败。

```dockerfile
RUN /app/mvnw -B -DskipTests dependency:go-offline
```

- 以批处理模式预下载 Maven 依赖和插件，并跳过测试运行。

```dockerfile
COPY . /app
```

- 将 `playedu-api` 构建上下文中的全部后端源码复制到 `/app`。
- 如果只使用项目根目录的 `.dockerignore`，应注意 Docker 只读取构建上下文根部的 ignore 文件；以 `playedu-api` 为上下文时，项目根目录的 `.dockerignore` 不在上下文根部，通常不会生效。

```dockerfile
RUN /app/mvnw -B -Dmaven.test.skip=true package
```

- 编译并打包多模块项目，同时跳过测试编译与运行。
- 生成 `/app/playedu-api/target/playedu-api.jar`。

```dockerfile
FROM registry.cn-hangzhou.aliyuncs.com/hzbs/eclipse-temurin:17 AS base
```

- 开启干净的 Java 17 运行阶段，构建工具、源码和 Maven 缓存不会进入最终镜像。

```dockerfile
WORKDIR /app
```

- 将最终容器的工作目录设为 `/app`。

```dockerfile
COPY --from=builder /app/playedu-api/target/playedu-api.jar /app/app.jar
```

- 从 `builder` 阶段只复制构建好的 API JAR。

```dockerfile
RUN chmod +x /app/app.jar
```

- 给 JAR 增加可执行权限。
- 当前容器通过 `java -jar` 读取 JAR，并不依赖 JAR 自身的可执行位，因此这一步通常不是必需的。

```dockerfile
EXPOSE 9898/tcp
```

- 声明 API 使用 TCP 9898 端口。应用配置中的默认服务端口也是 9898。
- 此声明不会自动发布端口。

```dockerfile
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- 使用 exec 形式将 Java 作为容器主进程启动。
- 相比 shell 形式，停止信号可以直接传给 JVM，更利于 Spring Boot 优雅关闭。
- `docker run` 末尾附加的参数会追加到该命令后，可用于传入 Spring Boot 参数。

可在项目根目录执行：

```bash
docker build -t playedu-api:local -f playedu-api/Dockerfile playedu-api
```

## 三、`playedu-api/Dockerfile.local`

这个文件不在容器内编译源码，而是直接复制本地 Maven 已经生成的 JAR，适合本地快速验证。

```dockerfile
FROM registry.cn-hangzhou.aliyuncs.com/hzbs/eclipse-temurin:17
```

- 使用 Java 17 运行镜像。
- 没有单独的构建阶段，因为该文件假设 JAR 已在宿主机上构建完成。

```dockerfile
WORKDIR /app
```

- 将容器工作目录设置为 `/app`。

```dockerfile
COPY ./playedu-api/target/playedu-api.jar /app/app.jar
```

- 从构建上下文的 `playedu-api/target` 目录复制现成 JAR。
- 因此构建镜像前必须先在后端项目中执行 Maven 打包。
- 构建上下文应为外层 `playedu-api` 目录，而不是仓库根目录。

```dockerfile
RUN chmod +x /app/app.jar
```

- 给 JAR 增加可执行权限；由于后面使用 `java -jar`，该权限通常不是必需的。

```dockerfile
EXPOSE 9898/tcp
```

- 声明 API 的 TCP 9898 端口，不会自动发布到宿主机。

```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- 以 exec 形式启动当前工作目录中的 `app.jar`。
- 因为 `WORKDIR` 是 `/app`，这里的相对路径最终解析为 `/app/app.jar`。

示例流程：

```bash
cd playedu-api
./mvnw -Dmaven.test.skip=true package
docker build -t playedu-api:local-jar -f Dockerfile.local .
```

需要注意：项目根目录 `.dockerignore` 包含 `**/target/`。当构建上下文是 `playedu-api` 时，该根目录 ignore 文件通常不会生效，因此本地 JAR 可以被复制；如果以后在 `playedu-api` 目录新增 `.dockerignore` 并排除 `target`，该 Dockerfile 会因找不到 JAR 而构建失败。

## 四、`docker/mysql/Dockerfile`

该文件基于 MySQL 8.1 镜像增加项目自己的数据库配置。

```dockerfile
FROM registry.cn-hangzhou.aliyuncs.com/hzbs/mysql:8.1
```

- 使用阿里云镜像仓库中的 MySQL 8.1 基础镜像。
- MySQL 的数据目录、初始化逻辑、默认端口和启动入口均继承自基础镜像。

```dockerfile
COPY my.cnf /etc/mysql/conf.d/my.cnf
```

- 将构建上下文中的 `my.cnf` 放入 MySQL 自动读取的附加配置目录。
- 构建上下文应为 `docker/mysql`，这样 `my.cnf` 才位于 Dockerfile 所引用的位置。

```dockerfile
RUN chmod 0444 /etc/mysql/conf.d/my.cnf
```

- 把配置文件权限设置为所有用户只读，任何用户都不能在容器中直接修改它。
- 配置变更应通过修改源码中的 `my.cnf` 并重新构建镜像完成。

可从项目根目录构建：

```bash
docker build -t playedu-mysql:8.1 -f docker/mysql/Dockerfile docker/mysql
```

## 五、四个文件之间的区别

| 对比项 | 根 `Dockerfile` | API `Dockerfile` | API `Dockerfile.local` | MySQL `Dockerfile` |
| --- | --- | --- | --- | --- |
| 是否编译前端 | 是，三个前端 | 否 | 否 | 否 |
| 是否编译后端 | 是 | 是 | 否，使用现成 JAR | 否 |
| 最终运行内容 | Nginx + Java API | Java API | Java API | MySQL |
| 主要端口 | 9898、9800、9801、9900 | 9898 | 9898 | 继承 MySQL 默认端口 3306 |
| 典型场景 | 一体化部署 | API 独立部署 | 本地快速制作镜像 | 带定制配置的数据库 |

## 六、值得关注的构建与运行问题

1. **基础镜像能力**：根 Dockerfile 的最终阶段会执行 `nginx`，但镜像名称体现的是 Temurin。该私有定制镜像必须确实包含 Nginx 和 `/etc/nginx/sites-enabled` 配置机制。
2. **数据库等待方式**：固定 `sleep 15` 不能保证 MySQL 已就绪，也可能造成不必要等待，建议改为健康检查或连接探测。
3. **容器主进程与信号**：根 Dockerfile 使用 shell 形式 `CMD` 且同时启动两个进程，信号转发和任一子进程异常退出的处理不够健壮。
4. **测试被完全跳过**：两个源码构建流程都使用 `-Dmaven.test.skip=true package`，镜像构建不会验证测试。
5. **依赖可重复性**：前端使用 `pnpm i`，可根据 CI 策略考虑 `--frozen-lockfile`。
6. **镜像版本可重复性**：基础镜像只使用 `20-alpine`、`17`、`8.1` 等可变标签；若需要严格可复现和供应链控制，可固定到更精确版本或镜像 digest。
7. **构建上下文**：每个 `COPY` 的源路径都相对于构建上下文，而不是相对于 Dockerfile 所在位置。使用本文给出的上下文可避免 `COPY ... not found` 错误。
8. **端口发布**：所有 `EXPOSE` 都只是声明，实际访问仍需通过 `-p` 或 Compose 显式映射。
