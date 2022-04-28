# micronaut-netty-vs-spring-cloud-proxy

## Before running either of the two sample programs:

1) Create a java key store (JSK) in the root directory named "keystore.jks" with the password "changeit"
2) Build the micronaut/netty version of the sample app:  cd micronaut-netty; mvn package
3) Build the spring cloud version of the sample app:  cd spring-cloud; mvn package

## To run the micronaut/netty version of the sameple app:

java -Xmx512m \
     -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -Dcom.sun.management.jmxremote=true \
     -Dcom.sun.management.jmxremote.rmi.port=5000 \
     -Dcom.sun.management.jmxremote.port=5000 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -Dcom.sun.management.jmxremote.local.only=false \
     -Djava.rmi.server.hostname=127.0.0.1 \
     -Djava.security.egd=file:/dev/./urandom \
     -jar ./micronaut-netty/target/metrics-1.0-SNAPSHOT.jar

## To run the spring cloud version of the sameple app:

java -Xmx512m \
     -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -Dcom.sun.management.jmxremote=true \
     -Dcom.sun.management.jmxremote.rmi.port=5000 \
     -Dcom.sun.management.jmxremote.port=5000 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -Dcom.sun.management.jmxremote.local.only=false \
     -Djava.rmi.server.hostname=127.0.0.1 \
     -Djava.security.egd=file:/dev/./urandom \
     -jar ./spring-cloud/target/metrics-1.0-SNAPSHOT.jar

## To run the JMeter laod script:

/path/to/jmeter/inatsll/bin/jmeter -n -t SAMPLE.jmx -l SAMPLE.out.out -Jthread-count=5