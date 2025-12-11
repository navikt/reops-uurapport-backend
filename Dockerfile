FROM gcr.io/distroless/java25
LABEL maintainer="team-researchops"

ARG JAR_PATH

ADD $JAR_PATH /app/app.jar

EXPOSE 8080
CMD ["/app/app.jar"]