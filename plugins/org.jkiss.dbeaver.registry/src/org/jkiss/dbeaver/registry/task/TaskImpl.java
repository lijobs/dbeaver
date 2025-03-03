/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2019 Serge Rider (serge@jkiss.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.registry.task;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPNamedObject2;
import org.jkiss.dbeaver.model.DBPObjectWithDescription;
import org.jkiss.dbeaver.model.app.DBPProject;
import org.jkiss.dbeaver.model.task.DBTTask;
import org.jkiss.dbeaver.model.task.DBTTaskEvent;
import org.jkiss.dbeaver.model.task.DBTTaskRun;
import org.jkiss.dbeaver.model.task.DBTTaskType;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.utils.ArrayUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * TaskImpl
 */
public class TaskImpl implements DBTTask, DBPNamedObject2, DBPObjectWithDescription {

    private static final Log log = Log.getLog(TaskImpl.class);
    private static final String META_FILE_NAME = "meta.json";

    private static final int MAX_RUNS_IN_STATS = 100;
    private static final TaskRunImpl VOID_RUN = new TaskRunImpl();
    private static final Gson gson = new GsonBuilder()
        .setLenient()
        .setDateFormat(GeneralUtils.DEFAULT_TIMESTAMP_PATTERN)
        .create();

    private final DBPProject project;
    private String id;
    private String label;
    private String description;
    private Date createTime;
    private Date updateTime;
    private DBTTaskType type;
    private Map<String, Object> properties;
    private TaskRunImpl lastRun;

    private static class RunStatistics {
        private List<TaskRunImpl> runs = new ArrayList<>();
    }

    public TaskImpl(DBPProject project, DBTTaskType type, String id, String label, String description, Date createTime, Date updateTime) {
        this.project = project;
        this.id = id;
        this.label = label;
        this.description = description;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.type = type;
    }

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    @Override
    public DBPProject getProject() {
        return project;
    }

    @NotNull
    @Override
    public String getName() {
        return label;
    }

    @Override
    public void setName(@NotNull String label) {
        this.label = label;
    }

    @NotNull
    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(@NotNull String description) {
        this.description = description;
    }

    @NotNull
    @Override
    public Date getCreateTime() {
        return createTime;
    }

    @NotNull
    @Override
    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @NotNull
    @Override
    public DBTTaskType getType() {
        return type;
    }

    @NotNull
    @Override
    public Map<String, Object> getProperties() {
        return properties;
    }

    @Nullable
    @Override
    public DBTTaskRun getLastRun() {
        if (lastRun == null) {
            synchronized (this) {
                List<TaskRunImpl> runs = loadRunStatistics().runs;
                lastRun = runs.isEmpty() ? VOID_RUN : runs.get(runs.size() - 1);
            }
        }
        return lastRun == VOID_RUN ? null : lastRun;
    }

    @NotNull
    @Override
    public DBTTaskRun[] getRunStatistics() {
        return loadRunStatistics().runs.toArray(new DBTTaskRun[0]);
    }

    @NotNull
    @Override
    public File getRunLog(DBTTaskRun run) {
        return new File(getTaskStatsFolder(false), TaskRunImpl.RUN_LOG_PREFIX + run.getId() + "." + TaskRunImpl.RUN_LOG_EXT);
    }

    @Override
    public void cleanRunStatistics() {
        File statsFolder = getTaskStatsFolder(false);
        if (statsFolder.exists()) {
            for (File file : ArrayUtils.safeArray(statsFolder.listFiles())) {
                file.delete();
            }
            statsFolder.delete();
        }
        TaskRegistry.getInstance().notifyTaskListeners(new DBTTaskEvent(this, DBTTaskEvent.Action.TASK_UPDATE));
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    File getTaskStatsFolder(boolean create) {
        File taskStatsFolder = new File(project.getTaskManager().getStatisticsFolder(), id);
        if (create && !taskStatsFolder.exists() && !taskStatsFolder.mkdirs()) {
            log.error("Can't create task log folder '" + taskStatsFolder.getAbsolutePath() + "'");
        }
        return taskStatsFolder;
    }

    RunStatistics loadRunStatistics() {
        File metaFile = new File(getTaskStatsFolder(false), META_FILE_NAME);
        if (!metaFile.exists()) {
            return new RunStatistics();
        }
        try (FileReader reader = new FileReader(metaFile)) {
            return gson.fromJson(reader, RunStatistics.class);
        } catch (IOException e) {
            log.error("Error reading task run statistics", e);
            return new RunStatistics();
        }
    }

    private void flushRunStatistics(RunStatistics stats) {
        File metaFile = new File(getTaskStatsFolder(true), META_FILE_NAME);
        try (FileWriter writer = new FileWriter(metaFile)) {
            String metaContent = gson.toJson(stats);
            writer.write(metaContent);
        } catch (IOException e) {
            log.error("Error writing task run statistics", e);
        }
    }

    void addNewRun(TaskRunImpl taskRun) {
        synchronized (this) {
            lastRun = taskRun;
            RunStatistics stats = loadRunStatistics();
            stats.runs.add(taskRun);
            while (stats.runs.size() > MAX_RUNS_IN_STATS) {
                stats.runs.remove(0);
            }
            flushRunStatistics(stats);
        }
        TaskRegistry.getInstance().notifyTaskListeners(new DBTTaskEvent(this, DBTTaskEvent.Action.TASK_UPDATE));
    }

    @Override
    public String toString() {
        return id + " " + label + " (" + type.getName() + ")";
    }

}
