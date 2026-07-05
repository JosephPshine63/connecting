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
COPY call-service/.mvn/ .mvn/
COPY call-service/mvnw call-service/pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -Dmaven.repo.local=/root/.m2/repository dependency:go-offline
COPY call-service/src ./src
RUN ./mvnw -q -Dmaven.repo.local=/root/.m2/repository clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
