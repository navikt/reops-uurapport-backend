FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25
LABEL maintainer="team-researchops"

ARG JAR_PATH

ADD $JAR_PATH /app/app.jar

EXPOSE 8080
CMD ["/app/app.jar"]