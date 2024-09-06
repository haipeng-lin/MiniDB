package com.peng.minidb.backend.parser.statement;

import com.peng.minidb.backend.vm.IsolationLevel;
import lombok.ToString;

@ToString
public class Begin {
    public IsolationLevel isolationLevel;
}
