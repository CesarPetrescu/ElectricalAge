# source this before running ./gradlew (port/1.21.1: Gradle and the mod both run on JDK 21)
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export GRADLE_USER_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.gradle-home"
export PATH="$JAVA_HOME/bin:$PATH"
