# Notes

* JDK: Stanbol only compiles and runs under java <= 1.8! Make sure to set the `JAVA_HOME` environment variable to a JDK home with version <= 1.8.

* Tests: Building Stanbol with tests fails, and I don't want to dig into the reason why. Just skip the tests: `mvn clean install -Dmaven.test.skip=true`.
