// Copyright 2011-2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef START_UI_H_
#define START_UI_H_

#include <string>
#include <vector>

#include "third_party/absl/status/status.h"
#include "third_party/zynamics/binexport/util/types.h"

namespace security::bindiff {

struct StartUiOptions {
  StartUiOptions& set_java_binary(std::string value) {
    java_binary = std::move(value);
    return *this;
  }

  template <typename StringsT>
  StartUiOptions& set_java_vm_options(const StringsT& value) {
    java_vm_options.assign(value.begin(), value.end());
    return *this;
  }

  StartUiOptions& set_max_heap_size_mb(int value) {
    max_heap_size_mb = value;
    return *this;
  }

  StartUiOptions& set_bindiff_dir(std::string value) {
    bindiff_dir = std::move(value);
    return *this;
  }

  std::string java_binary;
  std::vector<std::string> java_vm_options;
  int max_heap_size_mb = -1;  // Default means 75% of physical memory
  std::string bindiff_dir;
};

// Launches the BinDiff Java UI and immediately returns. Extra command-line
// arguments for the UI can be specified in args, and configuration settings in
// options.
absl::Status StartUiWithOptions(const std::vector<std::string>& extra_args,
                                const StartUiOptions& options);

}  // namespace security::bindiff

#endif  // START_UI_H_
