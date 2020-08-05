//
// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2020
//
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//
#pragma once

#include "td/utils/common.h"

#include <limits>
#include <map>
#include <tuple>
#include <shared_mutex>

namespace td {

template <class ValueT>
class Enumerator {
 public:
  using Key = int32;

  std::map<ValueT, int32> get_map() const {
    return map_;
  }

  void lock_access_mutex() const {
    access_mutex.lock();
  }

  void unlock_access_mutex() const {
    access_mutex.unlock();
  }

  /**
   *
   * @return true if the key is new
   */
  Key next() {
    auto id = next_id++;

    return id;
  }

  void erase_unsafe(Key key_y) {
    auto find_val = arr_.find(key_y);
    if (find_val != arr_.end()) {
      // Erase this
      map_.erase(find_val->second);
      // TODO: Not sure about erasing this, instead
      arr_.erase(key_y);
    }
  }

  Key add(ValueT v) {
    std::lock_guard<std::shared_timed_mutex> writerLock(access_mutex);

    return add_internal(v);
  }

  Key add_internal(ValueT v) {
    auto id = next();
    bool was_inserted;
    decltype(map_.begin()) it;
    std::tie(it, was_inserted) = map_.emplace(std::move(v), id);
    if (was_inserted) {
      arr_[id] = it->first;
    }
    return it->second;
  }

  const ValueT &get(Key key) const {
    std::shared_lock<std::shared_timed_mutex> readerLock(access_mutex);

    return get_internal(key);
  }

  const ValueT &get_internal(Key key) const {
    return arr_.at(key);
  }

  std::pair<Key,ValueT> add_and_get(ValueT v) {
    std::lock_guard<std::shared_timed_mutex> writerLock(access_mutex);

    auto remote_key = add_internal(v);
    auto &stored_info = get_internal(remote_key);
    return std::make_pair(remote_key, stored_info);
  }

 private:
  mutable int32 next_id = 1;
  std::map<ValueT, int32> map_;
  std::unordered_map<int32, ValueT> arr_;
  mutable std::shared_timed_mutex access_mutex;
};

}  // namespace td
