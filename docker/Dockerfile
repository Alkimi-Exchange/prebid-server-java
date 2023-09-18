FROM eclipse-temurin:17
ARG BUILD_ID
ARG APP_NAME

ENV APP_NAME $APP_NAME
ENV APP_WORKDIR="/opt/alkimi"
ENV USER_NAME="appuser"
ENV HEAP_OPTS="-Xms512M -Xmx512M"
ENV EXCHANGE_URL="http://exchange:8080"

RUN adduser "$USER_NAME"
RUN mkdir -p --mode=755 "$APP_WORKDIR/$APP_NAME"
RUN chown -R "$USER_NAME":"$USER_NAME" "$APP_WORKDIR"

COPY ./target/${APP_NAME}-${BUILD_ID}.jar ${APP_WORKDIR}/${APP_NAME}/${APP_NAME}.jar
COPY ./config/prebid-server-config.yaml ${APP_WORKDIR}/${APP_NAME}/prebid-server-config.yaml
COPY ./config/prebid-server-settings.yaml ${APP_WORKDIR}/${APP_NAME}/prebid-server-settings.yaml

USER "$USER_NAME"

WORKDIR $APP_WORKDIR/$APP_NAME

EXPOSE 8060
EXPOSE 8070
EXPOSE 8080

CMD java -jar ${HEAP_OPTS} -Dadapters.alkimi.endpoint=${EXCHANGE_URL} \
 -Dspring.config.additional-location=./prebid-server-config.yaml ${APP_NAME}.jar
