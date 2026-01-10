FROM eclipse-temurin:17-jdk
MAINTAINER Traversium Developers
WORKDIR /opt/tenant-service

COPY target/*.jar app.jar
ENTRYPOINT ["java","-jar","/opt/tenant-service/app.jar"]