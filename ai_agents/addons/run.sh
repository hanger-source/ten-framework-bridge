#!/bin/sh
DATETIME=$(date +%Y%m%d_%H%M%S)

# 启动 task run，并保存其 PID
task run 2>&1 | tee /app/addons/logs/task_run_${DATETIME}.log &
pid=$!

# 捕获 Ctrl+C（SIGINT），转发给 task run
trap "kill $pid 2>/dev/null" INT

# 等待 task run 结束
wait $pid