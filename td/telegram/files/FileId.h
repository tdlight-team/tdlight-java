//
// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2020
//
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//
#pragma once

#include "td/utils/common.h"
#include "td/utils/StringBuilder.h"

#include <ctime>
#include <functional>
#include <type_traits>

namespace td {

class FileId {
  int32 id = 0;
  int32 remote_id = 0;
  int64 time_ = INT64_MAX;

 public:
  FileId() = default;

  FileId(int32 file_id, int32 remote_id) : id(file_id), remote_id(remote_id) {
  }
  template <class T1, class T2, typename = std::enable_if_t<std::is_convertible<T1, int32>::value>,
            typename = std::enable_if_t<std::is_convertible<T2, int32>::value>>
  FileId(T1 file_id, T2 remote_id) = delete;

  bool empty() const {
    return id <= 0;
  }
  bool is_valid() const {
    return id > 0;
  }

  int32 get() const {
    return id;
  }

  void set_time() {
    time_ = std::time(nullptr);
  }

  int64 get_time() const {
    return time_;
  }

  void reset_time() {
    time_ = INT64_MAX;
  }

  int32 get_remote() const {
    return remote_id;
  }

  bool operator<(const FileId &other) const {
    return id < other.id;
  }

  bool operator==(const FileId &other) const {
    return id == other.id;
  }

  bool operator!=(const FileId &other) const {
    return id != other.id;
  }
};

struct FileIdHash {
  std::size_t operator()(FileId file_id) const {
    return std::hash<int32>()(file_id.get());
  }
};

inline StringBuilder &operator<<(StringBuilder &string_builder, FileId file_id) {
  return string_builder << file_id.get() << "(" << file_id.get_remote() << ")";
}

}  // namespace td
