package mite;

import com.google.common.base.Throwables;
import com.google.common.io.BaseEncoding;
import lsfusion.base.ExceptionUtils;
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
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class UDPServer extends MonitorServer {

    public final static ConcurrentHashMap<DataObject, UDPServer> runningServers = new ConcurrentHashMap<>();

    public final static ConcurrentHashMap<Long, DataObject> deviceTypes = new ConcurrentHashMap<>();

    private final LogicsInstance logicsInstance;
    private final int port;
    private final LA importAction;
    private final LP deviceType;
    private final DataObject unknownDevice;
    private final DataObject serverObject;

    private Long deviceId;          // ID устройства из пакета датчика
    private String cDt;             // Дата и время измерения
    private String cMeasuring;      // Строка измерений
    public  Integer qps;            // Количество пакетов для записи, устанавливается перед стартом сервера
    private Integer nQps;           // Текущий счетчик пакетов
    private Boolean lRead;          // Флаг цикла потока чтения UDP

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

    private Map<DataObject, StringBuilder> texts = new HashMap<>(); // Map: устройство, блок пакетов

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
                byte[] receiveData = new byte[1024];
                nQps = 0;
                lRead = true;
                while(lRead)
                {
                    String receivedString;
                    try {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        serverSocket.receive(receivePacket);
                        if (receivePacket == null) continue;
                        String cp1 = receivePacket.getAddress().toString();
                        String cp2 = Integer.toString(receivePacket.getPort());
                        receivedString = new String(receivePacket.getData()).trim();
                        if(receivedString.startsWith("b'")) {
                            print("OLD: " + receivedString + ", IP: " + cp1 + " : " + cp2);
                            receivedString = receivedString.substring(2);
                            if(receivedString.startsWith(";"))
                                receivedString = receivedString.substring(1);
                            if (!parseOld(receivedString)) continue;
                        } else {
                            receivedString = BaseEncoding.base16().encode(receiveData);
                            print("NEW: " + receivedString.substring(0,100) + ", IP: " + cp1 + " : " + cp2);   // наверное max = 32 байта * 2
                            if (chkLabelTime(receivedString,receivePacket)) continue;
                            if (!parseNew(receivedString)) continue;
                        }

                        DataObject deviceType = getDeviceType(deviceId);
                        StringBuilder text = texts.get(deviceType);
                        // дозаполняем текст импорта по своему устройству
                        if(text == null) {
                            text = new StringBuilder();
                            texts.put(deviceType, text);
                        }

                        if(text.length() > 0) text.append('\n');
                        text.append(cDt);
                        text.append(';');
                        text.append(cMeasuring);

                        nQps += 1;
                        if (nQps >= qps) nQps = 0; else continue;

                        // импортируем устройства в csv
                        importCSV();
                    } catch (Throwable t) {
                        print("ERROR: " + t.getMessage());
                    }
                }
            }
        });
    }

    // --- Импорт в CSV
    private void importCSV() {
        ExecutorService executorService = ExecutorFactory.createMonitorThreadService(100, UDPServer.this);
        for (final DataObject deviceType : texts.keySet() ) {
            final String textToProceed = texts.get(deviceType).toString();
            executorService.submit(new Runnable() {
                public void run() {
                    print("\n--- IMPORT: " + deviceType.toString() + ":\n" + textToProceed);
                    try(DataSession session = createSession()){
                        importAction.execute(session, getStack(), deviceType, serverObject, new DataObject(new RawFileData(textToProceed.getBytes()), CSVClass.get()));
                    } catch (Throwable t) {
                        print("ERROR, IMPORT: "+ textToProceed + "\n" + t.getMessage() + "\n" + ExceptionUtils.getExStackTrace(ExceptionUtils.getStackTrace(t), ExecutionStackAspect.getExceptionStackTrace()));
                    }
                }
            });
        }
        texts.clear();
    }

    // --- Обработка датчиков, начинается с b'(;)
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
        cMeasuring += getFloat(revers(cPacket,16,20)) + ";";
        cMeasuring += getFloat(revers(cPacket,20,24)) + ";";
        cMeasuring += getFloat(revers(cPacket,24,28)) + ";";
        cMeasuring += getVoltage(revers(cPacket,28,32));
        print("TYPE " + nt.toString());
        return true;
    }

    // Проверка, что идет запрос времени
    private boolean chkLabelTime(String cPacket,DatagramPacket dPacket) {
        String cLab = revers(cPacket,0,2);
        if (!cLab.equals("FFFF")) return false;
        long nId = Long.parseLong(revers(cPacket,8,12),16);
        String cId = Long.toString(nId);
        byte[] data = new byte[1];
        data[0] = (byte) 255;
        try {
            print("LABEL TIME, " + cId + "... " + dPacket.getAddress().toString() + ":" + Integer.toString(dPacket.getPort()));
            DatagramPacket dp = new DatagramPacket(data, data.length, dPacket.getAddress(), 20001);
            DatagramSocket ds = new DatagramSocket();
            ds.send(dp);
            ds.close();
            print("LABEL TIME, OK " + cId);
        } catch (IOException e) {
            print("ERROR LABEL TIME: " + e.getMessage());
        }
        return true;
    }


    // Переставляем пары байты
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

    // Получаем значение напряжения батареи
    private String getVoltage(String cNum) {
//        print("ADC: " + cNum); // Отладка: Значение АЦП для расчета напряжения батареи
        Double dNum = ((Long.parseLong(cNum,16) * 1.1)/1023) * 3.7;
        DecimalFormat df2 = new DecimalFormat("#.##");
        return df2.format(dNum).replace(",",".");
    }

    // Получаем дату-время снятия измерений: дата 15.05.2019 00:00:00 + пришедщее текущие значение счетчика
    private String getDTCounter(String cSecond) {
        int nt = Integer.parseInt(cSecond,16);
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        Calendar dt = Calendar.getInstance();
        dt.set(2019, Calendar.MAY, 15,0,0,nt);
        return dayFormat.format(dt.getTime());
    }

    // для отладки, выводит в консоль с признаком UDP дата время текст
    private void print(String cMsg) {
        Date date = new Date();
        SimpleDateFormat fDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        if (cMsg.length() > 0) cMsg = ", " + cMsg;
        cMsg = fDate.format(date) + cMsg;
        System.out.println("UDP: " + cMsg);
    }

    // остановка сервера
    public void stop() {
        lRead = false;
        importCSV();        // может что-то осталось в буфере
        try {
            serverSocket.close();
        } finally {
            daemonTasksExecutor.shutdown();
        }
    }
}
