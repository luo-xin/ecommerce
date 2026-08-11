#!/usr/bin/env bash
# 以挂载 JaCoCo agent 的方式启动 ecommerce（情况 B：功能/接口测试覆盖一个运行中的服务）
#
# 用法：
#   1) 设置 JDK17 与 maven（按你的环境改）：
#        export JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.8/libexec/openjdk.jdk/Contents/Home
#        export PATH="$JAVA_HOME/bin:/private/tmp/apache-maven-3.9.6/bin:$PATH"
#   2) bash ecommerce/scripts/run-with-coverage.sh
#   3) 用前端点击 / 跑 api_test 去打这个服务（覆盖率在该 JVM 内累积）
#   4) 在覆盖率平台对该项目点「运行」，平台会 dump+report 生成 jacoco.xml
#
# agent 以 tcpserver 模式暴露在 localhost:6300，供平台 jacoco:dump 拉取覆盖数据。
set -euo pipefail
cd "$(dirname "$0")/.."

JACOCO_VERSION="${JACOCO_VERSION:-0.8.11}"
JACOCO_PORT="${JACOCO_PORT:-6300}"
AGENT_JAR="$HOME/.m2/repository/org/jacoco/org.jacoco.agent/${JACOCO_VERSION}/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar"

if [ ! -f "$AGENT_JAR" ]; then
  echo "[info] 未找到 jacoco agent jar，先拉取依赖到本地仓库 ..."
  mvn -q org.apache.maven.plugins:maven-dependency-plugin:3.6.1:get \
    -Dartifact="org.jacoco:org.jacoco.agent:${JACOCO_VERSION}:jar:runtime"
fi

echo "[info] 启动 ecommerce，jacoco agent → tcpserver localhost:${JACOCO_PORT}"
exec mvn spring-boot:run \
  -Dspring-boot.run.profiles=test \
  -Dspring-boot.run.arguments="--ecommerce.test-backdoor.enabled=true" \
  -Dspring-boot.run.jvmArguments="-javaagent:${AGENT_JAR}=output=tcpserver,address=localhost,port=${JACOCO_PORT}"
