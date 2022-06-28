package mite;

import com.google.common.base.Throwables;
import lsfusion.base.BaseUtils;
import lsfusion.base.ExceptionUtils;
import lsfusion.base.file.RawFileData;
import lsfusion.server.base.controller.manager.MonitorServer;
import lsfusion.server.base.controller.stack.ExecutionStackAspect;
import lsfusion.server.base.controller.thread.ExecutorFactory;
import lsfusion.server.base.task.TaskRunner;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.action.LA;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.LogicsInstance;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.classes.data.file.CSVClass;
import lsfusion.server.logics.classes.data.time.ZDateTimeClass;
import lsfusion.server.physics.admin.log.ServerLoggers;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;
import java.util.zip.Checksum;


public class UDPServer extends MonitorServer {

    public final static ConcurrentHashMap<DataObject, UDPServer> runningServers = new ConcurrentHashMap<>();

    public final static ConcurrentHashMap<Long, DataObject> deviceTypes = new ConcurrentHashMap<>();

    private final LogicsInstance logicsInstance;
    private final int port;
    private final LA importAction;
    private final LA writeSimIDAction;
    private final LP deviceType;
    private final DataObject unknownDevice;
    private final DataObject serverObject;

    private Long deviceId;              // ID устройства из пакета датчика
    private String cDt;                 // Дата и время измерения
    private String cMeasuring;          // Строка измерений
    public  Integer qps;                // Количество пакетов для записи, устанавливается перед стартом сервера
    public  Integer threads;
    public  Integer maxDelay;           // in seconds
    private Integer nQps;               // Текущий счетчик пакетов
    private long    lastTimeStamp;
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

    public UDPServer(LogicsInstance logicsInstance, int port, LA importAction, LP deviceType, LA writeSimIDAction, DataObject unknownDevice, DataObject serverObject) {
        this.logicsInstance = logicsInstance;
        this.port = port;
        this.importAction = importAction;
        this.writeSimIDAction = writeSimIDAction;
        this.deviceType = deviceType;
        this.unknownDevice = unknownDevice;
        this.serverObject = serverObject;
    }

    private Map<DataObject, StringBuilder> texts = new HashMap<>(); // Map: устройство, блок пакетов
    private List<Runnable> runnables = new ArrayList<>();

    private DatagramSocket serverSocket;
    protected ExecutorService daemonTasksExecutor;
    protected ScheduledExecutorService scheduledTasksExecutor;
    protected ExecutorService importTasksExecutor;

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

//    private void writeTimestamp(ByteBuffer out, LocalDateTime timestamp) {
//    }
//    private LocalDateTime getTimestamp(ByteBuffer in) {
//    }

//    private void receiveMeasurements(ByteBuffer buffer) {
    private void receiveMeasurements(DatagramPacket receivePacket, int serialId, JSONObject jsonObject) {
        long flags = jsonObject.getInt("flags"); // getUnsignedInt(buffer);
        LocalDateTime dt = LocalDateTime.parse(jsonObject.getString("time"), formatter);// getTimestamp(buffer);
        float temp = jsonObject.getFloat("temp"); // buffer.getFloat();
        float humidity = jsonObject.getFloat("hum"); // buffer.getFloat();
        float batt = jsonObject.getFloat("bat"); // buffer.getFloat();

        cDt = ZDateTimeClass.instance.formatString(dt.atZone(ZoneId.systemDefault()).toInstant());
        cMeasuring = deviceId + ";" + temp + ";" + humidity + ';' + batt;
    }

    private void sendTsync(DatagramPacket request, long serialId) throws IOException {
        JSONObject out = new JSONObject();
//        ByteBuffer out = ByteBuffer.allocate(2+4+4+7+4);
//        writeUnsignedShort(out, 0xEA01);
        out.put("msgid", 0xEA01);
//        writeUnsignedInt(out, serialId);
        out.put("serial", serialId);
//        writeUnsignedInt(out, 0);
        out.put("flags", 0);
//        writeTimestamp(out, LocalDateTime.now());
        out.put("tsync", LocalDateTime.now().format(formatter));

        sendResponseWithCRC(request, out, true);
    }

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");

