#!/usr/bin/env bash

set -x
set -e

echoerr() {
  echo "$@" 1>&2;
}

assert() {
  $@ || (echo "FAILED: $@"; exit 1)
}

contains() {
  ls $@
}

for word in "${@}"
do
     assert contains "${word}"
done