#!/bin/bash

JAVA_HOME=/usr/local/jdk1.5.0_07

cd /home/upload/update/2017/primary;

if [ $# -lt 2 ]
then
 echo "USAGE: $0 <start Id> <end Id>";
 exit 1;
fi

$JAVA_HOME/bin/java -classpath ./:./lib/jsdk-24.jar:./lib/im4java-1.1.0-1.5.jar:./lib/log4j-1.2.14.jar:./lib/mysql-connector-java-5.0.0-beta-bin.jar:./lib/cos.jar:./lib/activation.jar:./lib/mail.jar:./lib/commons-logging-1.1.jar:./lib/commons-httpclient-3.0.1.jar:./lib/commons-codec-1.3.jar:./lib/commons-io.jar org/usagreencardlottery/main/ScrapperWithoutShutdownHook $1 $2
exit 0
