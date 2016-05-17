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
package com.dlmu.agent.client.receiver;

import com.dlmu.agent.client.Span;
import com.dlmu.agent.common.conf.DTraceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Used for testing. Simply prints to standard out any spans it receives.
 */
public class StandardOutSpanReceiver extends SpanReceiver {
    private static final Logger logger = LoggerFactory.getLogger(StandardOutSpanReceiver.class);

    public StandardOutSpanReceiver(DTraceConfiguration conf) {
        logger.trace("Created new StandardOutSpanReceiver.");
    }

    @Override
    public void receiveSpan(Span span) {
        System.out.println(span);
    }

    @Override
    public void close() throws IOException {
    }
}
