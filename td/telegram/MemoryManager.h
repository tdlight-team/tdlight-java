//
// Copyright Andrea Cavalli (nospam@warp.ovh) 2020
//
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//
#pragma once

#include "td/actor/actor.h"
#include "td/actor/MultiPromise.h"
#include "td/actor/PromiseFuture.h"
#include "td/actor/Timeout.h"

#include "td/telegram/files/FileId.h"
#include "td/telegram/files/FileSourceId.h"
#include "td/telegram/FullMessageId.h"
#include "td/telegram/Photo.h"
#include "td/telegram/SecretInputMedia.h"

#include "td/utils/buffer.h"
#include "td/utils/common.h"
#include "td/utils/Hints.h"
#include "td/utils/Slice.h"
#include "td/utils/Status.h"

#include "td/telegram/td_api.h"
#include "td/telegram/telegram_api.h"

#include <memory>
#include <tuple>
#include <unordered_map>
#include <unordered_set>
#include <utility>

namespace td {

class Td;

struct MemoryStats {
  string debug;
  MemoryStats() = default;
  explicit MemoryStats(string debug) : debug(debug) {
  }
  tl_object_ptr<td_api::memoryStatistics> get_memory_statistics_object() const;
};

class MemoryManager : public Actor {
 public:
  MemoryManager(Td *td, ActorShared<> parent);

  void init();

  void get_memory_stats(bool full, Promise<MemoryStats> promise) const;

  void clean_memory(bool full, Promise<Unit> promise) const;

  void get_current_state(vector<td_api::object_ptr<td_api::Update>> &updates) const;

 private:
  void start_up() override;

  void tear_down() override;

  Td *td_;
  ActorShared<> parent_;

  bool is_inited_ = false;
};

}  // namespace td
