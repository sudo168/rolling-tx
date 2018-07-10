package net.ewant.rolling.transaction.concert.zookeeper;

import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.WatchedEvent;

public class CuratorWatcherProcessor implements CuratorWatcher {
    @Override
    public void process(WatchedEvent watchedEvent) throws Exception {

    }
}
