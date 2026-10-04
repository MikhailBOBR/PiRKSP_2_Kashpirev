FROM eclipse-temurin:21-jdk-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY target/app.jar app.jar
ENTRYPOINT ["java","-Dfile.encoding=UTF-8","-jar","app.jar"]
