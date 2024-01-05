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

package com.google.security.zynamics.bindiff.graph.layout;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.security.zynamics.bindiff.enums.ELayoutOrientation;
import com.google.security.zynamics.bindiff.graph.settings.GraphLayoutSettings;
import com.google.security.zynamics.zylib.gui.zygraph.layouters.CircularStyle;
import com.google.security.zynamics.zylib.gui.zygraph.layouters.HierarchicOrientation;
import com.google.security.zynamics.zylib.gui.zygraph.layouters.OrthogonalStyle;
import com.google.security.zynamics.zylib.yfileswrap.gui.zygraph.layouters.ZyGraphLayouter;
import y.layout.CanonicMultiStageLayouter;

public final class LayoutCreator {
  public static CanonicMultiStageLayouter getCircularLayout(final GraphLayoutSettings settings) {
    checkNotNull(settings);

    final CircularStyle style = CircularStyle.parseInt(settings.getCircularLayoutStyle().ordinal());
    final long minNodeDist = settings.getMinimumCircularNodeDistance();

    return ZyGraphLayouter.createCircularLayouter(style, minNodeDist);
  }

  public static CanonicMultiStageLayouter getHierarchicalLayout(
      final GraphLayoutSettings settings) {
    checkNotNull(settings);

    final boolean orthogonalEdgeRouting = settings.getHierarchicalOrthogonalEdgeRouting();
    final long minLayerDist = settings.getMinimumHierarchicLayerDistance();
    final long minNodeDist = settings.getMinimumHierarchicNodeDistance();
    final HierarchicOrientation orientation =
        HierarchicOrientation.parseInt(settings.getHierarchicalOrientation().ordinal());

    return ZyGraphLayouter.createIncrementalHierarchicalLayouter(
        orthogonalEdgeRouting, minLayerDist, minNodeDist, orientation);
  }

  public static CanonicMultiStageLayouter getOrthogonalLayout(final GraphLayoutSettings settings) {
    checkNotNull(settings);

    final OrthogonalStyle style =
        OrthogonalStyle.parseInt(settings.getOrthogonalLayoutStyle().ordinal());
    final long gridSize = settings.getMinimumOrthogonalNodeDistance();
    final boolean isVerticalOrientation =
        settings.getOrthogonalLayoutOrientation() == ELayoutOrientation.VERTICAL;

    return ZyGraphLayouter.createOrthoLayouter(style, gridSize, isVerticalOrientation);
  }
}
