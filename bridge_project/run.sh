#!/bin/sh
DATETIME=$(date +%Y%m%d_%H%M%S)
task run 2>&1 | tee /bridge_project/logs/task_run_${DATETIME}.log