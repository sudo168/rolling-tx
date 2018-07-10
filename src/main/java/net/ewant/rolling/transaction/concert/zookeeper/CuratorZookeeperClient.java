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
package net.ewant.rolling.transaction.concert.zookeeper;

import net.ewant.rolling.transaction.TransactionContext;
import net.ewant.rolling.transaction.concert.*;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryNTimes;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.WatchedEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CuratorZookeeperClient implements MediatorClient {

    private Map<String, Integer> watcherMap = new ConcurrentHashMap<>();

    private MediatorWatcher listener;

    private boolean inited;

    private final CuratorFramework client;

    public CuratorZookeeperClient(URL url) {
        try {
            CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                    .connectString(url.getBackupAddress())
                    .retryPolicy(new RetryNTimes(1, 1000))
                    .connectionTimeoutMs(5000);
            String authority = url.getAuthority();
            if (authority != null && authority.length() > 0) {
                builder = builder.authorization("digest", authority.getBytes());
            }
            client = builder.build();
            client.getConnectionStateListenable().addListener(new ConnectionStateListener() {
                @Override
                public void stateChanged(CuratorFramework client, ConnectionState state) {
                    if(ConnectionState.CONNECTED == state){
                        init();
                    }
                }
            });
            client.start();
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void init() {
        if(inited){
           return;
        }
        inited = true;
        create(TransactionContext.TRANSACTION_CHAIN_ROOT, "", false);
        create(TransactionContext.TRANSACTION_STATE_ROOT, "", false);
    }

    private void create(String path, String data, boolean ephemeral) {
        if(data == null){
            data = "";
        }
        int i = path.lastIndexOf('/');
        if (i > 0) {
            String parentPath = path.substring(0, i);
            if (!exists(parentPath)) {
                create(parentPath, data,false);
            }
        }
        if (ephemeral) {
            createEphemeral(path, data);
        } else {
            createPersistent(path, data);
        }
    }

    private void createPersistent(String path, String data) {
        try {
            client.create().forPath(path, data.getBytes());
        } catch (NodeExistsException e) {
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void createEphemeral(String path, String data) {
        try {
            client.create().withMode(CreateMode.EPHEMERAL).forPath(path, data.getBytes());
        } catch (NodeExistsException e) {
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private boolean exists(String path) {
        try {
            if (client.checkExists().forPath(path) != null) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    private void delete(String path, boolean ignoreChildren) {
        try {
            if(ignoreChildren){
                client.delete().deletingChildrenIfNeeded().forPath(path);
            }else{
                client.delete().forPath(path);
            }
        } catch (NoNodeException e) {
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void addWatcher(MediatorWatcher watcher) {
        this.listener = watcher;
    }

    @Override
    public void clear(String transactionId) {
        String txChain = TransactionContext.TRANSACTION_CHAIN_ROOT + TransactionContext.NODE_SPLIT_CHAR + transactionId;
        String txState = TransactionContext.TRANSACTION_STATE_ROOT + TransactionContext.NODE_SPLIT_CHAR + transactionId;
        delete(txChain, true);
        delete(txState, true);
    }

    @Override
    public void joinChain(String transactionId, String group, String peer, int index, String data) {
        String txChain = TransactionContext.TRANSACTION_CHAIN_ROOT + TransactionContext.NODE_SPLIT_CHAR + transactionId;
        String txState = TransactionContext.TRANSACTION_STATE_ROOT + TransactionContext.NODE_SPLIT_CHAR + transactionId;
        boolean exists = exists(txChain);
        if(index > 0 && !exists){
            throw new IllegalStateException("Transaction join failed, invalid id [" + transactionId + "] in chain");
        }
        if(index == 0){
            // create new
            createPersistent(txChain, group + TransactionContext.FIELD_SPLIT_CHAR + peer + TransactionContext.FIELD_SPLIT_CHAR + System.currentTimeMillis());
            createPersistent(txState, "0");
        }
        // join
        if(data != null){
            create(txChain + TransactionContext.NODE_SPLIT_CHAR + group + TransactionContext.NODE_SPLIT_CHAR + peer + TransactionContext.NODE_SPLIT_CHAR + index, data, false);
        }
        try {
            // watch rollback
            if(!watcherMap.containsKey(txState)){
                watcherMap.put(txState, 1);
                client.getData().usingWatcher(new CuratorWatcherImpl()).forPath(txState);
            }
        } catch (Exception e) {
            // TODO rollback watch error ?
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void rollback(String transactionId, String group, String peer, String data) {
        try {
            String txState = TransactionContext.TRANSACTION_STATE_ROOT + TransactionContext.NODE_SPLIT_CHAR + transactionId;
            // TODO 是否需要处理返回值？
            client.setData().forPath(txState, data == null ? "".getBytes() : data.getBytes());
        } catch (Exception e) {
            // TODO rollback error ?
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void commit(String transactionId, String group, String peer) {
        try {
            client.setData().forPath(TransactionContext.TRANSACTION_STATE_ROOT + TransactionContext.NODE_SPLIT_CHAR + transactionId, "1".getBytes());
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public boolean transactionExists(String transactionId) {
        return exists(TransactionContext.TRANSACTION_STATE_ROOT + TransactionContext.NODE_SPLIT_CHAR + transactionId);
    }

    @Override
    public TransactionResult getResults(String transactionId) {
        return null;
    }

    @Override
    public boolean isConnected() {
        return client.getZookeeperClient().isConnected();
    }

    @Override
    public void close() {
        client.close();
    }

    private class CuratorWatcherImpl implements CuratorWatcher {

        @Override
        public void process(WatchedEvent event) throws Exception {
            if (listener != null) {
                String path = event.getPath() == null ? "" : event.getPath();
                if(path.length() > 0){
                    watcherMap.remove(path);
                    int lastIndex = path.lastIndexOf(TransactionContext.NODE_SPLIT_CHAR);
                    listener.change(path.substring(lastIndex + 1),
                            // if path is null, curator using watcher will throw NullPointerException.
                            // if client connect or disconnect to server, zookeeper will queue
                            // watched event(Watcher.Event.EventType.None, .., path = null).
                            new String(client.getData().forPath(path)));
                }
            }
        }
    }

}
