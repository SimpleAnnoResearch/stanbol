#!/bin/sh

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home
cd launchers

# Note: the SimpleAnno category service does not work in the stable configuration
#java -Xmx1g -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar stable/target/org.apache.stanbol.launchers.stable-1.0.1-SNAPSHOT.jar
java -Xmx1g -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar full/target/org.apache.stanbol.launchers.full-1.0.1-SNAPSHOT.jar -no-security

