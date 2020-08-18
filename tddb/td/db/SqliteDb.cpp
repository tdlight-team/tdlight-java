//
// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2020
//
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//
#include "td/db/SqliteDb.h"

#include "td/utils/common.h"
#include "td/utils/format.h"
#include "td/utils/port/path.h"
#include "td/utils/port/Stat.h"
#include "td/utils/Status.h"
#include "td/utils/StringBuilder.h"
#include "td/utils/Timer.h"

#include "sqlite/sqlite3.h"

namespace td {

namespace {

}  // namespace

SqliteDb::~SqliteDb() = default;

Status SqliteDb::init(CSlice path, bool *was_created) {
  // If database does not exist, delete all other files which may left
  // from older database

  if (path == ":memory:") {
      if (was_created != nullptr) {
        *was_created = false;
      }
    } else {
      bool is_db_exists = stat(path).is_ok();

      if (!is_db_exists) {
          TRY_STATUS(destroy(path));
      }

      if (was_created != nullptr) {
          *was_created = !is_db_exists;
      }
  }

  sqlite3 *db;
  CHECK(sqlite3_threadsafe() != 0);
  int rc = sqlite3_open_v2(path.c_str(), &db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE,
                           nullptr);
  if (rc != SQLITE_OK) {
    auto res = Status::Error(PSLICE() << "Failed to open database: " << detail::RawSqliteDb::last_error(db, path));
    sqlite3_close(db);
    return res;
  }
  sqlite3_busy_timeout(db, 1000 * 5 /* 5 seconds */);
  raw_ = std::make_shared<detail::RawSqliteDb>(db, path.str());
  return Status::OK();
}

static void trace_callback(void *ptr, const char *query) {
  LOG(ERROR) << query;
}
static int trace_v2_callback(unsigned code, void *ctx, void *p_raw, void *x_raw) {
  CHECK(code == SQLITE_TRACE_STMT);
  auto x = static_cast<const char *>(x_raw);
  if (x[0] == '-' && x[1] == '-') {
    trace_callback(ctx, x);
  } else {
    trace_callback(ctx, sqlite3_expanded_sql(static_cast<sqlite3_stmt *>(p_raw)));
  }

  return 0;
}
void SqliteDb::trace(bool flag) {
  sqlite3_trace_v2(raw_->db(), SQLITE_TRACE_STMT, flag ? trace_v2_callback : nullptr, nullptr);
}

Status SqliteDb::exec(CSlice cmd) {
  CHECK(!empty());
  char *msg;
  if (enable_logging_) {
    VLOG(sqlite) << "Start exec " << tag("query", cmd) << tag("database", raw_->db());
  }
  auto rc = sqlite3_exec(raw_->db(), cmd.c_str(), nullptr, nullptr, &msg);
  if (enable_logging_) {
    VLOG(sqlite) << "Finish exec " << tag("query", cmd) << tag("database", raw_->db());
  }
  if (rc != SQLITE_OK) {
    CHECK(msg != nullptr);
    return Status::Error(PSLICE() << tag("query", cmd) << " to database \"" << raw_->path() << "\" failed: " << msg);
  }
  CHECK(msg == nullptr);
  return Status::OK();
}

Result<bool> SqliteDb::has_table(Slice table) {
  TRY_RESULT(stmt, get_statement(PSLICE() << "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='" << table
                                          << "'"));
  TRY_STATUS(stmt.step());
  CHECK(stmt.has_row());
  auto cnt = stmt.view_int32(0);
  return cnt == 1;
}
Result<string> SqliteDb::get_pragma(Slice name) {
  TRY_RESULT(stmt, get_statement(PSLICE() << "PRAGMA " << name));
  TRY_STATUS(stmt.step());
  CHECK(stmt.has_row());
  auto res = stmt.view_blob(0).str();
  TRY_STATUS(stmt.step());
  CHECK(!stmt.can_step());
  return std::move(res);
}
Result<string> SqliteDb::get_pragma_string(Slice name) {
  TRY_RESULT(stmt, get_statement(PSLICE() << "PRAGMA " << name));
  TRY_STATUS(stmt.step());
  CHECK(stmt.has_row());
  auto res = stmt.view_string(0).str();
  TRY_STATUS(stmt.step());
  CHECK(!stmt.can_step());
  return std::move(res);
}

Result<int32> SqliteDb::user_version() {
  TRY_RESULT(get_version_stmt, get_statement("PRAGMA user_version"));
  TRY_STATUS(get_version_stmt.step());
  if (!get_version_stmt.has_row()) {
    return Status::Error(PSLICE() << "PRAGMA user_version failed for database \"" << raw_->path() << '"');
  }
  return get_version_stmt.view_int32(0);
}

Status SqliteDb::set_user_version(int32 version) {
  return exec(PSLICE() << "PRAGMA user_version = " << version);
}

Status SqliteDb::begin_transaction() {
  if (raw_->on_begin()) {
    return exec("BEGIN");
  }
  return Status::OK();
}

Status SqliteDb::commit_transaction() {
  TRY_RESULT(need_commit, raw_->on_commit());
  if (need_commit) {
    return exec("COMMIT");
  }
  return Status::OK();
}

Status SqliteDb::check_encryption() {
  return Status::OK();
}

Result<SqliteDb> SqliteDb::open_with_key(CSlice path, const DbKey &db_key, optional<int32> cipher_version) {
  auto res = do_open_with_key(path, db_key, cipher_version ? cipher_version.value() : 0);
  if (res.is_error() && !cipher_version) {
    return do_open_with_key(path, db_key, 3);
  }
  return res;
}

Result<SqliteDb> SqliteDb::do_open_with_key(CSlice path, const DbKey &db_key, int32 cipher_version) {
  SqliteDb db;
  TRY_STATUS(db.init(path));
  return std::move(db);
}

void SqliteDb::set_cipher_version(int32 cipher_version) {
}
optional<int32> SqliteDb::get_cipher_version() const {
  return optional<int32>{3};
}
Result<SqliteDb> SqliteDb::change_key(CSlice path, const DbKey &new_db_key, const DbKey &old_db_key) {
  SqliteDb db;
  TRY_STATUS(db.init(path));

  return std::move(db);
}

Status SqliteDb::destroy(Slice path) {
  return detail::RawSqliteDb::destroy(path);
}

Result<SqliteStatement> SqliteDb::get_statement(CSlice statement) {
  sqlite3_stmt *stmt = nullptr;
  auto rc = sqlite3_prepare_v2(get_native(), statement.c_str(), static_cast<int>(statement.size()) + 1, &stmt, nullptr);
  if (rc != SQLITE_OK) {
    return Status::Error(PSLICE() << "Failed to prepare SQLite " << tag("statement", statement) << raw_->last_error());
  }
  LOG_CHECK(stmt != nullptr) << statement;
  return SqliteStatement(stmt, raw_);
}

}  // namespace td
