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

#include "third_party/zynamics/bindiff/match/function_instruction_count.h"

#include "third_party/zynamics/bindiff/differ.h"

namespace security::bindiff {

bool MatchingStepFunctionInstructionCount::FindFixedPoints(
    const FlowGraph* primary_parent, const FlowGraph* secondary_parent,
    FlowGraphs& flow_graphs_1, FlowGraphs& flow_graphs_2,
    MatchingContext& context, MatchingSteps& matching_steps,
    const MatchingStepsFlowGraph& default_steps) {
  FlowGraphIntMap flow_graphs_map_1;
  FlowGraphIntMap flow_graphs_map_2;
  GetUnmatchedFlowGraphsByInstructionCount(flow_graphs_1, flow_graphs_map_1);
  GetUnmatchedFlowGraphsByInstructionCount(flow_graphs_2, flow_graphs_map_2);
  return ::security::bindiff::FindFixedPoints(
      primary_parent, secondary_parent, flow_graphs_map_1, flow_graphs_map_2,
      &context, matching_steps, default_steps);
}

void MatchingStepFunctionInstructionCount::
    GetUnmatchedFlowGraphsByInstructionCount(const FlowGraphs& flow_graphs,
                                             FlowGraphIntMap& flow_graphs_map) {
  for (FlowGraph* graph : flow_graphs) {
    if (IsValidCandidate(graph) && boost::num_vertices(graph->GetGraph())) {
      Counts counts;
      Count(*graph, &counts);
      const uint64_t instruction_count =
          counts[Counts::kInstructionsLibrary] +
          counts[Counts::kInstructionsNonLibrary];
      flow_graphs_map.emplace(instruction_count, graph);
    }
  }
}

}  // namespace security::bindiff
