//
// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2020
//
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//
#include "td/telegram/DialogDb.h"
#include "td/actor/actor.h"
#include "td/actor/SchedulerLocalStorage.h"

#include "td/db/SqliteConnectionSafe.h"
#include "td/db/SqliteDb.h"
#include "td/db/SqliteStatement.h"

#include "td/utils/common.h"
#include "td/utils/format.h"
#include "td/utils/logging.h"
#include "td/utils/misc.h"
#include "td/utils/ScopeGuard.h"
#include "td/utils/Time.h"

namespace td {
// NB: must happen inside a transaction
Status init_dialog_db(SqliteDb &db, int32 version, KeyValueSyncInterface &binlog_pmc, bool &was_created) {
  return Status::OK();
}

// NB: must happen inside a transaction
Status drop_dialog_db(SqliteDb &db, int version) {
  return Status::OK();
}

class DialogDbImpl : public DialogDbSyncInterface {
 public:
  explicit DialogDbImpl(SqliteDb db) {
    init().ensure();
  }

  Status init() {
    return Status::OK();
  }

  Status add_dialog(DialogId dialog_id, FolderId folder_id, int64 order, BufferSlice data,
                    vector<NotificationGroupKey> notification_groups) override {
    return Status::OK();
  }

  Result<BufferSlice> get_dialog(DialogId dialog_id) override {
    return Status::Error("Not found");
  }

  Result<NotificationGroupKey> get_notification_group(NotificationGroupId notification_group_id) override {
    return Status::Error("Not found");
  }

  Result<int32> get_secret_chat_count(FolderId folder_id) override {
    return 0;
  }

  Result<DialogDbGetDialogsResult> get_dialogs(FolderId folder_id, int64 order, DialogId dialog_id,
                                               int32 limit) override {
    DialogDbGetDialogsResult result;
    return std::move(result);
  }

  Result<vector<NotificationGroupKey>> get_notification_groups_by_last_notification_date(
      NotificationGroupKey notification_group_key, int32 limit) override {
    vector<NotificationGroupKey> notification_groups;
    return std::move(notification_groups);
  }

  Status begin_transaction() override {
    return Status::OK();
  }
  Status commit_transaction() override {
    return Status::OK();
  }
};

std::shared_ptr<DialogDbSyncSafeInterface> create_dialog_db_sync(
    std::shared_ptr<SqliteConnectionSafe> sqlite_connection) {
  class DialogDbSyncSafe : public DialogDbSyncSafeInterface {
   public:
    explicit DialogDbSyncSafe(std::shared_ptr<SqliteConnectionSafe> sqlite_connection)
        : lsls_db_([safe_connection = std::move(sqlite_connection)] {
          return make_unique<DialogDbImpl>(safe_connection->get().clone());
        }) {
    }
    DialogDbSyncInterface &get() override {
      return *lsls_db_.get();
    }

   private:
    LazySchedulerLocalStorage<unique_ptr<DialogDbSyncInterface>> lsls_db_;
  };
  return std::make_shared<DialogDbSyncSafe>(std::move(sqlite_connection));
}

class DialogDbAsync : public DialogDbAsyncInterface {
 public:
  DialogDbAsync(std::shared_ptr<DialogDbSyncSafeInterface> sync_db, int32 scheduler_id) {
    impl_ = create_actor_on_scheduler<Impl>("DialogDbActor", scheduler_id, std::move(sync_db));
  }

  void add_dialog(DialogId dialog_id, FolderId folder_id, int64 order, BufferSlice data,
                  vector<NotificationGroupKey> notification_groups, Promise<> promise) override {
    send_closure(impl_, &Impl::add_dialog, dialog_id, folder_id, order, std::move(data), std::move(notification_groups),
                 std::move(promise));
  }

  void get_notification_groups_by_last_notification_date(NotificationGroupKey notification_group_key, int32 limit,
                                                         Promise<vector<NotificationGroupKey>> promise) override {
    send_closure(impl_, &Impl::get_notification_groups_by_last_notification_date, notification_group_key, limit,
                 std::move(promise));
  }

  void get_notification_group(NotificationGroupId notification_group_id,
                              Promise<NotificationGroupKey> promise) override {
    send_closure(impl_, &Impl::get_notification_group, notification_group_id, std::move(promise));
  }

  void get_secret_chat_count(FolderId folder_id, Promise<int32> promise) override {
    send_closure(impl_, &Impl::get_secret_chat_count, folder_id, std::move(promise));
  }

