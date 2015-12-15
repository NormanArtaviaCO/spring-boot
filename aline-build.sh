echo "Building"
mvn install -q -nsu -DskipTests=true -Dmaven.test.redirectTestOutputToFile=true -P '!integration'

#rm -rf aline-artifacts
#mkdir -p aline-artifacts
#cp -r $(git ls-files -o --directory | tr "\n" ";") aline-artifacts
