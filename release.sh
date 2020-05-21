#!/bin/sh
set -e -x

rm -rf dist/
mkdir dist

cd ../gocd-build-github-pull-requests

mvn clean install -DskipTests -P github
cp target/gocd-scm-github-tags*.jar dist/
