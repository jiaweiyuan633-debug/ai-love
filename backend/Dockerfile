# 多阶段构建：Maven 打包 + JRE 运行
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# PDF 报告输出目录
RUN mkdir -p /app/data/reports
ENV TZ=Asia/Shanghai JAVA_OPTS=""
EXPOSE 8101
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=9000"]
