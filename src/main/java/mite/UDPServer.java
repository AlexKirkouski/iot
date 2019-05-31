package mite;

import com.google.common.base.Throwables;
import lsfusion.base.DaemonThreadFactory;
import lsfusion.base.ExceptionUtils;
import lsfusion.base.Pair;
import lsfusion.base.file.RawFileData;
import lsfusion.server.base.controller.manager.MonitorServer;
import lsfusion.server.base.controller.stack.ExecutionStackAspect;
import lsfusion.server.base.controller.thread.ExecutorFactory;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.action.LA;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.LogicsInstance;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.classes.data.file.CSVClass;
import lsfusion.server.logics.classes.data.time.DateTimeClass;

import java.io.IOException;
import java.net.*;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UDPServer extends MonitorServer {
    
    public final static ConcurrentHashMap<DataObject, UDPServer> runningServers = new ConcurrentHashMap<>();
    
    public final static ConcurrentHashMap<Long, DataObject> deviceTypes = new ConcurrentHashMap<>();

    private final LogicsInstance logicsInstance;
    private final int port;
    private final LA importAction;
    private final LP deviceType;
    private final DataObject unknownDevice;
    private final DataObject serverObject;

    public String getEventName() {
        return "udp-server-daemon";
    }

    public LogicsInstance getLogicsInstance() {
        return logicsInstance;
    }

    public UDPServer(LogicsInstance logicsInstance, int port, LA importAction, LP deviceType, DataObject unknownDevice, DataObject serverObject) {
        this.logicsInstance = logicsInstance;
        this.port = port;
        this.importAction = importAction;
        this.deviceType = deviceType;
        this.unknownDevice = unknownDevice;
        this.serverObject = serverObject;
    }

    private Map<DataObject, StringBuilder> texts = new HashMap<>();

    private DatagramSocket serverSocket;
    protected ExecutorService daemonTasksExecutor;
    
    public static void dropCaches() {
        deviceTypes.clear();
    }
    
    public DataObject getDeviceType(Long device) {
        DataObject deviceType = deviceTypes.get(device);
        if(deviceType != null)
            return deviceType;
        
        try(DataSession session = createSession()){
            ObjectValue value = this.deviceType.readClasses(session, new DataObject(device));
            if(value instanceof DataObject)
                deviceType = ((DataObject) value);
            else
                deviceType = unknownDevice;
        } catch (SQLException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }
        deviceTypes.put(device, deviceType);
        return deviceType;
    }

    public void start() throws SocketException {
        serverSocket = new DatagramSocket(port);

        daemonTasksExecutor = ExecutorFactory.createMonitorScheduledThreadService(0, this);
        daemonTasksExecutor.submit(new Runnable() {
            public void run() {
                try {
                    ExecutorService executorService = ExecutorFactory.createMonitorThreadService(100, UDPServer.this);
                    byte[] receiveData = new byte[1024];
                    while(true)
                    {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        serverSocket.receive(receivePacket);
                        String receivedString = new String(receivePacket.getData()).trim();
                        if(receivedString.startsWith("b'"))
                            receivedString = receivedString.substring(2);
                        if(receivedString.startsWith(";"))
                            receivedString = receivedString.substring(1);
                        
                        Long deviceId = Long.parseLong(receivedString.substring(0,  receivedString.indexOf(';')));
                        final DataObject deviceType = getDeviceType(deviceId);

                        StringBuilder text = texts.get(deviceType);
                        if(text == null) {
                            text = new StringBuilder();
                            texts.put(deviceType, text);
                        }

                        if(text.length() > 0)
                            text.append('\n');
                        text.append(DateTimeClass.instance.formatString(new Timestamp(Calendar.getInstance().getTime().getTime())));
                        text.append(';');
                        text.append(receivedString);

                        if(text.length() > 5) {
                            final String textToProceed = text.toString();
                            executorService.submit(new Runnable() {
                                public void run() {
                                    try(DataSession session = createSession()){
                                        System.out.println("Starting importing : " + deviceType + " " + serverObject + " " + textToProceed);
                                        importAction.execute(session, getStack(), deviceType, serverObject, new DataObject(new RawFileData(textToProceed.getBytes()), CSVClass.get()));
                                        System.out.println("Finished importing : " + deviceType + " " + serverObject + " " + textToProceed);
                                    } catch (Throwable t) {
                                        System.out.println("Error while importing : " + deviceType + " " + serverObject + " " + textToProceed + "\n" + t.getMessage() + "\n" + ExceptionUtils.getExStackTrace(ExceptionUtils.getStackTrace(t), ExecutionStackAspect.getExceptionStackTrace()));
                                        throw Throwables.propagate(t);
                                    }
                                }
                            });
                            texts.remove(deviceType);
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
