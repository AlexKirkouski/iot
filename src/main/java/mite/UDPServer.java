package mite;

import com.google.common.base.Throwables;
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
import lsfusion.server.physics.admin.log.ServerLoggers;

import java.net.*;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    private Long deviceId;              // ID устройства из пакета датчика
    private String cDt;                 // Дата и время измерения
    private String cMeasuring;          // Строка измерений
    public  Integer qps;                // Количество пакетов для записи, устанавливается перед стартом сервера
    private Integer nQps;               // Текущий счетчик пакетов
    private Boolean lRead;              // Флаг цикла потока чтения UDP
    public  Integer prnConsole = 0;     // печать отладочной информации в консоль

    public static void print(String cPref,String cMsg) {
        Date date = new Date();
        SimpleDateFormat fDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        if (cMsg.length() > 0) {
            ServerLoggers.systemLogger.info(fDate.format(date) + ", " + cPref + ", " + cMsg);
        } else {
            ServerLoggers.systemLogger.info(fDate.format(date) + ", " + cPref);
        }
    }

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
//                byte[] receiveData = new byte[1024];
                nQps = 0;
                lRead = true;
                while(lRead)
                {
                    String receivedString;
                    try {
                        byte[] receiveData = new byte[1024];
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        serverSocket.receive(receivePacket);
                        if (receivePacket == null) continue;
                        receivedString = new String(receivePacket.getData()).trim();
                        int nb = 0;
                        if(receivedString.startsWith("b'")) {
                            print("OLD PACKET: " + receivedString);
                            nb = 2;
                        } else if (receivedString.startsWith("b\"b'")) {
                            print("NEW PACKET: " + receivedString);
                            nb = 4;
                        } else {
                            print("??? PACKET: " + receivedString);
                            continue;
                        }
                        receivedString = receivedString.substring(nb);
                        receivedString = receivedString.substring(0,receivedString.lastIndexOf(";")+1);
                        if(receivedString.startsWith(";"))
                            receivedString = receivedString.substring(1);
                        if (!parsePacket(receivedString, nb)) continue;

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
    private boolean parsePacket(String cPacket, int nb) {
        deviceId = Long.parseLong(cPacket.substring(0,cPacket.indexOf(';')));
        if (nb == 2) {
            cDt = DateTimeClass.instance.formatString(LocalDateTime.now());
            cMeasuring = cPacket;
        } else {
            String [] cSplit = cPacket.split(";");
            cMeasuring = "";
            for (int i=0; i < cSplit.length - 1; i++) {
                cMeasuring += cSplit[i] + ";";
            }
            cDt = DateTimeClass.instance.formatString(LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(cSplit[cSplit.length - 1])), ZoneId.systemDefault()));
        }
        return true;
    }

    // для отладки, выводит в консоль с признаком UDP дата время текст
    private void print(String cMsg) {
        if (prnConsole > 0) {
            print("UDP", cMsg);
        }
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