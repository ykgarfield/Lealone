/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.cluster.service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.lealone.cluster.config.DatabaseDescriptor;
import org.lealone.cluster.net.IAsyncCallback;
import org.lealone.cluster.net.MessageIn;
import org.lealone.cluster.utils.concurrent.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TruncateResponseHandler implements IAsyncCallback<Object> {
    protected static final Logger logger = LoggerFactory.getLogger(TruncateResponseHandler.class);
    protected final SimpleCondition condition = new SimpleCondition();
    private final int responseCount;
    protected final AtomicInteger responses = new AtomicInteger(0);
    private final long start;

    public TruncateResponseHandler(int responseCount) {
        // at most one node per range can bootstrap at a time, and these will be added to the write until
        // bootstrap finishes (at which point we no longer need to write to the old ones).
        assert 1 <= responseCount : "invalid response count " + responseCount;

        this.responseCount = responseCount;
        start = System.nanoTime();
    }

    public void get() throws TimeoutException {
        long timeout = TimeUnit.MILLISECONDS.toNanos(DatabaseDescriptor.getTruncateRpcTimeout()) - (System.nanoTime() - start);
        boolean success;
        try {
            success = condition.await(timeout, TimeUnit.NANOSECONDS); // TODO truncate needs a much longer timeout
        } catch (InterruptedException ex) {
            throw new AssertionError(ex);
        }

        if (!success) {
            throw new TimeoutException("Truncate timed out - received only " + responses.get() + " responses");
        }
    }

    @Override
    public void response(MessageIn<Object> message) {
        responses.incrementAndGet();
        if (responses.get() >= responseCount)
            condition.signalAll();
    }

    @Override
    public boolean isLatencyForSnitch() {
        return false;
    }
}
