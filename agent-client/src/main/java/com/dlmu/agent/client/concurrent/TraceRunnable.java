/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dlmu.agent.client.concurrent;

import com.dlmu.agent.client.DTraceClient;
import com.dlmu.agent.client.SpanId;
import com.dlmu.agent.client.TraceScope;

/**
 * Wrap a Runnable with a Span that survives a change in threads.
 */
public class TraceRunnable implements Runnable {
    private final DTraceClient dTraceClient;
    private final SpanId parentId;
    private final Runnable runnable;
    private final String description;

    /**
     * @deprecated Use {@link #TraceRunnable(DTraceClient, SpanId, Runnable, String)} instead.
     */
    @Deprecated
    public TraceRunnable(DTraceClient dTraceClient, TraceScope parent,
                         Runnable runnable, String description) {
        this(dTraceClient, parent.getSpanId(), runnable, description);
    }

    public TraceRunnable(DTraceClient dTraceClient, SpanId parentId,
                         Runnable runnable, String description) {
        this.dTraceClient = dTraceClient;
        this.parentId = parentId;
        this.runnable = runnable;
        this.description = description;
    }

    @Override
    public void run() {
        String description = this.description;
        if (description == null) {
            description = Thread.currentThread().getName();
        }
        try (TraceScope chunk = dTraceClient.newScope(description, parentId)) {
            runnable.run();
        }
    }

    public Runnable getRunnable() {
        return runnable;
    }
}
