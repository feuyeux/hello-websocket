export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.3.1.jdk/Contents/Home
mvn -v
echo "======================"
mvn clean package -DskipTests
