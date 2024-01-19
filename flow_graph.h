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

#ifndef FLOW_GRAPH_H_
#define FLOW_GRAPH_H_

#include <boost/graph/compressed_sparse_row_graph.hpp>  // NOLINT
#include <cstdint>
#include <limits>
#include <string>
#include <vector>

#include "third_party/absl/status/status.h"
#include "third_party/zynamics/bindiff/call_graph.h"
#include "third_party/zynamics/bindiff/graph_util.h"
#include "third_party/zynamics/bindiff/instruction.h"
#include "third_party/zynamics/binexport/binexport2.pb.h"
#include "third_party/zynamics/binexport/util/types.h"

namespace security::bindiff {

class FixedPoint;
class BasicBlockFixedPoint;

bool IsSorted(const std::vector<Address>& addresses);

class FlowGraph {
 public:
  struct VertexInfo {
    uint64_t prime_ = 0;  // Prime signature
    uint32_t flags_ = 0;
    uint32_t string_hash_ = 0;       // String reference hash
    uint32_t basic_block_hash_ = 0;  // Basic block binary hash
    uint32_t instruction_start_ =
        std::numeric_limits<uint32_t>::max();  // Start index of instructions in
                                               // instruction vector
    uint32_t call_target_start_ =
        std::numeric_limits<uint32_t>::max();  // Start index of calltargets in
                                               // calltarget vector
    BasicBlockFixedPoint* fixed_point_ = nullptr;  // Basic block match (if any)
    uint16_t bfs_top_down_ = 0;                    // BFS level top down
    uint16_t bfs_bottom_up_ = 0;                   // BFS level bottom up
  };

  struct EdgeInfo {
    double md_index_top_down_ = 0;
    double md_index_bottom_up_ = 0;
    uint8_t flags_ = 0;  // Unconditional, true, false, switch
  };

  using Graph = boost::compressed_sparse_row_graph<
      boost::bidirectionalS,
      VertexInfo,          // Vertex properties
      EdgeInfo,            // Edge properties
      boost::no_property,  // Graph properties
      uint32_t,            // Index type for vertices (see b/35456354)
      uint32_t             // Index type for edges
      >;

  using Vertex = boost::graph_traits<Graph>::vertex_descriptor;
  using VertexIterator = boost::graph_traits<Graph>::vertex_iterator;
  using Edge = boost::graph_traits<Graph>::edge_descriptor;
  using EdgeIterator = boost::graph_traits<Graph>::edge_iterator;
  using OutEdgeIterator = boost::graph_traits<Graph>::out_edge_iterator;
  using InEdgeIterator = boost::graph_traits<Graph>::in_edge_iterator;
  // Basic block level, inner basic block level
  using Level = std::pair<uint16_t, uint16_t>;
  using CallTargets = std::vector<Address>;

  enum {
    EDGE_UNCONDITIONAL = 1 << 0,
    EDGE_TRUE = 1 << 1,
    EDGE_FALSE = 1 << 2,
    EDGE_SWITCH = 1 << 3,
    EDGE_DOMINATED = 1 << 4,
    VERTEX_LOOPENTRY = 1 << 31
    // The lower bits are used to indicate matching steps.
  };

  FlowGraph();
  FlowGraph(CallGraph* call_graph, Address entry_point);
  virtual ~FlowGraph();

  // Read and initialize flow graph from given proto message. The instruction
  // cache should be shared between flow graphs and stores mnemonic strings and
  // operand trees.
  absl::Status Read(const BinExport2& proto,
                    const BinExport2::FlowGraph& proto_flow_graph,
                    CallGraph* call_graph,
                    Instruction::Cache* instruction_cache);

  // O(logn) binary search for the vertex (==basic block) starting at "address".
  Vertex GetVertex(Address address) const;

  // O(1) return cached MD index calculated from top down or bottom up BFS.
  inline double GetMdIndex() const { return md_index_; }
  inline double GetMdIndexInverted() const { return md_index_inverted_; }
  void SetMdIndex(double index);
  void SetMdIndexInverted(double index);

  // Return MD index for a given vertex. Inverted MD index uses the bottom up
  // breadth first node level for calculating MD indices. This method is quite
  // expensive: it iterates its in and out edges and calculates and sums the
  // edge MD indices.
  double GetMdIndex(Vertex vertex) const;
  double GetMdIndexInverted(Vertex vertex) const;

  // O(1) return cached MD index for the edge.
  double GetMdIndex(const Edge& edge) const;
  double GetMdIndexInverted(const Edge& edge) const;

  // O(|V| + |E|) two breadth first searches over the graph. Stores resulting
  // BFS indices in vertices.
  void CalculateTopology();

  // Access boost graph implementation.
  Graph& GetGraph();
  const Graph& GetGraph() const;
  size_t GetBasicBlockCount() const;

