package com.peng.minidb.backend.vm;

/**
 * @User Administrator
 * @CreateTime 2024/8/8 22:26
 * @className com.peng.minidb.backend.vm.IsolationLevel
 */
public enum IsolationLevel {
    READ_COMMITTED,
    READ_UNCOMMITTED,
    REPEATABLE_READ,
    SERIALIZABLE
}
