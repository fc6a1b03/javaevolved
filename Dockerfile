# ============================================================================
# 高性能多阶段构建 Dockerfile
# 使用最新版本技术栈，优化镜像大小和启动速度
# ============================================================================

# ----------------------------------------------------------------------------
# Stage 1: 构建阶段 - 使用 Eclipse Temurin JDK 25 + JBang 生成静态 HTML
# ----------------------------------------------------------------------------
FROM docker.io/eclipse-temurin:25-jdk-noble AS builder

# 安装 JBang
# 使用官方安装脚本，指定最新稳定版本
RUN curl -sL https://sh.jbang.dev | bash -s - app setup

# 设置 JBang 环境变量
ENV PATH="/root/.jbang/bin:${PATH}"
ENV JBANG_DOWNLOAD_VERSION=0.125.1

WORKDIR /workspace

# 优化：先复制依赖文件，利用 Docker 层缓存
COPY html-generators/categories.properties html-generators/locales.properties html-generators/
COPY templates/ templates/
COPY content/ content/
COPY translations/ translations/
COPY site/ site/

# 复制生成脚本
COPY html-generators/generate.java html-generators/

# 使用 JBang 生成所有 HTML 页面
# --all-locales 生成所有语言版本
RUN jbang html-generators/generate.java --all-locales

# 清理不必要的文件以减少最终镜像大小
RUN rm -f site/*.html.bak site/**/*.html.bak 2>/dev/null || true

# ----------------------------------------------------------------------------
# Stage 2: 运行阶段 - 使用轻量级静态文件服务器
# ----------------------------------------------------------------------------
FROM docker.io/joseluisq/static-web-server:2

# static-web-server 镜像默认使用 UID 1000 (非 root)
# 直接复制文件到默认的 /public 目录
COPY --from=builder --chown=1000:1000 /workspace/site/ /public/

# 健康检查 - 检查根路径是否可访问
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://localhost:8080/ > /dev/null 2>&1 || exit 1

# 暴露端口
EXPOSE 8080

# 使用环境变量配置 static-web-server
# SERVER_ROOT 默认为 /public，不需要显式设置
ENV SERVER_PORT=8080 \
    SERVER_LOG_LEVEL=info \
    SERVER_COMPRESSION=true \
    SERVER_COMPRESSION_LEVEL=best_compression \
    SERVER_CACHE_CONTROL_HEADERS=true \
    SERVER_SECURITY_HEADERS=true \
    SERVER_CORS_ALLOW_ORIGINS=* \
    SERVER_CORS_ALLOW_METHODS="GET, HEAD, OPTIONS"

# 启动命令 (使用镜像默认入口点)
CMD ["static-web-server"]
