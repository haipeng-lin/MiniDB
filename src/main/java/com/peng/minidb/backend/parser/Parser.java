package com.peng.minidb.backend.parser;

import java.util.ArrayList;
import java.util.List;

import com.peng.minidb.backend.parser.statement.*;
import com.peng.minidb.backend.parser.statement.DeleteObj;
import com.peng.minidb.backend.vm.IsolationLevel;
import com.peng.minidb.common.Error;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.ItemsListVisitorAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.ShowStatement;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.Index;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectVisitorAdapter;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;

public class Parser {
    public static Object Parse(byte[] statement) throws Exception {
        String sql = new String(statement).trim();

        if (sql.toUpperCase().startsWith("BEGIN")) {
            return parseBegin(sql);
        } else if (sql.equalsIgnoreCase("ABORT") || sql.equalsIgnoreCase("ABORT;")) {
            return parseAbort();
        } else if (sql.equalsIgnoreCase("COMMIT") || sql.equalsIgnoreCase("COMMIT;")) {
            return parseCommit();
        }

        Statement parsedStatement;
        try {
            parsedStatement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            String message = e.getMessage();
            String result = null;
            int startIndex = message.indexOf("Encountered unexpected token:");
            int endIndex = message.indexOf("\nWas expecting one of:");
            if (startIndex != -1 && endIndex != -1) {
                result = message.substring(startIndex, endIndex);
            }
            throw new RuntimeException("Invalid statement: " + result == null ? sql : result, e);
        }

        if (parsedStatement instanceof CreateTable) {
            return parseCreate((CreateTable) parsedStatement);
        } else if (parsedStatement instanceof Select) {
            return parseSelect((Select) parsedStatement);
        } else if (parsedStatement instanceof Insert) {
            return parseInsert((Insert) parsedStatement);
        } else if (parsedStatement instanceof Update) {
            return parseUpdate((Update) parsedStatement);
        } else if (parsedStatement instanceof Delete) {
            return parseDelete((Delete) parsedStatement);
        } else if (parsedStatement instanceof Drop) {
            return parseDrop((Drop) parsedStatement);
        } else if (parsedStatement instanceof ShowStatement) {
            return parseShow((ShowStatement) parsedStatement);
        } else {
            throw new RuntimeException("Unsupported statement: " + sql);
        }
    }

    private static SelectObj parseSelect(Select select) {
        SelectObj read = new SelectObj();
        List<String> fields = new ArrayList<>();
        List<String> orderFields = new ArrayList<>();
        List<Boolean> orderAscFields = new ArrayList<>();

        select.getSelectBody().accept(new SelectVisitorAdapter() {
            @Override
            public void visit(PlainSelect plainSelect) {
                // 处理 SELECT 字段
                plainSelect.getSelectItems().forEach(selectItem -> {
                    String fieldName = selectItem instanceof SelectExpressionItem
                            ? ((SelectExpressionItem) selectItem).getExpression().toString()
                            : selectItem.toString();
                    fields.add(fieldName);
                });

                // 处理 ORDER BY 字段
                if (plainSelect.getOrderByElements() != null) {
                    plainSelect.getOrderByElements().forEach(orderByElement -> {
                        String orderField = orderByElement.getExpression().toString();
                        orderFields.add(orderField);
                        orderAscFields.add(orderByElement.isAsc());
                    });
                }

                // 设置查询字段和表名
                read.fields = fields.toArray(new String[0]);
                read.tableName = plainSelect.getFromItem().toString();

                // 初始化 ORDER BY 表达式
                read.orderByExpression = new OrderByExpression();
                // 设置 ORDER BY 表达式
                read.orderByExpression.fields = orderFields.toArray(new String[0]);
                read.orderByExpression.order = orderAscFields.toArray(new Boolean[0]);

                // 设置 WHERE 子句
                if (plainSelect.getWhere() != null) {
                    read.where = parseWhere(plainSelect.getWhere().toString());
                }
            }
        });

        return read;
    }

    private static Show parseShow(ShowStatement showStatement) throws Exception {
        Show show = new Show();
        String name = showStatement.getName();
        if (name.equalsIgnoreCase("table")) {
            show.isTable = true;
        }
        show.tableName = name;
        return show;
    }

    private static UpdateObj parseUpdate(Update updateStmt) {
        UpdateObj updateObj = new UpdateObj();
        List<String> fieldNames = new ArrayList<>();
        List<String> fieldValues = new ArrayList<>();

        updateObj.tableName = updateStmt.getTable().getName();
        for (UpdateSet updateSet : updateStmt.getUpdateSets()) {
            for (Column column : updateSet.getColumns()) {
                // 添加字段名
                fieldNames.add(column.getColumnName());
            }
            for (Expression expression : updateSet.getExpressions()) {
                // 获取value值
                fieldValues.add(stripQuotes(expression.toString()));
            }
        }
        updateObj.fieldName = fieldNames.toArray(new String[0]);
        updateObj.value = fieldValues.toArray(new String[0]);

        // 解析 WHERE 子句
        if (updateStmt.getWhere() != null) {
            updateObj.where = parseWhere(updateStmt.getWhere().toString());
        }

        return updateObj;
    }

