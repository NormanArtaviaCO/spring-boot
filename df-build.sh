echo "Build is done by now. Copying dependencies..."
mvn dependency:copy-dependencies
echo "Cleaning up existing zip if any..."
rm -f libs.zip
rm -f build.zip
echo "Zipping artifacts begin..."
zip libs.zip $(git ls-files -o | grep -e target/dependency/.*jar)
zip build.zip $(git ls-files -o | grep -e target/classes/.*class)
echo "Zipping artifacts DONE!!!"
# Modified by Insights Service at 2016-01-05 13:05:17.358054
