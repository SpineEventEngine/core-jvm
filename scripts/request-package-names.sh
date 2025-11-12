#!/bin/bash

#
# Copyright 2024, TeamDev. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# https://www.apache.org/licenses/LICENSE-2.0
#
# Redistribution and use in source and/or binary forms, with or without
# modification, must retain the above copyright notice and the following
# disclaimer.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
# "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
# LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
# A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
# LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
# THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#

# Requests GitHub to retrieve package names published from a specific repository
# in the Apache Maven registry on GitHub Packages.
#
# Parameters:
#   1. The GitHub access token with the "read:packages" permission.
#   2. The name of the repository from which the packages were published.
#   3. The organization that owns the packages.
#   4. The file path where the package names should be written.
#
# The output is a file containing a JSON array of package names. For example:
# `["io.spine.config.package1","io.spine.config.package2"]`.
#

if [ "$#" -ne 4 ]; then
    echo "Usage: request-package-names.sh <GitHub token> <repo name> <repo owner name> <output file>"
    exit 1
fi

token=$1
repoName=$2
orgName=$3
countOnThisPage=0
page=1
packages=''

function requestPackages() {
    url="https://api.github.com/orgs/$orgName/packages?package_type=maven&per_page=100&page=$page"
    response=$(curl -s -H "Authorization: Bearer $token" "$url")
    countOnThisPage=$(echo "$response" | jq -c length)
    packagesOnThisPage=$(echo "$response" | \
        jq -c --arg repo "$repoName" '.[] | select(.repository.name == $repo) | .name')
    if [ "$?" -ne 0 ]; then
        echo "An unexpected response was received and could not be parsed." \
             "Ensure that parameters contains the correct data."
        exit 1
    fi
    packages="$packages $packagesOnThisPage"
}

while true; do
    requestPackages
    (( page++ ))
    if [ "$countOnThisPage" -eq 0 ]; then
        break
    fi
done

json=$(echo $packages | sed 's/ /,/g' | sed 's/^/[/' | sed 's/$/]/')
echo "$json" >> "$4"
