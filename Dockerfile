FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY agentassist.war app.war
EXPOSE 9099
ENTRYPOINT ["java", "-jar", "app.war"]
