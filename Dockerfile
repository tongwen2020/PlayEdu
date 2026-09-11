FROM registry.cn-hangzhou.aliyuncs.com/hzbs/node:20-alpine AS node-builder

WORKDIR /app

COPY eleadinedu-admin/package.json eleadinedu-admin/pnpm-lock.yaml /app/admin/
COPY eleadinedu-pc/package.json    eleadinedu-pc/pnpm-lock.yaml    /app/pc/
COPY eleadinedu-h5/package.json    eleadinedu-h5/pnpm-lock.yaml    /app/h5/

RUN cd /app/admin && pnpm i
RUN cd /app/pc    && pnpm i
RUN cd /app/h5    && pnpm i

COPY eleadinedu-admin /app/admin
RUN cd /app/admin && VITE_APP_URL=/api/ pnpm build

COPY eleadinedu-pc /app/pc
RUN cd /app/pc && VITE_APP_URL=/api/ pnpm build

COPY eleadinedu-h5 /app/h5
RUN cd /app/h5 && VITE_APP_URL=/api/ pnpm build

FROM registry.cn-hangzhou.aliyuncs.com/hzbs/eclipse-temurin:17 AS java-builder

WORKDIR /app

COPY eleadinedu-api/mvnw          /app/mvnw
COPY eleadinedu-api/.mvn          /app/.mvn
COPY eleadinedu-api/pom.xml                     /app/pom.xml
COPY eleadinedu-api/eleadinedu-api/pom.xml         /app/eleadinedu-api/pom.xml
COPY eleadinedu-api/eleadinedu-common/pom.xml      /app/eleadinedu-common/pom.xml
COPY eleadinedu-api/eleadinedu-course/pom.xml      /app/eleadinedu-course/pom.xml
COPY eleadinedu-api/eleadinedu-resource/pom.xml    /app/eleadinedu-resource/pom.xml
COPY eleadinedu-api/eleadinedu-system/pom.xml      /app/eleadinedu-system/pom.xml
COPY eleadinedu-api/eleadinedu-exam/pom.xml        /app/eleadinedu-exam/pom.xml

RUN sed -i 's/\r$//' /app/mvnw && sh /app/mvnw -B -DskipTests dependency:go-offline

COPY eleadinedu-api /app

RUN sed -i 's/\r$//' /app/mvnw && sh /app/mvnw -B -Dmaven.test.skip=true package

FROM registry.cn-hangzhou.aliyuncs.com/hzbs/eclipse-temurin:17 AS base

COPY --from=java-builder /app/eleadinedu-api/target/eleadinedu-api.jar /app/api/app.jar

COPY --from=node-builder /app/admin/dist /app/admin
COPY --from=node-builder /app/pc/dist /app/pc
COPY --from=node-builder /app/h5/dist /app/h5

COPY docker/nginx/conf/nginx.conf /etc/nginx/sites-enabled/default

EXPOSE 9898
EXPOSE 9800
EXPOSE 9801
EXPOSE 9900

CMD nginx; echo "Waiting for MySQL to start..."; sleep 15; java -jar /app/api/app.jar
