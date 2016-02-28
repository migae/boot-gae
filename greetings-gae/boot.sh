#!/usr/bin/env bash

if [ -f .boot-jvm-options ]; then
  OPTS=`cat .boot-jvm-options`
fi

BOOT_JVM_OPTIONS="$OPTS" boot "$@"
