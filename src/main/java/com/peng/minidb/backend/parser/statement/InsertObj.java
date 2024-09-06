package com.peng.minidb.backend.parser.statement;

import lombok.ToString;

@ToString
public class InsertObj {
    public String tableName;
    public String[] fields;
    public String[] values;
}
