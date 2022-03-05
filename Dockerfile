FROM maven:3.6.2-jdk-8
COPY ./ /app
WORKDIR /app
RUN mvn clean compile
ENTRYPOINT sh