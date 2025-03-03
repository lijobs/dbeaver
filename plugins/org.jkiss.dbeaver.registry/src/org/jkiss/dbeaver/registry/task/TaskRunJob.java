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

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.runtime.*;
import org.jkiss.dbeaver.model.task.DBTTaskExecutionListener;
import org.jkiss.dbeaver.model.task.DBTTaskHandler;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.StandardConstants;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TaskRunJob
 */
public class TaskRunJob extends AbstractJob implements DBRRunnableContext {

    private static final Log log = Log.getLog(TaskRunJob.class);

    private static AtomicInteger taskNumber = new AtomicInteger(0);

    private final TaskImpl task;
    private final Locale locale;
    private DBTTaskExecutionListener executionListener;
    private Log taskLog = log;
    private DBRProgressMonitor activeMonitor;

    private long startTime;
    private long elapsedTime;
    private Throwable taskError;

    protected TaskRunJob(TaskImpl task, Locale locale, DBTTaskExecutionListener executionListener) {
        super("Task [" + task.getType().getName() + "] runner - " + task.getName());
        this.task = task;
        this.locale = locale;
        this.executionListener = new LoggingExecutionListener(executionListener);

    }

    @Override
    protected IStatus run(DBRProgressMonitor monitor) {
        try {
            Date startTime = new Date();
            File taskStatsFolder = task.getTaskStatsFolder(true);
            File logFile = new File(taskStatsFolder, TaskRunImpl.RUN_LOG_PREFIX + TaskManagerImpl.systemDateFormat.format(startTime) + "." + TaskRunImpl.RUN_LOG_EXT);

            String taskId = TaskManagerImpl.systemDateFormat.format(startTime) + "_" + taskNumber.incrementAndGet();
            TaskRunImpl taskRun = new TaskRunImpl(
                taskId,
                new Date(),
                System.getProperty(StandardConstants.ENV_USER_NAME),
                GeneralUtils.getProductTitle(),
                0, null, null);

            try (OutputStream logStream = new FileOutputStream(logFile)) {
                taskLog = new Log(getName(), logStream);
                try {
                    monitor.beginTask("Run task '" + task.getName() + " (" + task.getType().getName() + ")", 1);
                    executeTask(new LoggingProgressMonitor(monitor));
                } catch (Throwable e) {
                    taskError = e;
                    taskLog.error("Task fatal error", e);
                    throw e;
                } finally {
                    monitor.done();
                    taskLog.flush();
                }
            } finally {
                taskRun.setRunDuration(elapsedTime);
                if (taskError != null) {
                    String errorMessage = taskError.getMessage();
                    if (CommonUtils.isEmpty(errorMessage)) {
                        errorMessage = taskError.getClass().getName();
                    }
                    taskRun.setErrorMessage(errorMessage);
                    StringWriter buf = new StringWriter();
                    taskError.printStackTrace(new PrintWriter(buf, true));
                    taskRun.setErrorStackTrace(buf.toString());
                }
                task.addNewRun(taskRun);
            }
        } catch (Throwable e) {
            taskLog.error("Error running task", e);
            return GeneralUtils.makeExceptionStatus(e);
        }
        return Status.OK_STATUS;
    }

    private void executeTask(DBRProgressMonitor monitor) throws DBException {
        activeMonitor = monitor;
        DBTTaskHandler taskHandler = task.getType().createHandler();
        taskHandler.executeTask(this, task, locale, taskLog, executionListener);
    }

    @Override
    public void run(boolean fork, boolean cancelable, DBRRunnableWithProgress runnable) throws InvocationTargetException, InterruptedException {
        runnable.run(activeMonitor);
    }

    private class LoggingProgressMonitor extends ProxyProgressMonitor {
        public LoggingProgressMonitor(DBRProgressMonitor monitor) {
            super(monitor);
        }

        @Override
        public void beginTask(String name, int totalWork) {
            super.beginTask(name, totalWork);
            taskLog.debug(">> " + name);
        }

        @Override
        public void subTask(String name) {
            super.subTask(name);
            taskLog.debug(">>> " + name);
        }
    }

    private class LoggingExecutionListener implements DBTTaskExecutionListener {

        DBTTaskExecutionListener parent;

        public LoggingExecutionListener(DBTTaskExecutionListener src) {
            this.parent = src;
        }

        @Override
        public void taskStarted(@NotNull Object task) {
            startTime = System.currentTimeMillis();
            parent.taskStarted(task);
        }

        @Override
        public void taskFinished(@NotNull Object task, @Nullable Throwable error) {
            parent.taskFinished(task, error);
            elapsedTime = System.currentTimeMillis() - startTime;
            taskError = error;
        }

        @Override
        public void subTaskFinished(@Nullable Throwable error) {
            parent.subTaskFinished(error);
        }
    }

}
