#!/usr/bin/env bash
# This script prepares the environment for integration tests. It should be run before any test is executed.

# Check if Java is installed
if command -v java &> /dev/null; then
    echo "Java is installed: $(java -version 2>&1 | head -n 1)"
    echo "JAVA_HOME=$JAVA_HOME"
else
    echo "Java is not installed. Please install Java to run the tests."
    exit 1
fi

# Check if Gradle is installed
if command -v gradle &> /dev/null; then
    echo "Gradle is installed: $(gradle -v | grep 'Gradle' | awk '{print $2}')"
else
    echo "Gradle is not installed. Please install Gradle to run the tests."
    exit 1
fi

# Check or install Nextflow
if command -v nextflow &> /dev/null; then
    echo "Nextflow is installed: $(nextflow -v | head -n 1)"
else
    echo "Nextflow is not installed. Installing Nextflow..."
    curl -s https://get.nextflow.io | bash
    mv nextflow /usr/local/bin/
fi


# Install plugin
./gradlew assemble