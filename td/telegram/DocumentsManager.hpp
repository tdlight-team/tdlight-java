//
// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2020
//
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//
#pragma once

#include "td/telegram/DocumentsManager.h"

#include "td/telegram/files/FileId.hpp"
#include "td/telegram/Photo.hpp"
#include "td/telegram/Version.h"

#include "td/utils/logging.h"
#include "td/utils/tl_helpers.h"

#include "td/telegram/ConfigShared.h"

namespace td {

template <class StorerT>
void DocumentsManager::store_document(FileId file_id, StorerT &storer) const {
  //LOG(DEBUG) << "Store document " << file_id;
  auto it = documents_.find(file_id);
  if (it == documents_.end() || it->second == nullptr) {
    return;
  }
  const GeneralDocument *document = it->second.get();
  if (document == nullptr) {
      return;
  }
  store(document->file_name, storer);
  store(document->mime_type, storer);
  store(document->minithumbnail, storer);
  store(document->thumbnail, storer);
  store(file_id, storer);
}

template <class ParserT>
FileId DocumentsManager::parse_document(ParserT &parser) {
  auto document = make_unique<GeneralDocument>();

  string tmp_filename;
  parse(tmp_filename, parser);

  parse(document->mime_type, parser);

  if ( G()->shared_config().get_option_boolean("disable_document_filenames") && (
        document->mime_type.rfind("image/") == 0 ||
        document->mime_type.rfind("video/") == 0 ||
        document->mime_type.rfind("audio/") == 0)) {
      document->file_name = "0";
  } else {
      document->file_name = tmp_filename;
  }

  if (parser.version() >= static_cast<int32>(Version::SupportMinithumbnails)) {
      string tmp_minithumbnail;
      parse(tmp_minithumbnail, parser);
      if (!G()->shared_config().get_option_boolean("disable_minithumbnails")) {
          document->minithumbnail = tmp_minithumbnail;
      }
  }

  parse(document->thumbnail, parser);
  parse(document->file_id, parser);

  LOG(DEBUG) << "Parsed document " << document->file_id;
  if (parser.get_error() != nullptr || !document->file_id.is_valid()) {
    return FileId();
  }
  return on_get_document(std::move(document), false);
}

}  // namespace td
