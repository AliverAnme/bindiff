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

package com.google.security.zynamics.bindiff.graph;

import com.google.security.zynamics.bindiff.graph.settings.GraphSettings;

public abstract class AbstractGraphsContainer implements Iterable<BinDiffGraph<?, ?>> {
  public abstract void dispose();

  public abstract CombinedGraph getCombinedGraph();

  public abstract BinDiffGraph<?, ?> getFocusedGraph();

  public abstract SingleGraph getPrimaryGraph();

  public abstract SingleGraph getSecondaryGraph();

  public abstract GraphSettings getSettings();

  public abstract SuperGraph getSuperGraph();

  public abstract void updateViews();
}
