FROM eclipse-temurin:17-jdk-alpine AS shared
WORKDIR /shared
COPY shared-security/.mvn/ .mvn/
COPY shared-security/mvnw shared-security/pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -Dmaven.repo.local=/root/.m2/repository dependency:go-offline
COPY shared-security/src ./src
RUN ./mvnw -q -Dmaven.repo.local=/root/.m2/repository clean install -DskipTests

FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY --from=shared /root/.m2/repository /root/.m2/repository
COPY file-service/.mvn/ .mvn/
COPY file-service/mvnw file-service/pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -Dmaven.repo.local=/root/.m2/repository dependency:go-offline
COPY file-service/src ./src
RUN ./mvnw -q -Dmaven.repo.local=/root/.m2/repository clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN adduser -D -u 1000 appuser
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
