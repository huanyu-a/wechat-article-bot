# syntax=docker/dockerfile:1.7

ARG NODE_IMAGE=node:22-bookworm-slim
ARG MAVEN_IMAGE=maven:3.9.16-eclipse-temurin-17-noble
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre-noble

FROM ${NODE_IMAGE} AS frontend
WORKDIR /workspace/webui
COPY webui/package.json webui/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY webui/ ./
RUN npm run build -- --outDir dist --emptyOutDir

FROM ${MAVEN_IMAGE} AS backend
WORKDIR /workspace
COPY pom.xml ./
COPY src/ ./src/
COPY --from=frontend /workspace/webui/dist/ ./src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests -Dskip.installnodenpm -Dskip.npm package

FROM ${RUNTIME_IMAGE} AS runtime
WORKDIR /app

ARG APP_VERSION=dev
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="wechat-article-bot" \
      org.opencontainers.image.description="AI-powered WeChat article management workspace" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}"

ENV SERVER_PORT=8081 \
    STORAGE_PATH=/app/data/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

RUN mkdir -p /app/data/uploads && chown -R 10001:10001 /app && chmod -R g=u /app
COPY --from=backend --chown=10001:10001 /workspace/target/article-bot-*.jar /app/app.jar

USER 10001:10001
EXPOSE 8081
VOLUME ["/app/data/uploads"]

# 健康检查：容器内自打一个 HTTP 请求到 /api/health，要求首行出现 " 200 "。
# 为什么不用 curl/wget：基础镜像 eclipse-temurin:17-jre-noble 里没有它们，但**有 bash 5.2**，
# `/dev/tcp` 是 bash 内建的重定向，不需要任何额外二进制。
# 注意 `/dev/tcp/HOST/PORT` 是斜杠形式——写成 `HOST:PORT` 会报 "No such file or directory"。
# 端口取 SERVER_PORT（镜像 ENV 里是 8081），换端口时健康检查跟着走。
# 实测（在跑容器内以同样命令串执行）：正常 200 时 exit 0（约 0.4s）；端口不通或非 200 时 exit 1，不会挂住。
#
# ⚠️ 必须用 `podman build --format docker` 构建，否则这一行会被**静默忽略**：
#   podman/buildah 默认输出 OCI 镜像格式，而 HEALTHCHECK 不是 OCI 规范的一部分，
#   构建时只打一句 `level=warning ... HEALTHCHECK is not supported for OCI image format
#   and will be ignored. Must use docker format`，产物里根本没有 Healthcheck 字段。
#   实测对比：同一条 Dockerfile 分别构建 → OCI 产物 `Healthcheck=null`；
#   `--format docker` 产物 `Healthcheck={"Test":["CMD","bash","-c",...],"Interval":15s,...}`。
#   现有 `localhost/wechat-article-bot:local` 就是 OCI 格式（ManifestType 为
#   `application/vnd.oci.image.manifest.v1+json`），即按 docs/dev/docker-deployment.md:187
#   的 `podman build -f Dockerfile -t wechat-article-bot:local .` 构建出来的镜像不带健康检查。
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT:-8081} && printf \"GET /api/health HTTP/1.0\\r\\nHost: 127.0.0.1\\r\\nConnection: close\\r\\n\\r\\n\" >&3 && grep -q \" 200 \" <&3"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
