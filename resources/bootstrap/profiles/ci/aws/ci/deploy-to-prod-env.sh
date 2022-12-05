#!/usr/bin/env bash

set -eu -o pipefail

SCRIPT_DIR=$(dirname "$(realpath "$0")")
# shellcheck disable=SC1090,SC1091
. "${SCRIPT_DIR}/common-vars.sh"

# Skip updating if branch is not main
if [[ "${BRANCH:=}" != "${DEPLOYMENT_BRANCH}" ]]; then
    exit 0
fi

# Update test environment
aws elasticbeanstalk update-environment \
    --application-name "${APPLICATION_NAME}" \
    --environment-name "${PROD_ENV_NAME}" \
    --version-label "${VERSION_LABEL}"
