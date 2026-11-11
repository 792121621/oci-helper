FROM eclipse-temurin:21-jre-jammy AS base-with-tools

ENV LANG=zh_CN.UTF-8 \
    LC_ALL=zh_CN.UTF-8 \
    TZ=Asia/Shanghai \
    DEBIAN_FRONTEND=noninteractive

# 1. 更新软件源
RUN apt-get update -o Acquire::Retries=3

# 2. 安装依赖
RUN apt-get install -y --no-install-recommends \
    openssh-client \
    lsof \
    curl \
    locales \
    tzdata \
    ca-certificates

# 3. 配置中文 Locale
RUN sed -i '/zh_CN.UTF-8/s/^# //' /etc/locale.gen && \
    locale-gen

# 4. 配置 SSH
RUN mkdir -p /root/.ssh && \
    printf 'Host *\n  HostKeyAlgorithms +ssh-rsa\n  PubkeyAcceptedKeyTypes +ssh-rsa\n' > /root/.ssh/config && \
    chmod 700 /root/.ssh && \
    chmod 600 /root/.ssh/config

# 5. 配置时区
RUN ln -snf /usr/share/zoneinfo/${TZ} /etc/localtime && \
    echo "${TZ}" > /etc/timezone

# 6. 清理缓存
RUN rm -rf /var/lib/apt/lists/*