  void get_dialog(DialogId dialog_id, Promise<BufferSlice> promise) override {
    send_closure_later(impl_, &Impl::get_dialog, dialog_id, std::move(promise));
  }

  void get_dialogs(FolderId folder_id, int64 order, DialogId dialog_id, int32 limit,
                   Promise<DialogDbGetDialogsResult> promise) override {
    send_closure_later(impl_, &Impl::get_dialogs, folder_id, order, dialog_id, limit, std::move(promise));
  }

  void close(Promise<> promise) override {
    send_closure_later(impl_, &Impl::close, std::move(promise));
  }

 private:
  class Impl : public Actor {
   public:
    explicit Impl(std::shared_ptr<DialogDbSyncSafeInterface> sync_db_safe) : sync_db_safe_(std::move(sync_db_safe)) {
    }

    void add_dialog(DialogId dialog_id, FolderId folder_id, int64 order, BufferSlice data,
                    vector<NotificationGroupKey> notification_groups, Promise<> promise) {
      add_write_query([=, promise = std::move(promise), data = std::move(data),
                       notification_groups = std::move(notification_groups)](Unit) mutable {
        this->on_write_result(std::move(promise), sync_db_->add_dialog(dialog_id, folder_id, order, std::move(data),
                                                                       std::move(notification_groups)));
      });
    }

    void on_write_result(Promise<> promise, Status status) {
      pending_write_results_.emplace_back(std::move(promise), std::move(status));
    }

    void get_notification_groups_by_last_notification_date(NotificationGroupKey notification_group_key, int32 limit,
                                                           Promise<vector<NotificationGroupKey>> promise) {
      add_read_query();
      promise.set_result(sync_db_->get_notification_groups_by_last_notification_date(notification_group_key, limit));
    }

    void get_notification_group(NotificationGroupId notification_group_id, Promise<NotificationGroupKey> promise) {
      add_read_query();
      promise.set_result(sync_db_->get_notification_group(notification_group_id));
    }

    void get_secret_chat_count(FolderId folder_id, Promise<int32> promise) {
      add_read_query();
      promise.set_result(sync_db_->get_secret_chat_count(folder_id));
    }

    void get_dialog(DialogId dialog_id, Promise<BufferSlice> promise) {
      add_read_query();
      promise.set_result(sync_db_->get_dialog(dialog_id));
    }

    void get_dialogs(FolderId folder_id, int64 order, DialogId dialog_id, int32 limit,
                     Promise<DialogDbGetDialogsResult> promise) {
      add_read_query();
      promise.set_result(sync_db_->get_dialogs(folder_id, order, dialog_id, limit));
    }

    void close(Promise<> promise) {
      do_flush();
      sync_db_safe_.reset();
      sync_db_ = nullptr;
      promise.set_value(Unit());
      stop();
    }

   private:
    std::shared_ptr<DialogDbSyncSafeInterface> sync_db_safe_;
    DialogDbSyncInterface *sync_db_ = nullptr;

    static constexpr size_t MAX_PENDING_QUERIES_COUNT{50};
    static constexpr double MAX_PENDING_QUERIES_DELAY{0.01};

    //NB: order is important, destructor of pending_writes_ will change pending_write_results_
    std::vector<std::pair<Promise<>, Status>> pending_write_results_;
    vector<Promise<>> pending_writes_;
    double wakeup_at_ = 0;

    template <class F>
    void add_write_query(F &&f) {
      pending_writes_.push_back(PromiseCreator::lambda(std::forward<F>(f), PromiseCreator::Ignore()));
      if (pending_writes_.size() > MAX_PENDING_QUERIES_COUNT) {
        do_flush();
        wakeup_at_ = 0;
      } else if (wakeup_at_ == 0) {
        wakeup_at_ = Time::now_cached() + MAX_PENDING_QUERIES_DELAY;
      }
      if (wakeup_at_ != 0) {
        set_timeout_at(wakeup_at_);
      }
    }

    void add_read_query() {
    }

    void do_flush() {
    }

    void timeout_expired() override {
    }

    void start_up() override {
      sync_db_ = &sync_db_safe_->get();
    }
  };
  ActorOwn<Impl> impl_;
};

std::shared_ptr<DialogDbAsyncInterface> create_dialog_db_async(std::shared_ptr<DialogDbSyncSafeInterface> sync_db,
                                                               int32 scheduler_id) {
  return std::make_shared<DialogDbAsync>(std::move(sync_db), scheduler_id);
}

}  // namespace td
