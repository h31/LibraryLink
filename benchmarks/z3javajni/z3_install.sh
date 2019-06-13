#!/bin/sh
mvn install:install-file -Dfile=/usr/share/java/com.microsoft.z3-4.8.4.0.jar -DgroupId=com.microsoft -DartifactId=z3 -Dversion=4.8.4 -Dpackaging=jar