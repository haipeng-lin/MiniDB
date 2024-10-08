package com.peng.minidb.backend.tbm;

import com.peng.minidb.backend.dm.DataManager;
import com.peng.minidb.backend.parser.statement.*;
import com.peng.minidb.backend.parser.statement.DeleteObj;
import com.peng.minidb.backend.utils.Parser;
import com.peng.minidb.backend.vm.VersionManager;

public interface TableManager {
    BeginRes begin(Begin begin);
    byte[] commit(long xid) throws Exception;
    byte[] abort(long xid);

    byte[] show(long xid, Show stat);
    byte[] create(long xid, Create create) throws Exception;
    byte[] drop(long xid, DropObj stat) throws Exception;

    byte[] insert(long xid, InsertObj insertObj) throws Exception;
    byte[] read(long xid, SelectObj selectObj) throws Exception;
    byte[] update(long xid, UpdateObj updateObj) throws Exception;
    byte[] delete(long xid, DeleteObj deleteObj) throws Exception;

    // void close();

    public static TableManager create(String path, VersionManager vm, DataManager dm) {
        Booter booter = Booter.create(path);
        booter.update(Parser.long2Byte(0));
        return new TableManagerImpl(vm, dm, booter);
    }

    public static TableManager open(String path, VersionManager vm, DataManager dm) {
        Booter booter = Booter.open(path);
        return new TableManagerImpl(vm, dm, booter);
    }

}
