# source this before running ./gradlew (port/1.21.1: Gradle and the mod both run on JDK 21).
# A JAVA_HOME or GRADLE_USER_HOME already set (CI, setup-java) is left alone; the defaults are
# this box's JDK 21 and a Gradle home inside the checkout, so the caches stay with the repo.
if [ -z "${JAVA_HOME:-}" ] && [ -d /usr/lib/jvm/java-21-openjdk-amd64 ]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
fi
if [ -z "${GRADLE_USER_HOME:-}" ]; then
    export GRADLE_USER_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.gradle-home"
fi
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"
