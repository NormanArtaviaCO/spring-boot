echo "Building"
mvn install -q -nsu -Dmaven.test.redirectTestOutputToFile=true -P '!integration' -DskipTests

#rm -rf aline-artifacts
#mkdir -p aline-artifacts
#cp -r $(git ls-files -o --directory | tr "\n" ";") aline-artifacts
