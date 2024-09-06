package com.peng.minidb.backend.parser.statement;

import lombok.ToString;

@ToString
public class UpdateObj {
    public String tableName;
    public String[] fieldName;
    public String[] value;
    public Where where;
}
