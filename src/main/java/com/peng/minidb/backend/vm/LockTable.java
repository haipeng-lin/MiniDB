package com.peng.minidb.backend.vm;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.peng.minidb.common.Error;

public class LockTable {

    private Map<Long, List<Long>> x2u;  // 某个XID已获得的资源UID列表
    private Map<Long, Long> u2x;        // UID被某个XID持有
    private Map<Long, List<Long>> wait; // 正在等待UID的XID列表
    private Map<Long, Lock> waitLock;   // 正在等待资源的XID的锁
    private Map<Long, Long> waitU;      // XID正在等待的UID
    private Lock lock;
    private Map<Long, Long> waitStartTime;  // 记录每个XID进入等待状态的时间
    private static final int CHECK_INTERVAL_MS = 1000; // 检查间隔（0.6秒）
    private static final int TIMEOUT_THRESHOLD_MS = 30000; // 超时时间阈值（30秒）


    public LockTable() {
        x2u = new HashMap<>();
        u2x = new HashMap<>();
        wait = new HashMap<>();
        waitLock = new HashMap<>();
        waitU = new HashMap<>();
        lock = new ReentrantLock();
        waitStartTime = new HashMap<>();
        startTimeoutDeadlockChecker();
    }

    // 尝试获取资源，如需等待则检测死锁并处理
    public Lock add(long xid, long uid) throws Exception {
        lock.lock();
        try {
            if (isInList(x2u, xid, uid)) {
                return null; // 已拥有资源
            }
            if (!u2x.containsKey(uid)) { // 资源未被占用
                u2x.put(uid, xid);
                putIntoList(x2u, xid, uid);
                return null;
            }
            waitU.put(xid, uid); // 资源被占用，进入等待
            putIntoList(wait, uid, xid);
            waitStartTime.put(xid, System.currentTimeMillis());

            // 仅在等待链长度达到阈值时才触发死锁检测
            if (hasDeadLock()) {
                waitU.remove(xid);
                removeFromList(wait, uid, xid);
                throw Error.DeadlockException;
            }

            Lock l = new ReentrantLock();
            l.lock();
            waitLock.put(xid, l);
            return l;

        } finally {
            lock.unlock();
        }
    }

    public void remove(long xid) {
        lock.lock();
        try {
            List<Long> l = x2u.get(xid);
            if (l != null) {
                while (l.size() > 0) {
                    Long uid = l.remove(0);
                    selectNewXID(uid); // 重新分配资源
                }
            }
            waitU.remove(xid);
            x2u.remove(xid);
            waitLock.remove(xid);
        } finally {
            lock.unlock();
        }
    }

    // 从等待队列中选择一个xid来占用uid
    private void selectNewXID(long uid) {
        u2x.remove(uid);
        List<Long> l = wait.get(uid);
        if (l == null) return;
        assert l.size() > 0;

        while (l.size() > 0) {
            long xid = l.remove(0);
            if (!waitLock.containsKey(xid)) {
                continue;
            } else {
                u2x.put(uid, xid);
                Lock lo = waitLock.remove(xid);
                waitU.remove(xid);
                lo.unlock(); // 解锁等待事务
                break;
            }
        }

        if (l.size() == 0) wait.remove(uid);
    }

    private Map<Long, Integer> xidStamp;
    private Map<Long, Boolean> pathCache; // 路径缓存
    private int stamp;

    // 死锁检测，使用DFS
    private boolean hasDeadLock() {
        xidStamp = new HashMap<>();
        pathCache = new HashMap<>();
        stamp = 1;
        for (long xid : x2u.keySet()) {
            if (xidStamp.getOrDefault(xid, 0) > 0) continue;
            stamp++;
            if (dfs(xid)) return true;
        }
        return false;
    }

    private boolean dfs(long xid) {
        // 如果路径缓存中已经有结果，直接返回缓存的值
        if (pathCache.containsKey(xid)) {
            return pathCache.get(xid);
        }

        Integer stp = xidStamp.get(xid);
        if (stp != null && stp == stamp) {
            pathCache.put(xid, true);  // 更新路径缓存
            return true;
        }
        if (stp != null && stp < stamp) {
            pathCache.put(xid, false);  // 更新路径缓存
            return false;
        }
        xidStamp.put(xid, stamp);

        Long uid = waitU.get(xid);
        if (uid == null) {
            pathCache.put(xid, false);  // 更新路径缓存
            return false;
        }
        Long x = u2x.get(uid);
        boolean hasCycle = x != null && dfs(x);
        pathCache.put(xid, hasCycle);  // 更新路径缓存
        return hasCycle;
    }


    private void removeFromList(Map<Long, List<Long>> listMap, long uid0, long uid1) {
        List<Long> l = listMap.get(uid0);
        if (l == null) return;
        Iterator<Long> i = l.iterator();
        while (i.hasNext()) {
            long e = i.next();
            if (e == uid1) {
                i.remove();
                break;
            }
        }
        if (l.size() == 0) {
            listMap.remove(uid0);
        }
    }

    private void putIntoList(Map<Long, List<Long>> listMap, long uid0, long uid1) {
        if (!listMap.containsKey(uid0)) {
            listMap.put(uid0, new ArrayList<>());
        }
        listMap.get(uid0).add(0, uid1);
    }

    private boolean isInList(Map<Long, List<Long>> listMap, long uid0, long uid1) {
        List<Long> l = listMap.get(uid0);
        if (l == null) return false;
        Iterator<Long> i = l.iterator();
        while (i.hasNext()) {
            long e = i.next();
            if (e == uid1) {
                return true;
            }
        }
        return false;
    }

    private void startTimeoutDeadlockChecker() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(CHECK_INTERVAL_MS);
                    checkForTimeouts(TIMEOUT_THRESHOLD_MS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }

    public void checkForTimeouts(long timeout) {
        lock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            Iterator<Map.Entry<Long, Long>> iterator = waitStartTime.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, Long> entry = iterator.next();
                long xid = entry.getKey();
                long startTime = entry.getValue();
                if (currentTime - startTime >= timeout) {
                    // 超时，执行回滚操作
                    rollbackTimeoutTransaction(xid);
                    iterator.remove();  // 从等待时间记录中移除
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void rollbackTimeoutTransaction(long xid) {
        System.out.println("Transaction " + xid + " has timed out and will be rolled back.");

        // 解除事务等待的资源
        Long uid = waitU.remove(xid);
        if (uid != null) {
            removeFromList(wait, uid, xid);
        }
        // 释放所有已占用资源
        List<Long> resources = x2u.remove(xid);
        if (resources != null) {
            for (Long resource : resources) {
                selectNewXID(resource);
            }
        }
        // 通知等待该事务的其他线程
        Lock l = waitLock.remove(xid);
        if (l != null && ((ReentrantLock) l).isHeldByCurrentThread()) {
            l.unlock();
        }
    }


}