    private static DeleteObj parseDelete(Delete deleteStmt) {
        DeleteObj deleteObj = new DeleteObj();
        // 获取表名
        deleteObj.tableName = deleteStmt.getTable().getName();
        // 解析 WHERE 子句
        if (deleteStmt.getWhere() != null) {
            deleteObj.where = parseWhere(deleteStmt.getWhere().toString());
        }

        return deleteObj;
    }


    private static InsertObj parseInsert(Insert insertStmt) throws Exception {
        InsertObj insertObj = new InsertObj();
        insertObj.tableName = insertStmt.getTable().getName();
        List<String> values = new ArrayList<>();
        List<String> columnNames = new ArrayList<>();

        // 获取列名
        if (insertStmt.getColumns() != null && !insertStmt.getColumns().isEmpty()) {
            insertStmt.getColumns().forEach(column -> columnNames.add(column.getColumnName()));
        }

        // 获取值
        insertStmt.getItemsList().accept(new ItemsListVisitorAdapter() {
            @Override
            public void visit(ExpressionList expressionList) {
                expressionList.getExpressions().forEach(expression -> {
                    // 将表达式转换为字符串并去掉单引号
                    String value = expression.toString().replace("'", "");
                    // 去掉前后的括号
                    value = value.replaceAll("^\\(|\\)$", "");
                    values.add(value);
                });
            }
        });

        // 检查列名与值的数量是否匹配
        if (!columnNames.isEmpty() && columnNames.size() != values.size()) {
            throw new Exception("Column count does not match value count.");
        }

        insertObj.fields = columnNames.toArray(new String[0]);
        insertObj.values = values.toArray(new String[0]);

        return insertObj;
    }

