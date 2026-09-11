FROM registry.cn-hangzhou.aliyuncs.com/hzbs/eclipse-temurin:17 AS builder

WORKDIR /app

COPY mvnw          /app/mvnw
COPY .mvn          /app/.mvn
COPY pom.xml                    /app/pom.xml
COPY eleadinedu-api/pom.xml        /app/eleadinedu-api/pom.xml
COPY eleadinedu-common/pom.xml     /app/eleadinedu-common/pom.xml
COPY eleadinedu-course/pom.xml     /app/eleadinedu-course/pom.xml
COPY eleadinedu-resource/pom.xml   /app/eleadinedu-resource/pom.xml
COPY eleadinedu-system/pom.xml     /app/eleadinedu-system/pom.xml

RUN /app/mvnw -B -DskipTests dependency:go-offline

COPY . /app

RUN /app/mvnw -B -Dmaven.test.skip=true package

FROM registry.cn-hangzhou.aliyuncs.com/hzbs/eclipse-temurin:17 AS base

WORKDIR /app

COPY --from=builder /app/eleadinedu-api/target/eleadinedu-api.jar /app/app.jar

RUN chmod +x /app/app.jar

EXPOSE 9898/tcp

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
