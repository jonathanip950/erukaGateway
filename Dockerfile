FROM adoptopenjdk/openjdk11:jre-11.0.10_9-centos
ARG JAR_FILE=target/jebsen-gateway-*.jar
COPY ${JAR_FILE} app.jar
COPY src/main/resources/jebsen-auth.keystore jebsen-auth.keystore
ENV TZ=Asia/Hong_Kong
EXPOSE 8080
ENTRYPOINT ["java","-jar", "-Xmx6144M", "/app.jar"]