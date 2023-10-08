package mite;

import com.google.common.base.Throwables;
import com.intelligt.modbus.jlibmodbus.Modbus;
import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;
import lsfusion.base.Pair;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.data.time.ZDateTimeClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import org.json.JSONArray;
import org.json.JSONObject;
import org.spongycastle.util.Arrays;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AddModbusDataAction extends InternalAction {

    // connecting to the modbus server

    private static Map<Long, StringBuilder> devicesData = new HashMap<>();
    public static void addData(Long deviceType, String data) {
        synchronized (devicesData) {
            StringBuilder dataList = devicesData.get(deviceType);
            if(dataList == null) {
                dataList = new StringBuilder();
                devicesData.put(deviceType, dataList);
            } else {
                dataList.append("\n");
            }
            dataList.append(data);
        }
    }
    public static String flushData(Long device) {
        synchronized (devicesData) {
            return devicesData.remove(device).toString();
        }
    }

    // reading device modbus server + input registers to read

    private final ClassPropertyInterface deviceInfoInterface;

    public AddModbusDataAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        deviceInfoInterface = getOrderInterfaces().get(0);
    }

    private Map<Pair<String, Integer>, ModbusMaster> connections = new ConcurrentHashMap<>();
//    private Map<Pair<String, Integer>, ModbusTcpMaster> connections = new HashMap<>();

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        ObjectValue dataObject = context.getKeyValue(deviceInfoInterface);

        JSONObject deviceData;
        try {
            deviceData = (JSONObject) readJSON(dataObject);

            long id = deviceData.getLong("id");
            String modbusServer = deviceData.getString("modbusServer");
            int modbusPort = deviceData.getInt("modbusPort");
            int modbusId = deviceData.getInt("modbusId");
            long deviceType = deviceData.getLong("type");
            JSONArray measurements = deviceData.getJSONArray("measurements");

            Pair<String, Integer> params = new Pair<>(modbusServer, modbusPort);
            ModbusMaster m = connections.get(params);
            if(m == null) {
                m = connectModbus(params.first, params.second);
                connections.put(params, m);
            }

            String resultString = ZDateTimeClass.instance.formatString(Instant.now()) + ";";
            resultString += id + ";";

            for (int i = 0, size = measurements.length(); i < size; i++) {
                JSONObject measurement = measurements.getJSONObject(i);
                int address = measurement.getInt("address");
                String mSize = measurement.getString("size");
                String endian = measurement.getString("endian");

                int shorts = 1;
                switch (mSize) {
                    case "Modbus_Size.x16":
                        shorts = 1;
                        break;
                    case "Modbus_Size.x32":
                        shorts = 2;
                        break;
                    case "Modbus_Size.x64":
                        shorts = 4;
                       break;
                }
                RegisterOrder order = RegisterOrder.HighLow;
                switch (endian) {
                    case "Modbus_Endian.big":
                        order = RegisterOrder.HighLow;
                        break;
                    case "Modbus_Endian.little":
                        order = RegisterOrder.LowHigh;
                        break;
                }

                int[] slotValues = m.readInputRegisters(modbusId, address, shorts); // Arrays.copyOfRange(registerValues, i * 20, (i + 1) * 20);

                long data = ConvertRegisters(slotValues, order);

                resultString += data + ";";
            }


            addData(deviceType, resultString);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }


    private enum RegisterOrder {
        HighLow,
        LowHigh;
    }

    private static void disconnectModbus(ModbusMaster m) {
        try {
            m.disconnect();
        } catch (ModbusIOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ModbusMaster connectModbus(String modbusHost, int modbusPort) {
        TcpParameters tcpParameters = new TcpParameters();
        try {
            tcpParameters.setHost(InetAddress.getByName(modbusHost));
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        tcpParameters.setKeepAlive(true);
        tcpParameters.setPort(modbusPort);

        //if you would like to set connection parameters separately,
        // you should use another method: createModbusMasterTCP(String host, int port, boolean keepAlive);
        ModbusMaster m = ModbusMasterFactory.createModbusMasterTCP(tcpParameters);
//        m.setResponseTimeout(5000);

        Modbus.setAutoIncrementTransactionId(true);
        if (!m.isConnected()) {
            try {
                m.connect();
            } catch (ModbusIOException e) {
                throw new RuntimeException(e);
            }
        }
        return m;
    }

    public static int ConvertRegisters(int[] registers, RegisterOrder registerOrder) {
        if(registerOrder == RegisterOrder.HighLow)
            registers = Arrays.reverse(registers);
        return ConvertRegisters(registers);
    }

    public static int ConvertRegisters(int[] registers)
    {
        int result = 0;
        int coeff = 1;
        for(int i = 0; i < registers.length; i++) {
            result += registers[i] * coeff;
            coeff *= 256 * 256;
        }
        return result;
    }

    public static int ConvertRegisters(byte[] registers, RegisterOrder registerOrder) {
        if(registerOrder == RegisterOrder.HighLow)
            registers = Arrays.reverse(registers);
        return ConvertRegisters(registers);
    }

    public static int ConvertRegisters(byte[] registers)
    {
        int result = 0;
        int coeff = 1;
        for(int i = 0; i < registers.length; i++) {
            result += registers[i] * coeff;
            coeff *= 256;
        }
        return result;
    }
}
