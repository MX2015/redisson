/**
 * Copyright 2016 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.executor;

import java.util.Arrays;

import org.redisson.Redisson;
import org.redisson.RedissonRemoteService;
import org.redisson.api.RBlockingQueue;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.remote.RemoteServiceRequest;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ExecutorRemoteService extends RedissonRemoteService {

    private String tasksCounterName;
    private String statusName;
    
    public ExecutorRemoteService(Codec codec, Redisson redisson, String name, CommandExecutor commandExecutor) {
        super(codec, redisson, name, commandExecutor);
    }
    
    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }
    
    public void setTasksCounterName(String tasksCounterName) {
        this.tasksCounterName = tasksCounterName;
    }

    @Override
    protected Future<Boolean> addAsync(RBlockingQueue<RemoteServiceRequest> requestQueue,
            RemoteServiceRequest request, RemotePromise<Object> result) {
        final Promise<Boolean> promise = commandExecutor.getConnectionManager().newPromise();
        Future<Boolean> future = commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('exists', KEYS[2]) == 0 then "
                    + "redis.call('rpush', KEYS[3], ARGV[1]); "
                    + "redis.call('incr', KEYS[1]);"
                    + "return 1;"
                + "end;"
                + "return 0;", 
                Arrays.<Object>asList(tasksCounterName, statusName, requestQueue.getName()),
                encode(request));
        
        result.setAddFuture(future);
        
        future.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.setFailure(future.cause());
                    return;
                }

                if (!future.getNow()) {
                    promise.cancel(true);
                    return;
                }
                
                promise.setSuccess(true);
            }
        });
        
        return promise;
    }

}
