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
package org.jkiss.dbeaver.model.task;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.DBPNamedObject;
import org.jkiss.dbeaver.model.DBPObjectWithDescription;
import org.jkiss.dbeaver.model.app.DBPProject;

import java.io.File;
import java.util.Date;
import java.util.Map;

/**
 * Task configuration
 */
public interface DBTTask extends DBPNamedObject, DBPObjectWithDescription {

    @NotNull
    String getId();

    @NotNull
    DBPProject getProject();

    @NotNull
    Date getCreateTime();

    @NotNull
    Date getUpdateTime();

    @NotNull
    DBTTaskType getType();

    @NotNull
    Map<String, Object> getProperties();

    @Nullable
    DBTTaskRun getLastRun();

    @NotNull
    DBTTaskRun[] getRunStatistics();

    @NotNull
    File getRunLog(DBTTaskRun run);

    void cleanRunStatistics();

}
