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
package org.jkiss.dbeaver.model.sql.data;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.data.DBDDataFilter;
import org.jkiss.dbeaver.model.data.DBDDataReceiver;
import org.jkiss.dbeaver.model.exec.*;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.*;
import org.jkiss.dbeaver.model.struct.DBSDataContainer;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * Data container for single SQL query.
 * Doesn't support multiple resulsets.
 */
public class SQLQueryDataContainer implements DBSDataContainer, SQLQueryContainer, DBPContextProvider {

    private DBPContextProvider contextProvider;
    private SQLQuery query;
    private Log log;

    public SQLQueryDataContainer(DBPContextProvider contextProvider, SQLQuery query, Log log) {
        this.contextProvider = contextProvider;
        this.query = query;
        this.log = log;
    }

    @Override
    public DBCExecutionContext getExecutionContext() {
        return contextProvider.getExecutionContext();
    }

    @Override
    public int getSupportedFeatures() {
        return DATA_SELECT;
    }

    @NotNull
    @Override
    public DBCStatistics readData(@NotNull DBCExecutionSource source, @NotNull DBCSession session, @NotNull DBDDataReceiver dataReceiver, DBDDataFilter dataFilter, long firstRow, long maxRows, long flags, int fetchSize) throws DBCException
    {
        DBCStatistics statistics = new DBCStatistics();
        // Modify query (filters + parameters)
        DBPDataSource dataSource = session.getDataSource();
        SQLQuery sqlQuery = query;
        String queryText = sqlQuery.getText();//.trim();
        if (dataFilter != null && dataFilter.hasFilters() && dataSource instanceof SQLDataSource) {
            String filteredQueryText = ((SQLDataSource) dataSource).getSQLDialect().addFiltersToQuery(
                dataSource, queryText, dataFilter);
            sqlQuery = new SQLQuery(dataSource, filteredQueryText, sqlQuery);
        } else {
            sqlQuery = new SQLQuery(dataSource, queryText, sqlQuery);
        }

        final SQLQueryResult curResult = new SQLQueryResult(sqlQuery);
        if (firstRow > 0) {
            curResult.setRowOffset(firstRow);
        }
        statistics.setQueryText(sqlQuery.getText());

        long startTime = System.currentTimeMillis();

        try (final DBCStatement dbcStatement = DBUtils.makeStatement(
            source,
            session,
            DBCStatementType.SCRIPT,
            sqlQuery,
            firstRow,
            maxRows))
        {
            DBExecUtils.setStatementFetchSize(dbcStatement, firstRow, maxRows, fetchSize);

            // Execute statement

            session.getProgressMonitor().subTask("Execute query");

            boolean hasResultSet = dbcStatement.executeStatement();

            statistics.addExecuteTime(System.currentTimeMillis() - startTime);
            statistics.addStatementsCount();

            curResult.setHasResultSet(hasResultSet);

            if (hasResultSet) {
                DBCResultSet resultSet = dbcStatement.openResultSet();
                if (resultSet != null) {
                    SQLQueryResult.ExecuteResult executeResult = curResult.addExecuteResult(true);
                    DBRProgressMonitor monitor = session.getProgressMonitor();
                    monitor.subTask("Fetch result set");
                    DBFetchProgress fetchProgress = new DBFetchProgress(session.getProgressMonitor());

                    dataReceiver.fetchStart(session, resultSet, firstRow, maxRows);

                    try {
                        long fetchStartTime = System.currentTimeMillis();

                        // Fetch all rows
                        while (!fetchProgress.isMaxRowsFetched(maxRows) && !fetchProgress.isCanceled() && resultSet.nextRow()) {
                            dataReceiver.fetchRow(session, resultSet);
                            fetchProgress.monitorRowFetch();
                        }
                        statistics.addFetchTime(System.currentTimeMillis() - fetchStartTime);
                    }
                    finally {
                        try {
                            resultSet.close();
                        } catch (Throwable e) {
                            log.error("Error while closing resultset", e);
                        }
                        try {
                            dataReceiver.fetchEnd(session, resultSet);
                        } catch (Throwable e) {
                            log.error("Error while handling end of result set fetch", e);
                        }
                        dataReceiver.close();
                    }

                    if (executeResult != null) {
                        executeResult.setRowCount(fetchProgress.getRowCount());
                    }
                    statistics.setRowsFetched(fetchProgress.getRowCount());
                    monitor.subTask(fetchProgress.getRowCount() + " rows fetched");
                }
            } else {
                log.warn("No results returned by query execution");
            }
            try {
                curResult.addWarnings(dbcStatement.getStatementWarnings());
            } catch (Throwable e) {
                log.warn("Can't read execution warnings", e);
            }
        }

        return statistics;
    }

    @Override
    public long countData(@NotNull DBCExecutionSource source, @NotNull DBCSession session, DBDDataFilter dataFilter, long flags)
        throws DBCException
    {
        return -1;
    }

    @Nullable
    @Override
    public String getDescription()
    {
        return "SQL Query";
    }

    @Nullable
    @Override
    public DBSObject getParentObject()
    {
        return getDataSource();
    }

    @Nullable
    @Override
    public DBPDataSource getDataSource()
    {
        DBCExecutionContext executionContext = getExecutionContext();
        return executionContext == null ? null : executionContext.getDataSource();
    }

    @Override
    public boolean isPersisted() {
        return false;
    }

    @NotNull
    @Override
    public String getName()
    {
        String name = query.getOriginalText();
        if (name == null) {
            name = "SQL";
        }
        return name;
    }

    @Nullable
    @Override
    public DBPDataSourceContainer getDataSourceContainer() {
        DBPDataSource dataSource = getDataSource();
        return dataSource == null ? null : dataSource.getContainer();
    }

    @Override
    public String toString() {
        return query.getOriginalText();
    }

    @Override
    public SQLScriptElement getQuery() {
        return query;
    }

}
