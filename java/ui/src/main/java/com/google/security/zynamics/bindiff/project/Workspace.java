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

package com.google.security.zynamics.bindiff.project;

import com.google.common.flogger.FluentLogger;
import com.google.security.zynamics.bindiff.database.CommentsDatabase;
import com.google.security.zynamics.bindiff.database.WorkspaceDatabase;
import com.google.security.zynamics.bindiff.enums.ESide;
import com.google.security.zynamics.bindiff.gui.window.MainWindow;
import com.google.security.zynamics.bindiff.project.diff.Diff;
import com.google.security.zynamics.bindiff.project.diff.DiffDirectories;
import com.google.security.zynamics.bindiff.project.matches.DiffMetadata;
import com.google.security.zynamics.zylib.general.ListenerProvider;
import com.google.security.zynamics.zylib.gui.CMessageBox;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class Workspace {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private File workspaceFile = null;

  private final List<Diff> diffs = new ArrayList<>();

  private String name;

  private boolean isLoaded = false;

  private final ListenerProvider<WorkspaceListener> listeners = new ListenerProvider<>();

  private MainWindow parentWindow;

  private void createCommentDatabase() {
    //noinspection EmptyTryBlock
    try (final CommentsDatabase database = new CommentsDatabase(this, true)) {
    } catch (final SQLException e) {
      logger.atSevere().withCause(e).log("%s", e.getMessage());
      CMessageBox.showError(parentWindow, e.getMessage());
    }
  }

  public void addDiff(final Diff diff) throws SQLException {
    if (diffs.contains(diff)) {
      return;
    }

    Diff toRemove = null;
    for (final Diff workspaceDiff : diffs) {
      if (workspaceDiff.getMatchesDatabase().equals(diff.getMatchesDatabase())) {
        toRemove = workspaceDiff;
      }
    }

    if (toRemove != null) {
      diffs.remove(toRemove);
    }

    diffs.add(diff);

    if (isLoaded) {
      for (final WorkspaceListener listener : listeners) {
        listener.addedDiff(diff);
      }

      saveWorkspace();
    }
  }

  public String addDiff(
      final File matchesDatabase, final DiffMetadata diffMetaData, final boolean isFunctionDiff) {
    final StringBuilder fileErrors = new StringBuilder("");

    File primaryExportFile = null;
    File secondaryExportFile = null;

    if (diffMetaData == null || !matchesDatabase.exists()) {
      fileErrors.append(" - ").append(matchesDatabase.getPath()).append("\n");
    }

    if (diffMetaData != null) {
      primaryExportFile =
          DiffDirectories.getBinExportFile(matchesDatabase, diffMetaData, ESide.PRIMARY);

      if (!primaryExportFile.exists() || primaryExportFile.isDirectory()) {
        fileErrors.append(" - ").append(primaryExportFile.getPath()).append("\n");
      }

      secondaryExportFile =
          DiffDirectories.getBinExportFile(matchesDatabase, diffMetaData, ESide.SECONDARY);

      if (!secondaryExportFile.exists() || secondaryExportFile.isDirectory()) {
        fileErrors.append(" - ").append(secondaryExportFile.getPath()).append("\n");
      }
    }

    final Diff diff =
        new Diff(
            diffMetaData, matchesDatabase, primaryExportFile, secondaryExportFile, isFunctionDiff);
    try {
      addDiff(diff);
    } catch (final SQLException e) {
      logger.atSevere().withCause(e).log("%s", e.getMessage());
      CMessageBox.showError(parentWindow, e.getMessage());
    }

    return fileErrors.toString();
  }

  public void addListener(final WorkspaceListener listener) {
    listeners.addListener(listener);
  }

  public void closeWorkspace() {
    if (!isLoaded()) {
      return;
    }

    logger.atInfo().log("Closing workspace '%s'...", workspaceFile.getPath());

    diffs.clear();

    workspaceFile = null;
    name = "";

    isLoaded = false;

    for (final WorkspaceListener listener : listeners) {
      listener.closedWorkspace();
    }

    logger.atInfo().log("Workspace closed");
  }

  public boolean containsDiff(final String matchesBinaryPath) {
    for (final Diff diff : diffs) {
      if (diff.getMatchesDatabase().getPath().equals(matchesBinaryPath)) {
        return true;
      }
    }

    return false;
  }

  public List<Diff> getDiffList() {
    return diffs;
  }

  public List<Diff> getDiffList(final boolean isFunctionDiff) {
    final List<Diff> list = new ArrayList<>();

    for (final Diff diff : diffs) {
      if (diff.isFunctionDiff() == isFunctionDiff) {
        list.add(diff);
      }
    }

    return list;
  }

  public ListenerProvider<WorkspaceListener> getListeners() {
    return listeners;
  }

  public MainWindow getWindow() {
    return parentWindow;
  }

  public File getWorkspaceDir() {
    return workspaceFile.getParentFile();
  }

  public String getWorkspaceDirPath() {
    return workspaceFile.getParent();
  }

  public File getWorkspaceFile() {
    return workspaceFile;
  }

  public String getWorkspaceFileName() {
    return name;
  }

  public String getWorkspaceFilePath() {
    return workspaceFile.getPath();
  }

  public boolean isLoaded() {
    return isLoaded;
  }

  public void newWorkspace(final File workspaceFile) throws SQLException, IOException {
    if (isLoaded) {
      throw new IOException(
          "Couldn't create new workspace. Existing workspace has to be closed first.");
    }

    this.workspaceFile = workspaceFile;
    name = workspaceFile.getName();

    isLoaded = true;

    saveWorkspace();

    createCommentDatabase();

    for (final WorkspaceListener listener : listeners) {
      listener.loadedWorkspace(this);
    }

    logger.atWarning().log("Created new Workspace");
  }

  public void removeDiff(final Diff diff) {
    diffs.remove(diff);
  }

  public void removeListener(final WorkspaceListener listener) {
    listeners.removeListener(listener);
  }

  public void saveWorkspace() throws SQLException {
    if (workspaceFile == null) {
      throw new SQLException("Couldn't save workspace. No workspace is loaded.");
    }

    try (final WorkspaceDatabase workspaceDatabase = new WorkspaceDatabase(workspaceFile)) {
      workspaceDatabase.saveWorkspace(this);
    }
  }

  public void setLoaded(final boolean isLoaded) {
    this.isLoaded = isLoaded;
  }

  public void setParentWindow(final MainWindow window) {
    parentWindow = window;
  }

  void setWorkspaceFile(final File workspaceFile) {
    this.workspaceFile = workspaceFile;
    name = workspaceFile.getName();
  }
}
