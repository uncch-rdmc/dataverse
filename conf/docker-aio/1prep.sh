#!/bin/bash

# move things necessary for integration tests into build context.
# this was based off the phoenix deployment; and is likely uglier and bulkier than necessary in a perfect world

mkdir -p testdata/doc/sphinx-guides/source/_static/util/
cp ../solr/8.11.1/schema*.xml testdata/
cp ../solr/8.11.1/solrconfig.xml testdata/
cp ../jhove/jhove.conf testdata/
cp ../jhove/jhoveConfig.xsd testdata/
cd ../../
cp -r scripts conf/docker-aio/testdata/
cp doc/sphinx-guides/source/_static/util/createsequence.sql conf/docker-aio/testdata/doc/sphinx-guides/source/_static/util/

# downloading the maven binary archive was moved to 0prep_deps.sh (payara/solr)
if [ -d maven ]; then 
    rm -R maven
fi
if [ ! -d maven ]; then
    tar xfz apache-maven-3.8.5-bin.tar.gz
    mkdir maven
    mv apache-maven-3.8.5/* maven/
fi

# echo "export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64" > maven/maven.sh 
# echo "export JAVA_HOME=/usr/lib/jvm/jre-openjdk" > maven/maven.sh
echo "export JAVA_HOME=/usr/lib/jvm/temurin-11-jdk-amd64" > maven/maven.sh 
echo "export M2_HOME=../maven" >> maven/maven.sh
echo "export MAVEN_HOME=../maven" >> maven/maven.sh
echo "export PATH=../maven/bin:${PATH}" >> maven/maven.sh
chmod 0755 maven/maven.sh

# not using dvinstall.zip for setupIT.bash; but still used in install.bash for normal ops
source maven/maven.sh && mvn clean
./scripts/installer/custom-build-number
source maven/maven.sh && mvn package
cd scripts/installer
make clean
make
mkdir -p ../../conf/docker-aio/dv/install
cp dvinstall.zip ../../conf/docker-aio/dv/install/

# ITs sometimes need files server-side
# yes, these copies could be avoided by moving the build root here. but the build 
#  context is already big enough that it seems worth avoiding.
cd ../../
cp src/test/java/edu/harvard/iq/dataverse/makedatacount/sushi_sample_logs.json conf/docker-aio/testdata/
