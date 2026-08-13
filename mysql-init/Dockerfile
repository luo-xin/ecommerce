# SQL 打进镜像而非 bind mount：Jenkins 容器内跑 compose 时 bind 源路径
# 对 daemon（Desktop VM）不可见，会退化成空目录；build context 走 daemon 上传协议则无此问题。
FROM mysql:8.0
COPY init.sql /docker-entrypoint-initdb.d/01-init.sql
COPY demo_data.sql /docker-entrypoint-initdb.d/02-demo-data.sql
