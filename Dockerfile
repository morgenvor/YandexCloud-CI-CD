FROM eclipse-temurin:17-jre

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid 10001 --home-dir /home/app --create-home app

WORKDIR /home/app

COPY --chown=app:app build/libs/java-gradle-app.jar app.jar

EXPOSE 8080

USER 10001:10001

ENTRYPOINT ["java", "-jar", "app.jar"]
