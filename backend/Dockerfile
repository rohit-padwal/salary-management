FROM eclipse-temurin:21-jdk AS build
# The wrapper pins the ZIP checksum; unzip prevents its fallback to a different archive.
RUN apt-get update && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system salary && useradd --system --gid salary --home /app salary
WORKDIR /app
COPY --from=build --chown=salary:salary /workspace/target/salary-management-0.0.1-SNAPSHOT.jar app.jar
USER salary
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
