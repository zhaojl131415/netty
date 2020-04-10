/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.concurrent;

import io.netty.util.internal.UnstableApi;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation which uses simple round-robin to choose next {@link EventExecutor}.
 */
@UnstableApi
public final class DefaultEventExecutorChooserFactory implements EventExecutorChooserFactory {

    public static final DefaultEventExecutorChooserFactory INSTANCE = new DefaultEventExecutorChooserFactory();

    private DefaultEventExecutorChooserFactory() { }

    /**
     *
     * @param executors NioEventLoop的对象数组
     * @return 事件执行器选择器
     */
    @SuppressWarnings("unchecked")
    @Override
    public EventExecutorChooser newChooser(EventExecutor[] executors) {
        // 判断数组是否为2的指数幂：只是为了更高效获取
        if (isPowerOfTwo(executors.length)) {
            // 通过位运算获取
            return new PowerOfTwoEventExecutorChooser(executors);
        } else {
            // 通过取余获取
            return new GenericEventExecutorChooser(executors);
        }
    }

    private static boolean isPowerOfTwo(int val) {
        return (val & -val) == val;
    }

    private static final class PowerOfTwoEventExecutorChooser implements EventExecutorChooser {
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        /**
         *
         * @param executors NioEventLoop的对象数组
         */
        PowerOfTwoEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        /**
         * 从executors所表示的NioEventLoop对象数组中返回NioEventLoop对象
         * @return
         */
        @Override
        public EventExecutor next() {
            // 位运算获取 效率肯定高于取余
            return executors[idx.getAndIncrement() & executors.length - 1];
        }
    }

    private static final class GenericEventExecutorChooser implements EventExecutorChooser {
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        /**
         *
         * @param executors NioEventLoop的对象数组
         */
        GenericEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        /**
         * 从executors所表示的NioEventLoop对象数组中返回NioEventLoop对象
         * @return
         */
        @Override
        public EventExecutor next() {
            // 取余获取
            return executors[Math.abs(idx.getAndIncrement() % executors.length)];
        }
    }
}
