package com.peng.minidb.backend.parser.statement;

import lombok.ToString;

@ToString
public class SingleExpression {
    public String field;
    public String compareOp;
    public String value;
}
