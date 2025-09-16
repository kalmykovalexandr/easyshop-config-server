FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/app.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java","-jar","app.jar"]