  // Returns the number of loops in the graph. A loop is defined as a back edge
  // by Lengauer Tarjan (http://goo.gl/GEIMB).
  uint16_t GetLoopCount() const;

  // The function's entry point address.
  // This actually showed up in profiles as a significant (16.9%) chunk.
  inline Address GetEntryPointAddress() const { return entry_point_address_; }

  // Calculates the "level" for every call in the flow graph. Level is defined
  // as the shortest path from function entry point to the call. Multiple calls
  // within the same basic block are ordered by code flow through the block.
  void CalculateCallLevels();

  // Return the level for the call at "address".
  // This is logarithmic in the number of calls in the function plus linear in
  // the number of calls at the same level.
  Level GetLevelForCallAddress(Address address) const;

  // O(1) return the function matched to us if any. nullptr for no match.
  // I've put the code in the header because it actually showed up in profiles.
  inline FixedPoint* GetFixedPoint() const { return fixed_point_; }
  void SetFixedPoint(FixedPoint* fixed_point);

  // Returns the basic block matched to the one at vertex. nullptr for none.
  BasicBlockFixedPoint* GetFixedPoint(Vertex vertex) const;
  void SetFixedPoint(Vertex vertex, BasicBlockFixedPoint* fixed_point);

  // O(1) get address for basic block.
  Address GetAddress(Vertex vertex) const;

  // Return BFS index for vertex. The inverted BFS iteration starts at a virtual
  // node that connects all basic blocks without any out edges. The regular, top
  // down iteration simply starts at the uniquely defined function entry point
  // basic block.
  size_t GetTopologyLevel(Vertex vertex) const;
  size_t GetTopologyLevelInverted(Vertex vertex) const;

  // Return associated call graph.
  CallGraph* GetCallGraph() const;
  void SetCallGraph(CallGraph* graph);
  CallGraph::Vertex GetCallGraphVertex() const;

  // Return all call targets for this basic block, in order of appearance.
  std::pair<CallTargets::const_iterator, CallTargets::const_iterator>
  GetCallTargets(Vertex vertex) const;
  int GetCallCount(Vertex vertex) const;

  std::pair<Instructions::const_iterator, Instructions::const_iterator>
  GetInstructions(Vertex vertex) const;
  int GetInstructionCount(Vertex vertex) const;
  int GetInstructionCount() const;

  // Reset all fixed point information, i.e. remove function match and all
  // fixed point matches.
  void ResetMatches();

  // Am I a IsLibrary function? O(logn), accesses call graph.
  bool IsLibrary() const;

  // O(1) is this vertex a loop entry point? This is defined as being the
  // target of a back edge in Lengauer Tarjan.
  bool IsLoopEntry(Vertex vertex) const;

  // O(1) a graph is considered trivial if it consists of a single basic block.
  bool IsTrivial() const;

  // O(1) edge source == edge target?
  bool IsCircular(const Edge& edge) const;

  bool HasRealName() const;

  // Return the flags for a basic block or edge. See enum declaration above.
  uint32_t GetFlags(Vertex vertex) const;
  void SetFlags(Vertex vertex, uint32_t flags);
  uint8_t GetFlags(const Edge& edge) const;
  void SetFlags(const Edge& edge, uint8_t flags);

  // Get the string reference hash for a vertex or the whole function.
  uint32_t GetStringReferences(Vertex vertex) const;
  uint32_t GetStringReferences() const;

  // Return the instruction prime product for a basic block or the whole
  // function the prime is defined in binexport.proto.
  uint64_t GetPrime(Vertex vertex) const;
  uint64_t GetPrime() const;

  // Return the string reference hash for a basic block or the whole
  // function.
  uint32_t GetHash(Vertex vertex) const;
  uint32_t GetHash() const;

  // O(1) The function accesses a call graph vertex in order to retrieve the
  // name.
  const std::string& GetName() const;
  const std::string& GetDemangledName() const;
  // Returns the demangled name if available, raw name otherwise.
  const std::string& GetGoodName() const;

 protected:
  using AddressToLevelMap = std::vector<std::pair<Address, Level>>;

  void Init();
  void MarkLoops();

  Graph graph_;
  AddressToLevelMap level_for_call_;
  CallGraph* call_graph_;
  CallGraph::Vertex call_graph_vertex_;
  double md_index_;
  double md_index_inverted_;
  Address entry_point_address_;
  FixedPoint* fixed_point_;
  uint64_t prime_;
  uint32_t byte_hash_;
  uint32_t string_references_;
  Instructions instructions_;
  CallTargets call_targets_;
  uint16_t num_loops_;
};

struct SortByAddress {
  bool operator()(const FlowGraph* one, const FlowGraph* two) const {
    return one->GetEntryPointAddress() < two->GetEntryPointAddress();
  }
};

using FlowGraphs = std::set<FlowGraph*, SortByAddress>;

}  // namespace security::bindiff

#endif  // FLOW_GRAPH_H_
