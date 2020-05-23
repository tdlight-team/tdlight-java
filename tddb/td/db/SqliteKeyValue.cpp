//
// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2020
//
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//
#include "td/db/SqliteKeyValue.h"

#include "td/utils/ScopeGuard.h"

namespace td {

Result<bool> SqliteKeyValue::init(string path) {
  return true;
}

Status SqliteKeyValue::init_with_connection(SqliteDb connection, string table_name) {
  return Status::OK();
}

Status SqliteKeyValue::drop() {
  return Status::OK();
}

SqliteKeyValue::SeqNo SqliteKeyValue::set(Slice key, Slice value) {
  return 0;
}

string SqliteKeyValue::get(Slice key) {
  return "";
}

SqliteKeyValue::SeqNo SqliteKeyValue::erase(Slice key) {
  return 0;
}

void SqliteKeyValue::erase_by_prefix(Slice prefix) {
}

string SqliteKeyValue::next_prefix(Slice prefix) {
  return string{};
}

}  // namespace td
