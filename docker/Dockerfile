FROM eclipse-temurin:17
ARG APP_NAME

ENV APP_NAME $APP_NAME
ENV APP_WORKDIR="/opt/alkimi"
ENV USER_NAME="appuser"
ENV HEAP_OPTS="-Xms512M -Xmx512M"

RUN adduser "$USER_NAME"
RUN mkdir -p --mode=755 "$APP_WORKDIR/$APP_NAME/stored"
RUN chown -R "$USER_NAME":"$USER_NAME" "$APP_WORKDIR"

COPY ./target/${APP_NAME}.jar ${APP_WORKDIR}/${APP_NAME}/${APP_NAME}.jar
COPY ./config/prebid-server-*.yaml ${APP_WORKDIR}/${APP_NAME}/
COPY ./config/*.json ${APP_WORKDIR}/${APP_NAME}/stored/

USER "$USER_NAME"

WORKDIR $APP_WORKDIR/$APP_NAME

EXPOSE 8060
EXPOSE 8070
EXPOSE 8080

CMD java -jar ${HEAP_OPTS} -Dspring.config.additional-location=./prebid-server-config.yaml ${APP_NAME}.jar
