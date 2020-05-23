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

namespace td {

template <class ValueT>
class Enumerator {
 public:
  using Key = int32;

  std::map<ValueT, int32> get_map() const {
    return map_;
  }

  std::pair<Key, bool> next() {
    if (!empty_id_.empty()) {
      auto res = empty_id_.back();
      empty_id_.pop_back();
      return std::make_pair(res, true);
    }

    return std::make_pair((Key) (arr_.size() + 1), false);
  }

  void erase(Key key_y) {
    empty_id_.push_back(key_y);
  }

  Key add(ValueT v) {
    auto next_id = next();
    bool was_inserted;
    decltype(map_.begin()) it;
    std::tie(it, was_inserted) = map_.emplace(std::move(v), next_id.first);
    if (was_inserted && next_id.second) {
      arr_[next_id.first - 1] = &it->first;
    } else if (was_inserted) {
      arr_.push_back(&it->first);
    } else if (next_id.second) {
      empty_id_.push_back(next_id.first);
    }
    return it->second;
  }

  const ValueT &get(Key key) const {
    auto pos = static_cast<Key>(key - 1);
    return *arr_[pos];
  }

 private:
  std::vector<Key> empty_id_;
  std::map<ValueT, int32> map_;
  std::vector<const ValueT *> arr_;
};

}  // namespace td
