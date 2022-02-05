/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.opensearch.ml.stats.StatNames.ML_EXECUTING_TASK_COUNT;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.action.ActionListener;
import org.opensearch.ml.model.MLTask;
import org.opensearch.ml.model.MLTaskState;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableMap;

/**
 * MLTaskRunner has common code for dispatching and running predict/training tasks.
 * @param <Request> ML task request
 * @param <Response> ML task request
 */
public abstract class MLTaskRunner<Request, Response> {
    public static final int TIMEOUT_IN_MILLIS = 2000;
    protected final MLTaskManager mlTaskManager;
    protected final MLStats mlStats;
    protected final MLTaskDispatcher mlTaskDispatcher;

    protected static final String TASK_ID = "task_id";
    protected static final String ALGORITHM = "algorithm";
    protected static final String MODEL_NAME = "model_name";
    protected static final String MODEL_VERSION = "model_version";
    protected static final String MODEL_CONTENT = "model_content";
    protected static final String USER = "user";

    public MLTaskRunner(MLTaskManager mlTaskManager, MLStats mlStats, MLTaskDispatcher mlTaskDispatcher) {
        this.mlTaskManager = mlTaskManager;
        this.mlStats = mlStats;
        this.mlTaskDispatcher = mlTaskDispatcher;
    }

    protected void handleMLTaskFailure(MLTask mlTask, Exception e) {
        // decrease ML_EXECUTING_TASK_COUNT
        // update task state to MLTaskState.FAILED
        // update task error
        mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).decrement();
        if (mlTask.isAsync()) {
            Map<String, Object> updatedFields = ImmutableMap
                .of(MLTask.STATE_FIELD, MLTaskState.FAILED.name(), MLTask.ERROR_FIELD, e.getMessage());
            // wait for 2 seconds to make sure failed state persisted
            mlTaskManager.updateMLTask(mlTask.getTaskId(), updatedFields, TIMEOUT_IN_MILLIS);
        }
    }

    protected void handleMLTaskComplete(MLTask mlTask) {
        // decrease ML_EXECUTING_TASK_COUNT
        // update task state to MLTaskState.COMPLETED
        mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).decrement();
        if (mlTask.isAsync()) {
            Map<String, Object> updatedFields = new HashMap<>();
            updatedFields.put(MLTask.STATE_FIELD, MLTaskState.COMPLETED);
            if (mlTask.getModelId() != null) {
                updatedFields.put(MLTask.MODEL_ID_FIELD, mlTask.getModelId());
            }
            // wait for 2 seconds to make sure completed state persisted
            mlTaskManager.updateMLTask(mlTask.getTaskId(), updatedFields, TIMEOUT_IN_MILLIS);
        }
    }

    public abstract void run(Request request, TransportService transportService, ActionListener<Response> listener);
}