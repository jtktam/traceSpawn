java \
-Dotel.javaagent.configuration-file=./opentelemetry-javaagent.properties \
-Dotel.javaagent.debug=true \
-javaagent:./opentelemetry-javaagent.jar \
-jar target/trace-1.0.0-SNAPSHOT.jar \
--spring.profiles.active=local
