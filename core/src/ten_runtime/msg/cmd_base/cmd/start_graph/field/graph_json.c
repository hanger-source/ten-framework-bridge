//
// Copyright © 2025 Agora
// This file is part of TEN Framework, an open source project.
// Licensed under the Apache License, Version 2.0, with certain conditions.
// Refer to the "LICENSE" file in the root directory for more information.
//
#include "include_internal/ten_runtime/common/constant_str.h"
#include "include_internal/ten_runtime/extension/extension_info/extension_info.h"
#include "include_internal/ten_runtime/msg/cmd_base/cmd/cmd.h"
#include "include_internal/ten_runtime/msg/cmd_base/cmd/start_graph/cmd.h"
#include "include_internal/ten_runtime/msg/loop_fields.h"
#include "include_internal/ten_runtime/msg/msg.h"
#include "ten_runtime/msg/cmd/start_graph/cmd.h"
#include "ten_utils/lib/string.h"
#include "ten_utils/macro/check.h"
#include "ten_utils/macro/mark.h"
#include "ten_utils/value/value_get.h"

void ten_cmd_start_graph_copy_graph_json(
    ten_msg_t *self, ten_msg_t *src,
    TEN_UNUSED ten_list_t *excluded_field_ids) {
  TEN_ASSERT(self, "Should not happen.");
  TEN_ASSERT(src, "Should not happen.");
  TEN_ASSERT(ten_raw_cmd_check_integrity((ten_cmd_t *)src),
             "Should not happen.");
  TEN_ASSERT(ten_raw_msg_get_type(src) == TEN_MSG_TYPE_CMD_START_GRAPH,
             "Should not happen.");

  ten_string_copy(
      ten_value_peek_string(&((ten_cmd_start_graph_t *)self)->graph_json),
      ten_value_peek_string(&((ten_cmd_start_graph_t *)src)->graph_json));
}

bool ten_cmd_start_graph_process_graph_json(
    ten_msg_t *self, ten_raw_msg_process_one_field_func_t cb, void *user_data,
    ten_error_t *err) {
  TEN_ASSERT(self, "Should not happen.");
  TEN_ASSERT(ten_raw_msg_check_integrity(self), "Should not happen.");

  ten_msg_field_process_data_t graph_json_field;
  ten_msg_field_process_data_init(&graph_json_field, TEN_STR_GRAPH_JSON,
                                  &((ten_cmd_start_graph_t *)self)->graph_json,
                                  false);

  return cb(self, &graph_json_field, user_data, err);
}
