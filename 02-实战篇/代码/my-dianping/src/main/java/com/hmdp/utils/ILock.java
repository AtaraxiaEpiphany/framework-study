package com.hmdp.utils;

public interface ILock {
    /**
     * try to lock with {@code timeSec} seconds.
     *
     * @param timeSec Expire time.The lock is automatically released.
     * @return Return true if lock with {@code timeSec} seconds.
     */
    boolean tryLock(long timeSec);

    /**
     * Release the lock.
     */
    void unLock();
}
