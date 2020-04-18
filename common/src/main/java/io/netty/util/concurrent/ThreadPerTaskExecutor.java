/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.internal.ObjectUtil;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * Executor是一个JDK源码的顶层接口,在它里面只声明了一个execute(Runnable)方法, 返回值为void
 * 参数为:Runnable类型,表示就是用来执行传进去的任务的
 */
public final class ThreadPerTaskExecutor implements Executor {
    private final ThreadFactory threadFactory;

    public ThreadPerTaskExecutor(ThreadFactory threadFactory) {
        // checkNotNull:判断是否为空，如果为空抛异常，不为空直接返回在赋值
        this.threadFactory = ObjectUtil.checkNotNull(threadFactory, "threadFactory");
    }

    /**
     * 需要开启一个线程时，只需要调用此方法，传入一个Runnable任务，就会通过ThreadFactory创建一个线程执行
     * @param command
     */
    @Override
    public void execute(Runnable command) {
        /**
         * @see DefaultThreadFactory#newThread(java.lang.Runnable)
         */
        threadFactory.newThread(command).start();
    }
}
