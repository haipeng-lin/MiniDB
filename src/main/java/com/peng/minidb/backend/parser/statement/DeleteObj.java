package com.peng.minidb.backend.parser.statement;

import lombok.ToString;

@ToString
public class DeleteObj {
    public String tableName;
    public Where where;
}
