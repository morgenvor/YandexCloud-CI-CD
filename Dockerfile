FROM eclipse-temurin:17-jre

WORKDIR /home/app

COPY build/libs/docker-exercises-project-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
