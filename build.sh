mvn install -q -nsu -Dmaven.test.redirectTestOutputToFile=true -P '!integration' -DskipTests=true;
echo "------"