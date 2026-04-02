FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY agentassist.war app.war
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.war"]
