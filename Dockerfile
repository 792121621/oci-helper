FROM eclipse-temurin:21-jre-jammy AS base-with-tools

ENV LANG=zh_CN.UTF-8 \
    LC_ALL=zh_CN.UTF-8 \
    TZ=Asia/Shanghai \
    DEBIAN_FRONTEND=noninteractive

# 安装依赖
RUN set -eux; \
    for i in 1 2 3; do \
        apt-get update && break || { \
            echo "APT update failed, retry $i/3"; \
            sleep 5; \
        }; \
    done; \
    apt-get install -y --no-install-recommends \
        openssh-client \
        lsof \
        curl \
        locales \
        tzdata; \
    rm -rf /var/lib/apt/lists/*

# 配置 SSH
RUN mkdir -p /root/.ssh && \
    printf 'Host *\n  HostKeyAlgorithms +ssh-rsa\n  PubkeyAcceptedKeyTypes +ssh-rsa\n' \
    > /root/.ssh/config && \
    chmod 700 /root/.ssh && \
    chmod 600 /root/.ssh/config

# 配置中文语言环境
RUN locale-gen zh_CN.UTF-8

# 配置时区
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone
