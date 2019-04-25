package mite;

import com.google.common.base.Throwables;
import lsfusion.base.DaemonThreadFactory;
import lsfusion.base.file.RawFileData;
import lsfusion.server.base.controller.manager.MonitorServer;
import lsfusion.server.base.controller.thread.ExecutorFactory;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.action.LA;
import lsfusion.server.logics.LogicsInstance;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.classes.data.file.CSVClass;
import lsfusion.server.logics.classes.data.time.DateTimeClass;

import java.io.IOException;
import java.net.*;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UDPServer extends MonitorServer {

    public final static ConcurrentHashMap<DataObject, UDPServer> runningServers = new ConcurrentHashMap<>();

    private final LogicsInstance logicsInstance;
    private final int port;
    private final LA action;
    private final DataObject serverObject;

    public String getEventName() {
        return "udp-server-daemon";
    }

    public LogicsInstance getLogicsInstance() {
        return logicsInstance;
    }

    public UDPServer(LogicsInstance logicsInstance, int port, LA action, DataObject serverObject) {
        this.logicsInstance = logicsInstance;
        this.port = port;
        this.action = action;
        this.serverObject = serverObject;
    }

    private StringBuilder text = new StringBuilder();

    private DatagramSocket serverSocket;
    protected ExecutorService daemonTasksExecutor;

    public void start() throws SocketException {
        serverSocket = new DatagramSocket(port);

        daemonTasksExecutor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("udp-server-daemon"));
        daemonTasksExecutor.submit(new Runnable() {
            public void run() {
                try {
                    ExecutorService executorService = ExecutorFactory.createMonitorThreadService(100, UDPServer.this);
                    byte[] receiveData = new byte[1024];
                    while(true)
                    {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        serverSocket.receive(receivePacket);
                        if(text.length() > 0)
                            text.append('\n');
                        text.append(DateTimeClass.instance.formatString(new Timestamp(Calendar.getInstance().getTime().getTime())));
                        String receivedString = new String(receivePacket.getData()).trim();
                        if(receivedString.isEmpty() || receivedString.charAt(0) != ';')
                            text.append(';');
                        text.append(receivedString);

                        if(text.length() > 150) {
                            final String textToProceed = text.toString();
                            executorService.submit(new Runnable() {
                                public void run() {
                                    try(DataSession session = createSession()){
                                        action.execute(session, getStack(), serverObject, new DataObject(new RawFileData(textToProceed.getBytes()), CSVClass.get()));
                                    } catch (SQLException | SQLHandledException e) {
                                        throw Throwables.propagate(e);
                                    }
                                }
                            });
                            text = new StringBuilder();
                        }

                    }
                } catch (IOException e) {
                    throw Throwables.propagate(e);
                }
            }
        });
    }

    public void stop() {
        try {
            serverSocket.close();
        } finally {
            daemonTasksExecutor.shutdown();
        }
    }
}
