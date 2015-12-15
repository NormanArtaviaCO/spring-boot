mvn install -q -nsu -Dmaven.test.redirectTestOutputToFile=true -P '!integration' -DskipTests=true;
export ARTIFACTS_PATHS="$(git ls-files -o | tr \"\\n\" \":\")"
echo $ARTIFACTS_PATHS
echo "------"
cat /home/travis/build.sh