    private void sendAck(DatagramPacket request, long serialId, boolean immediately) throws IOException {
        JSONObject out = new JSONObject();
//        ByteBuffer out = ByteBuffer.allocate(2+4+4+4);
//        writeUnsignedShort(out, 0xAC01);
        out.put("msgid", 0xAC01);
//        writeUnsignedInt(out, serialId);
        out.put("serial", serialId);
//        writeUnsignedInt(out, 0);

        int flags = 0;
        UpdateDeviceSettingsAction.Values values;
        if((values = UpdateDeviceSettingsAction.devices.remove(deviceId)) != null) {
            flags = 4; // 3 bit
            if(values.measurementPeriod != null)
                out.put("mtime", values.measurementPeriod.intValue());
            if(values.transmissionPeriod != null)
                out.put("stime", values.transmissionPeriod.intValue());
            if(values.minTemperature != null)
                out.put("tmin", values.minTemperature.floatValue());
            if(values.maxTemperature != null)
                out.put("tmax", values.maxTemperature.floatValue());
            if(values.minHumidity != null)
                out.put("hmin", values.minHumidity.floatValue());
            if(values.maxHumidity != null)
                out.put("hmax", values.maxHumidity.floatValue());
            if(values.adjustmentTemperature != null)
                out.put("toffset", values.adjustmentTemperature.floatValue());
            if(values.adjustmentHumidity != null)
                out.put("hoffset", values.adjustmentHumidity.floatValue());
        }
        out.put("flags", flags);

        sendResponseWithCRC(request, out, immediately);
    }

    private static AtomicLong responseIndex = new AtomicLong();

