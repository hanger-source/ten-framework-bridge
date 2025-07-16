#!/bin/sh

link_dir_recursive() {
  local src_base="$1"
  local dst_base="$2"
  shift 2  # 移除前两个参数，剩下的都是要处理的子目录

  for sub in "$@"; do
    local src="$src_base/$sub"
    local dst="$dst_base/$sub"
    if [ -d "$src" ]; then
      # 递归处理该子目录
      _link_dir "$src" "$dst"
    fi
  done
}

# 递归处理单个目录
_link_dir() {
  local src_dir="$1"
  local dst_dir="$2"
  mkdir -p "$dst_dir"
  for item in "$src_dir"/*; do
    name=$(basename "$item")
    if [ -d "$item" ]; then
      if [ ! -e "$dst_dir/$name" ]; then
        ln -s "$item" "$dst_dir/$name"
      else
        if [ -d "$dst_dir/$name" ] && [ ! -L "$dst_dir/$name" ]; then
          _link_dir "$item" "$dst_dir/$name"
        fi
      fi
    fi
  done
}

src_base="/bridge_project"
dst_base="/app/agents"

link_dir_recursive "$src_base" "$dst_base" examples ten_packages

task use AGENT=agents/examples/bridge