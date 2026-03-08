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

# 使用非 root 用户运行 (安全最佳实践)
USER root

# 创建网站目录并设置权限
RUN mkdir -p /var/www/html && chown -R static-web-server:static-web-server /var/www/html

# 从构建阶段复制生成的静态文件
COPY --from=builder --chown=static-web-server:static-web-server /workspace/site/ /var/www/html/

# 切换回非 root 用户
USER static-web-server

# 健康检查 - 检查根路径是否可访问
# static-web-server 镜像基于 busybox，支持 wget
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://localhost:8080/ > /dev/null 2>&1 || exit 1

# 暴露端口
EXPOSE 8080

# 使用环境变量配置 static-web-server
ENV SERVER_PORT=8080 \
    SERVER_ROOT=/var/www/html \
    SERVER_LOG_LEVEL=info \
    SERVER_COMPRESSION=true \
    SERVER_COMPRESSION_LEVEL=best_compression \
    SERVER_CACHE_CONTROL_HEADERS=true \
    SERVER_SECURITY_HEADERS=true \
    SERVER_CORS_ALLOW_ORIGINS=* \
    SERVER_CORS_ALLOW_METHODS="GET, HEAD, OPTIONS"

# 启动命令 (使用镜像默认入口点)
CMD ["static-web-server"]