    private void sendResponseWithCRC(DatagramPacket request, JSONObject out, boolean immediately) throws IOException {
//        writeCRC32(out);

        InetAddress address = request.getAddress();
        int port = request.getPort();

        Runnable runnable = () -> {
            String outString = out.toString();
            byte[] bytes = outString.getBytes(); //out.array();
            long index = responseIndex.getAndIncrement();
            print("RESPONSE SENDING " + address + " " + port + " " + outString + " INDEX: " + index);
            DatagramPacket sendPacket = new DatagramPacket(bytes, bytes.length, address, port);
            try {
                serverSocket.send(sendPacket);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
            print("RESPONSE SENT " + address + " " + port + " " + outString + " INDEX: " + index);
        };
        if(immediately)
            runnable.run();
        else
            runnables.add(runnable);
    }

    private void writeCRC32(ByteBuffer buf) throws IOException {
        Checksum checksum = new CRC32();

        // update the current checksum with the specified array of bytes
        checksum.update(buf.array(), 0, buf.position());

        // get the current checksum value
        writeUnsignedInt(buf, checksum.getValue());
    }

    private static int asUnsignedShort(short s) {
        return s & 0xFFFF;
    }
    private static long asUnsignedInt(int s) {
        return s & 0xFFFFFFFFL;
    }
    private static int getUnsignedShort(ByteBuffer byteBuffer) {
        return asUnsignedShort(byteBuffer.getShort());
    }
    private static long getUnsignedInt(ByteBuffer byteBuffer) {
        return asUnsignedInt(byteBuffer.getInt());
    }
    private static void writeUnsignedShort(ByteBuffer byteBuffer, int value) {
        byteBuffer.putShort((short) value);
    }
    private static void writeUnsignedInt(ByteBuffer byteBuffer, long value) {
        byteBuffer.putInt((int) value);
    }

    private boolean receiveNewPacket(DatagramPacket receivePacket, String receivedString) throws IOException {
        JSONObject jsonObject = new JSONObject(receivedString);
//        ByteBuffer byteBuffer = ByteBuffer.wrap(receiveData).order(ByteOrder.LITTLE_ENDIAN);

        int packetType = jsonObject.getInt("msgid");
        int serialId = jsonObject.getInt("serial");
        deviceId = (long)serialId;

//        int packetType = getUnsignedShort(byteBuffer);
//        long serialId = getUnsignedInt(byteBuffer);
        switch (packetType) {
            case 0xEB01: // DEVID
                //Packet id u16	Serial u32	Flags u32	T-min
                //f32	T-max f32	H-min float 32	H-max float 32	T-meas u16	T-send u16	CRC u32
                long simId = jsonObject.getLong("imsi");
                try(DataSession session = createSession()){
                    writeSimIDAction.execute(session, getStack(), new DataObject(deviceId), new DataObject(String.valueOf(simId)));
                } catch (Throwable t) {
                    print("ERROR, IMPORT SID: "+ "\n" + t.getMessage() + "\n" + ExceptionUtils.getExStackTrace(ExceptionUtils.getStackTrace(t), ExecutionStackAspect.getExceptionStackTrace()));
                }
                sendAck(receivePacket, serialId, true);
                break;
            case 0xEA01: // TSYNC
                // Packet id u16	Serial u32	Flags u32	Timestamp 7 bytes	CRC u32
                sendTsync(receivePacket, serialId);
                break;
            case 0x5A01: // MEASUREMENTS
//                Packet id u16	Serial u32	Flags u32	Timestamp 7 bytes	Temp float 32 	Humidity float 32	Reserved u32
//                boolean immediate = immediateIds.contains(deviceId);
//                if(immediate)
                    sendAck(receivePacket, serialId, true);

                receiveMeasurements(receivePacket, serialId, jsonObject);

//                if(!immediate)
//                    sendAck(receivePacket, serialId, false);
                return true;
        }

        return false;
    }

//    private final Set<Long> immediateIds = BaseUtils.toSet(1210000022L, 1210000028L, 1210000112L, 1210000115L, 1210000109L);

    public void start() throws SocketException {
        serverSocket = new DatagramSocket(port);
        importTasksExecutor = ExecutorFactory.createMonitorThreadService(threads, UDPServer.this);
        scheduledTasksExecutor = ExecutorFactory.createMonitorScheduledThreadService(0, this);
        scheduledTasksExecutor.schedule(this::checkAndFlushPackets, 100, TimeUnit.MILLISECONDS);
        daemonTasksExecutor = ExecutorFactory.createMonitorThreadService(0, this);
        daemonTasksExecutor.submit(() -> {
//                byte[] receiveData = new byte[1024];
            nQps = 0;
            lastTimeStamp = System.currentTimeMillis();
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
                    if(receivedString.startsWith("{")) {
                        print("JSON PACKET: " + receivedString);
                        if(!receiveNewPacket(receivePacket, receivedString)) continue;
                    } else {
                        int nb = 0;
                        if (receivedString.startsWith("b'")) {
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
                        receivedString = receivedString.substring(0, receivedString.lastIndexOf(";") + 1);
                        if (receivedString.startsWith(";"))
                            receivedString = receivedString.substring(1);
                        if (!parsePacket(receivedString, nb)) continue;
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
                    checkAndFlushPackets();
                } catch (Throwable t) {
                    print("ERROR: " + "\n" + ExceptionUtils.getStackTrace(t));
                }
            }
        });
    }

    public void checkAndFlushPackets() {
        long timestamp = System.currentTimeMillis();
        if (nQps >= qps || timestamp - lastTimeStamp > maxDelay * 1000) {
            nQps = 0;
            lastTimeStamp = timestamp;
            importCSV();
        }
    }

    // --- Импорт в CSV
    private void importCSV() {
        final List<Runnable> textRunnables = new ArrayList<>(runnables);
        for (final DataObject deviceType : texts.keySet() ) {
            final String textToProceed = texts.get(deviceType).toString();
            importTasksExecutor.submit(new Runnable() {
                public void run() {
                    print("\n--- IMPORT: " + deviceType.toString() + ":\n" + textToProceed);
                    try(DataSession session = createSession()){
                        importAction.execute(session, getStack(), deviceType, serverObject, new DataObject(new RawFileData(textToProceed.getBytes()), CSVClass.get()));
                        session.applyException(getLogicsInstance().getBusinessLogics(), getStack());
                        for(Runnable runnable : textRunnables)
                            try {
                                runnable.run();
                            } catch (Throwable t) {
                                print("ERROR, RUNNING RESPONSE: " + "\n" + t.getMessage() + "\n" + ExceptionUtils.getExStackTrace(ExceptionUtils.getStackTrace(t), ExecutionStackAspect.getExceptionStackTrace()));
                            }
                    } catch (Throwable t) {
                        print("ERROR, IMPORT: "+ textToProceed + "\n" + t.getMessage() + "\n" + ExceptionUtils.getExStackTrace(ExceptionUtils.getStackTrace(t), ExecutionStackAspect.getExceptionStackTrace()));
                    }
                }
            });
        }
        texts.clear();
        runnables.clear();
    }

    // --- Обработка датчиков, начинается с b'(;)
    private boolean parsePacket(String cPacket, int nb) {
        deviceId = Long.parseLong(cPacket.substring(0,cPacket.indexOf(';')));
        if (nb == 2) {
            cDt = ZDateTimeClass.instance.formatString(Instant.now());
            cMeasuring = cPacket;
        } else {
            String [] cSplit = cPacket.split(";");
            cMeasuring = "";
            for (int i=0; i < cSplit.length - 1; i++) {
                cMeasuring += cSplit[i] + ";";
            }
            cDt = ZDateTimeClass.instance.formatString(Instant.ofEpochSecond(Long.parseLong(cSplit[cSplit.length - 1])));
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
            scheduledTasksExecutor.shutdown();
            importTasksExecutor.shutdown();;
        }
    }
}