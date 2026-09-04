# source this before running ./gradlew
export JAVA_HOME=/opt/jdks/jdk-25.0.4.1+1
export GRADLE_USER_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.gradle-home"
export PATH="$JAVA_HOME/bin:$PATH"
