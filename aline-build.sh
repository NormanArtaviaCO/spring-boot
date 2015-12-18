echo "Building..."
mvn install -q -nsu -DskipTests -Dmaven.test.redirectTestOutputToFile=true -P '!integration'
echo "Copying artifacts..."
rm -r aline-artifacts.zip
zip aline-artifacts.zip $(git ls-files -o)
echo "Build Done!"
mvn -e jacoco:report coveralls:report
echo "Testing Done!"


