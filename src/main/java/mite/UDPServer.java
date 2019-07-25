package mite;

import com.google.common.base.Throwables;
import com.google.common.io.BaseEncoding;
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

import java.io.FileWriter;
import java.io.IOException;
import java.net.*;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
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

    public Long deviceId;       // ID устройства из пакета датчика
    public String cDt;          // Дата и время измерения
    public String cMeasuring;   // Строка измерений

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
                ExecutorService executorService = ExecutorFactory.createMonitorThreadService(100, UDPServer.this);
                byte[] receiveData = new byte[1024];
                while(true)
                {
                    String receivedString = null;
                    try {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        serverSocket.receive(receivePacket);
                        if (receivePacket == null) continue;
                        receivedString = new String(receivePacket.getData()).trim();
                        if(receivedString.startsWith("b'")) {
                            receivedString = receivedString.substring(2);
                            if(receivedString.startsWith(";"))
                                receivedString = receivedString.substring(1);
                            if (!parseOld(receivedString)) continue;
                        } else {
                            print("--- new ---");
//                            if (!parseNew(receivedString)) continue;
                            continue;
                        }

                        final DataObject deviceType = getDeviceType(deviceId);
                        StringBuilder text = texts.get(deviceType);
                        if(text == null) {
                            text = new StringBuilder();
                            texts.put(deviceType, text);
                        }

                        if(text.length() > 0) text.append('\n');
                        text.append(cDt);
                        text.append(';');
                        text.append(cMeasuring);

                        if(text.length() > 5) {
                            final String textToProceed = text.toString();
//                            print("Submitting importing : " + deviceType + " " + serverObject + " " + textToProceed);
                            executorService.submit(new Runnable() {
                                public void run() {
                                    try(DataSession session = createSession()){
//                                        print("Starting importing : " + deviceType + " " + serverObject + " " + textToProceed);
                                        importAction.execute(session, getStack(), deviceType, serverObject, new DataObject(new RawFileData(textToProceed.getBytes()), CSVClass.get()));
//                                        print("Finished importing : " + deviceType + " " + serverObject + " " + textToProceed);
                                    } catch (Throwable t) {
                                        print("Import Error : " + deviceType + " " + serverObject + " " + textToProceed + "\n" + t.getMessage() + "\n" + ExceptionUtils.getExStackTrace(ExceptionUtils.getStackTrace(t), ExecutionStackAspect.getExceptionStackTrace()));
                                    }
                                }
                            });
                            print("Import, Ok : " + deviceType + " " + serverObject + " " + textToProceed);
                            texts.remove(deviceType);
                        }
                    } catch (Throwable t) {
                        print("ERROR: " + t.getMessage());
//                        print("ERROR: " + receivedString + "\n" + t.getMessage() + "\n" + ExceptionUtils.getExStackTrace(ExceptionUtils.getStackTrace(t), ExecutionStackAspect.getExceptionStackTrace()));
                    }
                }
            }
        });
    }

    // --- Обработка датчиков, начинается с x3b (;)
    private boolean parseOld(String cPacket) {
        deviceId = Long.parseLong(cPacket.substring(0,cPacket.indexOf(';')));
        cDt = DateTimeClass.instance.formatString(new Timestamp(Calendar.getInstance().getTime().getTime()));
        cMeasuring = cPacket;
        return true;
    }

    // --- Обработка новых типов датчиков
    private boolean parseNew(String cPacket) {
        Integer nt;
        deviceId = Long.parseLong(revers(cPacket,8,12),16);
        nt = Integer.parseInt(revers(cPacket,2,4),16);  // тип датчика
        cDt = getDTCounter(revers(cPacket,12,16));
        cMeasuring = deviceId.toString() + ";";
        if ((nt == 1) || (nt == 2)) {
            cMeasuring += getFloat(revers(cPacket,16,20)) + ";";
            cMeasuring += getFloat(revers(cPacket,20,24)) + ";";
            cMeasuring += getFloat(revers(cPacket,24,28)) + ";";
            cMeasuring += getVoltage(revers(cPacket,28,32));
        } else if ((nt == 3) || (nt == 4)) {
            cMeasuring += getFloat(revers(cPacket,16,20)) + ";";
            cMeasuring += getVoltage(revers(cPacket,20,24));
        } else if ((nt == 5) || (nt == 6)) {
            cMeasuring += getFloat(revers(cPacket,16,20)) + ";";
            cMeasuring += getVoltage(revers(cPacket,20,24));
        } else return false;
        return true;
    }

    // Переставляем байты
    private String revers(String cb,int n1,int n2) {
        String c1;
        StringBuilder cRet = new StringBuilder();
        cb = cb.substring(n1*2,n2*2);
        for (int i=cb.length();i>0;i-=2) {
            c1 = cb.substring(i-2, i);
            cRet.append(c1);
        }
        return cRet.toString();
    }

    // Получаем символьную строку float
    private String getFloat(String cNum) {
        Long num = Long.parseLong(cNum, 16);
        Float fNum = Float.intBitsToFloat(num.intValue());
        return  fNum.toString();
    }

    // Получаем символьную строку float
    private String getVoltage(String cNum) {
        Double dNum = ((Long.parseLong(cNum,16) * 1.1)/1023) * 4;
        DecimalFormat df2 = new DecimalFormat("#.##");
        return df2.format(dNum).replace(",",".");
    }

    // Получаем дату-время снятия измерений
    private String getDTCounter(String cSecond) {
        int nt = Integer.parseInt(cSecond,16);
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        Calendar dt = Calendar.getInstance();
        dt.set(2019, Calendar.MAY, 15,0,0,nt);
        return dayFormat.format(dt.getTime());
    }

    // для отладки, выводит в консоль и пишет лог в корень проекта
    private void print(String cMsg) {
        Date date = new Date();
        SimpleDateFormat fDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        if (cMsg.length() > 0) cMsg = ", " + cMsg;
        cMsg = fDate.format(date) + cMsg;
        System.out.println("UDP: " + cMsg);
    }

    public void stop() {
        try {
            serverSocket.close();
        } finally {
            daemonTasksExecutor.shutdown();
        }
    }
}
