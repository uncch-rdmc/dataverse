FROM docker.io/geerlingguy/docker-rockylinux8-ansible:latest

# component arguments. maybe we'll make these override-able.
ARG DEFAULT_CONFIG=https://raw.githubusercontent.com/IQSS/dataverse/develop/scripts/installer/default.config
ARG JACOCO_ZIP=https://repo1.maven.org/maven2/org/jacoco/jacoco/0.8.6/jacoco-0.8.6.zip
ARG MAVEN_URL=https://www-us.apache.org/dist/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz
ARG PAYARA_URL=https://s3-eu-west-1.amazonaws.com/payara.fish/Payara+Downloads/5.2020.6/payara-5.2020.6.zip
ARG SOLR_URL=https://archive.apache.org/dist/lucene/solr/8.8.1/solr-8.8.1.tgz

# PR repo and branch arguments. May be passed as Docker --build-args
ARG PR_REPO
ARG PR_BRANCH

# OS dependencies
RUN dnf -y update
RUN dnf install -y https://download.postgresql.org/pub/repos/yum/reporpms/EL-8-x86_64/pgdg-redhat-repo-latest.noarch.rpm
RUN dnf install -y epel-release bsdtar curl git java-11-openjdk-devel lsof make python36 python3-psycopg2 sudo unzip zip
RUN dnf -qy module disable postgresql && dnf -qy install postgresql13-server
# jq is only available in EPEL, hence the second call
RUN dnf install -y jq

# initialize postgres
RUN sudo -u postgres /usr/pgsql-13/bin/initdb /var/lib/pgsql/data

# install solr
RUN curl -s -O ${SOLR_URL}
RUN mkdir /usr/local/solr && bsdtar --strip-components=1 -C /usr/local/solr -xf /solr-*
RUN echo "solr soft nproc 65000" >> /etc/security/limits.conf && \
    echo "solr hard nproc 65000" >> /etc/security/limits.conf && \
    echo "solr soft nofile 65000" >> /etc/security/limits.conf && \
    echo "solr hard nofile 65000" >> /etc/security/limits.conf
RUN cp -r /usr/local/solr/server/solr/configsets/_default /usr/local/solr/server/solr/collection1 && \
    echo "name=collection1" > /usr/local/solr/server/solr/collection1/core.properties && \
    sed -i 's/solr.jetty.request.header.size"\ default="8192"/solr.jetty.request.header.size"\ default="102400"/' /usr/local/solr/server/etc/jetty.xml
RUN useradd solr && chown -R solr:solr /usr/local/solr

# install payara
RUN curl -s -O ${PAYARA_URL}
RUN mkdir /usr/local/payara5 && bsdtar --strip-components=1 -C /usr/local/payara5 -xf /payara-*
RUN useradd payara && chown -R payara:payara /usr/local/payara5

# download jacoco
RUN curl -s -L -O ${JACOCO_ZIP} && unzip -q jacoco-*.zip -d /jacoco
RUN cp /jacoco/lib/jacocoagent.jar /usr/local/payara5/glassfish/lib/

# install maven
RUN curl -s -L -O ${MAVEN_URL}
RUN mkdir /opt/maven && bsdtar --strip-components=1 -C /opt/maven -xf /apache-maven-3.*
RUN echo "export JAVA_HOME=/usr/lib/jvm/jre-openjdk" > /etc/profile.d/maven.sh && \
    echo "export M2_HOME=/opt/maven" >> /etc/profile.d/maven.sh && \
    echo "export MAVEN_HOME=/opt/maven" >> /etc/profile.d/maven.sh && \
    echo "export PATH=/opt/maven/bin:${PATH}" >> /etc/profile.d/maven.sh

# don't expose ports lest we collide with other containers, but in case you want to:
# payara
#EXPOSE 8080
#EXPOSE 4848
# solr
#EXPOSE 8983
# postgres
#EXPOSE 5432

# keeping the symlink on the off chance that something else is still assuming /usr/local/glassfish4
# do we need this?
#RUN ln -s /usr/local/payara5 /usr/local/glassfish4

COPY entrypoint.bash /

# set PR repo and branch if passed, otherwise populate with IQSS
ENV PR_REPO_STR="${PR_REPO:-https://github.com/IQSS/dataverse.git}"
ENV PR_BRANCH_STR="${PR_BRANCH:-develop}"

RUN echo "PR_REPO_STR: ${PR_REPO_STR}"
RUN echo "PR_BRANCH_STR: ${PR_BRANCH_STR}"

CMD ["/bin/bash","-c","/entrypoint.bash -r ${PR_REPO_STR} -b ${PR_BRANCH_STR}"]
