FROM maven:3.6.2-jdk-8
COPY ./ /app
WORKDIR /app
RUN --mount=type=cache,target=/root/.m2/repository mvn clean compile
ENTRYPOINT sh