    private static Where parseWhere(String whereClause) {
        Where where = new Where();

        // 使用正则表达式进行解析，支持空格和引号分隔
        String[] parts = whereClause.split("\\s+(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

        if (parts.length >= 3) {
            SingleExpression exp1 = new SingleExpression();
            exp1.field = parts[0];
            exp1.compareOp = parts[1];
            exp1.value = stripQuotes(parts[2]); // 去掉引号
            where.singleExp1 = exp1;

            if (parts.length > 3) {
                // 检查逻辑操作符的有效性
                String logicOp = parts[3].toLowerCase();
                if (logicOp.equals("and") || logicOp.equals("or")) {
                    where.logicOp = logicOp;
                } else {
                    throw new IllegalArgumentException("Invalid logical operation: " + logicOp);
                }

                // 检查第二个条件的完整性
                if (parts.length == 7) {
                    SingleExpression exp2 = new SingleExpression();
                    exp2.field = parts[4];
                    exp2.compareOp = parts[5];
                    exp2.value = stripQuotes(parts[6]); // 去掉引号
                    where.singleExp2 = exp2;
                } else {
                    throw new IllegalArgumentException("Invalid or incomplete second condition in WHERE clause.");
                }
            }
        } else {
            throw new IllegalArgumentException("Incomplete WHERE clause.");
        }

        // 额外检查：确保值部分格式正确
        validateSingleExpression(where.singleExp1);
        if (where.singleExp2 != null) {
            validateSingleExpression(where.singleExp2);
        }

        return where;
    }

    /**
     * 去除单引号或双引号
     */
    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1); // 去掉首尾的引号
        }
        return value;
    }


    private static void validateSingleExpression(SingleExpression exp) {
        // 检查比较操作符是否有效
        if (!isValidOperator(exp.compareOp)) {
            throw new IllegalArgumentException("Invalid comparison operator: " + exp.compareOp);
        }

        // 检查值部分是否合理，例如不允许多个不合法的数值
        // 例如 name = 20 30 40 是不合法的，这里可以添加具体的值验证逻辑
        if (exp.value.split("\\s+").length > 1 && !exp.compareOp.equals("in")) {
            throw new IllegalArgumentException("Invalid value format: " + exp.value);
        }
    }

    private static boolean isValidOperator(String operator) {
        return operator.equals("=") || operator.equals(">") || operator.equals("<") ||
                operator.equals(">=") || operator.equals("<=") || operator.equals("!=") ||
                operator.equalsIgnoreCase("in") || operator.equalsIgnoreCase("like");
    }

    private static Create parseCreate(CreateTable createTable) {
        Create create = new Create();
        create.tableName = createTable.getTable().getName();
        List<String> fieldNames = new ArrayList<>();
        List<String> fieldTypes = new ArrayList<>();
        List<String> indexes = new ArrayList<>();
        List<String> autoIncrement = new ArrayList<>();
        List<String> notNull = new ArrayList<>();
        List<String> unique = new ArrayList<>();


        for (ColumnDefinition columnDefinition : createTable.getColumnDefinitions()) {
            fieldNames.add(columnDefinition.getColumnName());
            fieldTypes.add(columnDefinition.getColDataType().toString());

            if (columnDefinition.getColumnSpecs() != null) {
                for (String columnSpec : columnDefinition.getColumnSpecs()) {
                    if (columnSpec.equalsIgnoreCase("PRIMARY")) {
                        create.primaryKey = columnDefinition.getColumnName();
                    } else if (columnSpec.equalsIgnoreCase("AUTO_INCREMENT")) {
                        autoIncrement.add(columnDefinition.getColumnName());
                    } else if (columnSpec.equalsIgnoreCase("NOT")) {
                        notNull.add(columnDefinition.getColumnName());
                    } else if (columnSpec.equalsIgnoreCase("UNIQUE")) {
                        unique.add(columnDefinition.getColumnName());
                    }
                }
            }
        }

        if (createTable.getIndexes() != null) {
            for (Index index : createTable.getIndexes()) {
                // 只处理单列索引
                if (index.getColumnsNames().size() == 1) {
                    indexes.add(index.getColumnsNames().get(0));
                }
            }
        }

        create.fieldName = fieldNames.toArray(new String[0]);
        create.fieldType = fieldTypes.toArray(new String[0]);
        create.index = indexes.toArray(new String[0]);
        create.autoIncrement = autoIncrement.toArray(new String[0]);
        create.notNull = notNull.toArray(new String[0]);
        create.unique = unique.toArray(new String[0]);

        return create;
    }

    private static DropObj parseDrop(Drop dropStmt) {
        DropObj dropObj = new DropObj();
        if (dropStmt.getType().equalsIgnoreCase("table")) {
            dropObj.tableName = dropStmt.getName().getName();
        }
        return dropObj;
    }

    private static Abort parseAbort() {
        return new Abort();
    }

    private static Commit parseCommit() {
        return new Commit();
    }

    private static Begin parseBegin(String sql) throws Exception {
        sql = sql.trim();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }

        Tokenizer tokenizer = new Tokenizer(sql.getBytes());
        tokenizer.peek();
        tokenizer.pop();

        String isolation = tokenizer.peek();
        Begin begin = new Begin();
        if ("".equals(isolation)) {
            begin.isolationLevel = IsolationLevel.READ_COMMITTED;
            return begin;
        }
        if (!"isolation".equalsIgnoreCase(isolation)) {
            throw Error.InvalidCommandException;
        }

        tokenizer.pop();
        String level = tokenizer.peek();
        if (!"level".equalsIgnoreCase(level)) {
            throw Error.InvalidCommandException;
        }
        tokenizer.pop();

        String tmp1 = tokenizer.peek();
        if ("read".equalsIgnoreCase(tmp1)) {
            tokenizer.pop();
            String tmp2 = tokenizer.peek();
            if ("committed".equalsIgnoreCase(tmp2)) {
                tokenizer.pop();
                if (!"".equals(tokenizer.peek())) {
                    throw Error.InvalidCommandException;
                }
                begin.isolationLevel = IsolationLevel.READ_COMMITTED;
                return begin;
            } else if ("uncommitted".equalsIgnoreCase(tmp2)) {
                tokenizer.pop();
                if (!"".equals(tokenizer.peek())) {
                    throw Error.InvalidCommandException;
                }
                begin.isolationLevel = IsolationLevel.READ_UNCOMMITTED;
                return begin;
            } else {
                throw Error.InvalidCommandException;
            }
        } else if ("repeatable".equalsIgnoreCase(tmp1)) {
            tokenizer.pop();
            String tmp2 = tokenizer.peek();
            if ("read".equals(tmp2)) {
                begin.isolationLevel = IsolationLevel.REPEATABLE_READ;
                tokenizer.pop();
                if (!"".equals(tokenizer.peek())) {
                    throw Error.InvalidCommandException;
                }
                return begin;
            } else {
                throw Error.InvalidCommandException;
            }
        } else if ("serializable".equalsIgnoreCase(tmp1)) {
            tokenizer.pop();
            if (!"".equals(tokenizer.peek())) {
                throw Error.InvalidCommandException;
            }
            begin.isolationLevel = IsolationLevel.SERIALIZABLE;
            return begin;
        } else {
            throw Error.InvalidCommandException;
        }
    }


    private static boolean isName(String name) {
        return name.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    private static boolean isCmpOp(String op) {
        return ("=".equals(op) || ">".equals(op) || "<".equals(op));
    }

    private static boolean isLogicOp(String op) {
        return ("and".equals(op) || "or".equals(op));
    }
}
