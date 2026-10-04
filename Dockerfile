FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/seat-reservation-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
