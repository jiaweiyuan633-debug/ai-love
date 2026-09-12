# 多阶段构建：Maven 打包 + JRE 运行
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY settings.xml .
COPY pom.xml .
RUN mvn -q -s settings.xml dependency:go-offline
COPY src ./src
RUN mvn -q -s settings.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# PDF 报告输出目录
RUN mkdir -p /app/data/reports
ENV TZ=Asia/Shanghai JAVA_OPTS=""
# 与 ENTRYPOINT 中 --server.port=9000、s.yaml customContainerConfig.port 保持一致
EXPOSE 9000
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=9000"]
