FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/classes ./classes
COPY target/dependency ./lib
ENTRYPOINT ["java","-Dfile.encoding=UTF-8","-cp","classes:lib/*","ru.kashpirev.RSocketDemo"]
CMD ["demo"]
