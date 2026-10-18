#!/bin/sh
set -eu

if [ "$#" -ne 0 ]; then
    echo "Usage: seed-employees.sh (database configuration comes from environment variables)" >&2
    exit 1
fi

: "${DB_URL:?Set DB_URL for the target database}"
: "${DB_USERNAME:?Set DB_USERNAME for the target database}"
: "${DB_PASSWORD:?Set DB_PASSWORD for the target database}"

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
jar="$project_dir/backend/target/salary-management-0.0.1-SNAPSHOT.jar"
if [ ! -f "$jar" ]; then
    echo "Build the backend with ./mvnw verify before running the seed command." >&2
    exit 1
fi

java_bin=java
if [ -n "${JAVA_HOME:-}" ]; then
    java_bin="$JAVA_HOME/bin/java"
fi
exec "$java_bin" -jar "$jar" --seed-employees
