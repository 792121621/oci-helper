# =========================
# 第一阶段：Maven 编译
# =========================
FROM maven:3-eclipse-temurin-21 AS builder

WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests \
    && cp target/oci-helper-*.jar /app/oci-helper.jar


# =========================
# 第二阶段：安装系统依赖
# =========================
FROM eclipse-temurin:21-jre-jammy AS base-with-tools

ENV LANG=zh_CN.UTF-8 \
    LC_ALL=zh_CN.UTF-8 \
    TZ=Asia/Shanghai \
    DEBIAN_FRONTEND=noninteractive

# 安装依赖
RUN apt-get update -o Acquire::Retries=3 && \
    apt-get install -y --no-install-recommends \
        openssh-client \
        lsof \
        curl \
        locales \
        tzdata \
        ca-certificates && \
    sed -i '/zh_CN.UTF-8/s/^# //' /etc/locale.gen && \
    locale-gen && \
    mkdir -p /root/.ssh && \
    printf 'Host *\n  HostKeyAlgorithms +ssh-rsa\n  PubkeyAcceptedKeyTypes +ssh-rsa\n' > /root/.ssh/config && \
    chmod 700 /root/.ssh && \
    chmod 600 /root/.ssh/config && \
    ln -snf /usr/share/zoneinfo/${TZ} /etc/localtime && \
    echo "${TZ}" > /etc/timezone && \
    rm -rf /var/lib/apt/lists/*


# =========================
# 第三阶段：最终运行镜像
# =========================
FROM base-with-tools

ENV OCI_HELPER_VERSION=3.5.1

WORKDIR /app/oci-helper

COPY --from=builder /app/oci-helper.jar .

EXPOSE 8818

CMD exec java \
    --add-opens java.base/java.net=ALL-UNNAMED \
    --add-opens java.base/sun.net.www.protocol.https=ALL-UNNAMED \
    -jar oci-helper.jar | tee -a /var/log/oci-helper.log
