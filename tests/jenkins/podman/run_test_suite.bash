#!/bin/bash -e

DOCKERCMD="/usr/bin/podman"

usage() {
  echo "Usage: $0 -r <PR_repo> -b <PR_branch>"
  echo "The default repo and branch are IQSS/dataverse and develop, respectively."
  echo "Command-line arguments will specify a branch/repo to be merged with the IQSS develop branch."
  exit 0
}

while getopts "r:b:" o; do
  case "${o}" in
  r)
    PR_REPO=${OPTARG}
    ;;
  b)
    PR_BRANCH=${OPTARG}
    ;;
  \?)
    usage
    ;;
  esac
done

if [ ! -z "$PR_REPO" ]; then
   PR_REPO_STR="--build-arg PR_REPO=$PR_REPO"
fi

if [ ! -z "$PR_BRANCH" ]; then
   PR_BRANCH_STR="--build-arg PR_BRANCH=$PR_BRANCH"
   CONTAINER="$PR_BRANCH"
   echo "container: $CONTAINER"
else
   CONTAINER="dataverse-jenkins"
   echo "container: $CONTAINER"
fi

echo "building container"
$DOCKERCMD build -t dataverse-jenkins . $PR_REPO_STR $PR_BRANCH_STR

echo "running container"
$DOCKERCMD run --mount type=bind,source=${HOME}/.m2,target=/.m2 --name $CONTAINER dataverse-jenkins:latest

/bin/mkdir -p ./target
$DOCKERCMD cp $CONTAINER:/dataverse/target/classes ./target/
$DOCKERCMD cp $CONTAINER:/dataverse/target/coverage-it ./target/
$DOCKERCMD cp $CONTAINER:/dataverse/target/jacoco_merged.exec ./target/
$DOCKERCMD cp $CONTAINER:/dataverse/target/site ./target/
$DOCKERCMD cp $CONTAINER:/dataverse/target/surefire-reports ./target/

$DOCKERCMD rm $CONTAINER
