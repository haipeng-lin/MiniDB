package com.peng.minidb.client;

import com.peng.minidb.transport.Encoder;
import com.peng.minidb.transport.Packager;
import com.peng.minidb.transport.Transporter;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * @User Administrator
 * @CreateTime 2024/8/14 22:48
 * @className com.peng.minidb.client.l3
 */
public class l3 {
    public static void main(String[] args) throws UnknownHostException, IOException {
        Socket socket = new Socket("127.0.0.1", 9999);
        Encoder e = new Encoder();
        Transporter t = new Transporter(socket);
        Packager packager = new Packager(t, e);

        Client client = new Client(packager);
        Shell shell = new Shell(client);
        shell.run();
    }
}
