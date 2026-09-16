FROM eclipse-temurin:17-jre

WORKDIR /home/app

COPY build/libs/java-gradle-app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
