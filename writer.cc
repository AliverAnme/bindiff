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

#include "third_party/zynamics/bindiff/writer.h"

#include <memory>

#include "third_party/absl/status/status.h"
#include "third_party/zynamics/binexport/util/status_macros.h"

namespace security::bindiff {

absl::Status ChainWriter::Write(const CallGraph& call_graph1,
                                const CallGraph& call_graph2,
                                const FlowGraphs& flow_graphs1,
                                const FlowGraphs& flow_graphs2,
                                const FixedPoints& fixed_points) {
  for (auto& writer : writers_) {
    NA_RETURN_IF_ERROR(writer->Write(call_graph1, call_graph2, flow_graphs1,
                                     flow_graphs2, fixed_points));
  }
  return absl::OkStatus();
}

void ChainWriter::Add(std::unique_ptr<Writer> writer) {
  writers_.push_back(std::move(writer));
}

bool ChainWriter::empty() const { return writers_.empty(); }

}  // namespace security::bindiff
