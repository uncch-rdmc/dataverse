#!/bin/bash -e

DOCKERCMD="/usr/bin/podman"
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

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
$DOCKERCMD build -t dataverse-jenkins $SCRIPT_DIR $PR_REPO_STR $PR_BRANCH_STR

echo "running container"
mkdir -p $SCRIPT_DIR/.m2
$DOCKERCMD run --mount type=bind,source=$SCRIPT_DIR/.m2,target=/.m2 --name $CONTAINER dataverse-jenkins:latest

/bin/mkdir -p ./target
$DOCKERCMD cp $CONTAINER:/dataverse/target/classes $SCRIPT_DIR/target/
$DOCKERCMD cp $CONTAINER:/dataverse/target/coverage-it $SCRIPT_DIR/target/
$DOCKERCMD cp $CONTAINER:/dataverse/target/jacoco_merged.exec $SCRIPT_DIR/target/
$DOCKERCMD cp $CONTAINER:/dataverse/target/site $SCRIPT_DIR/target/
$DOCKERCMD cp $CONTAINER:/dataverse/target/surefire-reports $SCRIPT_DIR/target/

$DOCKERCMD rm $CONTAINER
