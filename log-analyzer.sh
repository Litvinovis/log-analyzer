#!/bin/bash

HOME_PATH="$(dirname $(readlink -f "$0"))"
APP_NAME="$(basename "$0" .sh)"
JAR_FILE="log-analyzer-1.0.0.jar"

COMMANDLINE="/usr/lib/jvm/jre-21/bin/java \
            -Dcom.sun.management.jmxremote \
            -Dcom.sun.management.jmxremote.port=19485 \
            -Dcom.sun.management.jmxremote.local.only=false \
            -Dcom.sun.management.jmxremote.authenticate=false \
            -Dcom.sun.management.jmxremote.access.file=/u01/fraudmon/security/jmxremote.access \
            -Dcom.sun.management.jmxremote.password.file=/u01/fraudmon/security/jmxremote.password \
            -Dcom.sun.management.jmxremote.ssl=false \
            -Xms128m -Xmx512m -Xdebug -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=15485 \
            -Dserver.port=18765 \
            -Dserver.tomcat.threads.max=16 \
            --add-opens=java.base/java.nio=ALL-UNNAMED \
            --add-opens=java.base/java.time=ALL-UNNAMED \
            --add-opens=java.base/jdk.internal.access=ALL-UNNAMED \
            --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
            --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
            --add-opens=java.base/sun.util.calendar=ALL-UNNAMED \
            --add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED \
            --add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED \
            --add-opens=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED \
            --add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED \
            --add-opens=java.base/java.io=ALL-UNNAMED \
            --add-opens=java.base/java.net=ALL-UNNAMED \
            --add-opens=java.base/java.util=ALL-UNNAMED \
            --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
            --add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED \
            --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
            --add-opens=java.base/java.lang=ALL-UNNAMED \
            --add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
            --add-opens=java.base/java.math=ALL-UNNAMED \
            --add-opens=java.sql/java.sql=ALL-UNNAMED \
            --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
            --add-opens=java.base/java.text=ALL-UNNAMED \
            --add-opens=java.management/sun.management=ALL-UNNAMED \
            --add-opens java.desktop/java.awt.font=ALL-UNNAMED \
            --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED \
            --add-exports=java.base/sun.nio.ch=ALL-UNNAMED \
            --add-exports=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED \
            --add-exports=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED \
            --add-exports=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED \
            -XX:+UnlockExperimentalVMOptions \
            -XX:+UseG1GC \
            -XX:MaxGCPauseMillis=20 \
            -XX:ParallelGCThreads=20 \
            -XX:ConcGCThreads=5 \
            -XX:InitiatingHeapOccupancyPercent=70 \
            -XX:+ScavengeBeforeFullGC \
            -XX:+DisableExplicitGC \
            -Xlog:gc*:file=$HOME_PATH/logs/log-analyzer-GC.log:time,level,tags:filesize=10M:filecount=100 \
            -Dlogback.configurationFile=$HOME_PATH/config/logback.xml \
            -Dlogging.config=$HOME_PATH/config/logback.xml \
            -Dspring.config.location=file:$HOME_PATH/config/application.yml \
            -jar $HOME_PATH/$JAR_FILE"

function start_helper() {
    echo "${APP_NAME^^} STARTED MD5SUM. LOGFILE LOCATION: ${HOME_PATH}/${APP_NAME}_start.log"
    nohup md5sum "${HOME_PATH}/${JAR_FILE}" > "${HOME_PATH}/${APP_NAME}_start.log" 2>&1 &
    nohup $1 > /dev/null 2>&1 &
}

function start_debug_helper() {
    echo "${APP_NAME^^} STARTED IN DEBUG MODE. LOGFILE LOCATION: ${HOME_PATH}/${APP_NAME}_debug.log)"
    nohup $1 > "${APP_NAME}_debug.log" 2>&1 &
}

function start_app() {

    export CURRENT_DIR="$(pwd)"
    cd "$HOME_PATH"
    if [[ -z "$(check_run $HOME_PATH/$JAR_FILE)" ]];then
        ATTEMPTS=0

        until [[ -n "$(check_run $HOME_PATH/$JAR_FILE)" ]];do
            if (( $ATTEMPTS < 3 ));then
                echo "Trying to start $APP_NAME..."

                if [[ $2 == "debug" ]];then
                    start_debug_helper "$COMMANDLINE"
                else
                    start_helper "$COMMANDLINE"
                fi

                sleep 5
                ATTEMPTS=$((ATTEMPTS+1))
            else
                echo "Can not start $APP_NAME!"
                exit 1
            fi
        done

        echo "$APP_NAME now running with following PIDs: $(check_run $HOME_PATH/$JAR_FILE)"
    else
        echo "$APP_NAME already running. Current PIDs: $(check_run $HOME_PATH/$JAR_FILE)"
    fi

    cd $CURRENT_DIR

}

function status_app() {
    if [[ -z "$(check_run $HOME_PATH/$JAR_FILE)" ]];then
        echo "$APP_NAME with path \"$HOME_PATH/$JAR_FILE\" is not running"
    else
        echo "$APP_NAME is running with path \"$HOME_PATH/$JAR_FILE\" and PID: $(check_run $HOME_PATH/$JAR_FILE)"
    fi
}

function stop_helper() {
    kill $1
}

function stop_app() {
        if [[ -z "$(check_run $HOME_PATH/$JAR_FILE)" ]];then
        echo "$APP_NAME is not running"

    elif [[ $2 == "force" ]];then
        echo "Force stopping these $APP_NAME PIDs: $(check_run $HOME_PATH/$JAR_FILE)"
        stop_force "$(check_run $HOME_PATH/$JAR_FILE)"
        sleep 5
    else
        COUNT=0
        ATTEMPTS=0
        echo "Stopping these $APP_NAME PIDs: $(check_run $HOME_PATH/$JAR_FILE)"
        stop_helper "$(check_run $HOME_PATH/$JAR_FILE)"
        sleep 5

        until [[ -z "$(check_run $HOME_PATH/$JAR_FILE)" ]];do
            if (( $ATTEMPTS < 2 ));then
                if (( $COUNT < 3 ));then
                    echo "Performing 5 seconds timeout..."
                    sleep 5
                    COUNT=$((COUNT+1))
                else
                    echo "One more attempt to stop $APP_NAME correctly: $(check_run $HOME_PATH/$JAR_FILE)"
                    stop_helper "$(check_run $HOME_PATH/$JAR_FILE)"
                    sleep 5
                    COUNT=0
                    ATTEMPTS=$((ATTEMPTS+1))
                fi
            else
                echo "Can not stop these PIDs of $APP_NAME correctly: $(check_run $HOME_PATH/$JAR_FILE)...Forced stop!"
                stop_force "$(check_run $HOME_PATH/$JAR_FILE)"
                sleep 5
                break
            fi
        done
    fi

    if [[ -z "$(check_run $HOME_PATH/$JAR_FILE)" ]];then
        echo "$APP_NAME app PID file does not exists"
    else
        echo "PIDs $(check_run $HOME_PATH/$JAR_FILE) WAS NOT STOPPED!!!"
    fi

}

function stop_force() {
    kill -9 $1
}

function check_run() {
    echo $(pgrep -f $1)
}

case "$1" in
    start)
        start_app $@
        ;;
    status)
        status_app
        ;;
    stop)
        stop_app $@
        ;;
    restart)
        echo "Performing $APP_NAME restart"
        stop_app
        start_app $@
        ;;
    *)
        echo "usage: service {start|start debug|stop|restart|stop force}" >&2
        exit 0
        ;;
esac
