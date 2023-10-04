package mite;

import com.digitalpetri.modbus.master.ModbusTcpMaster;
import com.digitalpetri.modbus.master.ModbusTcpMasterConfig;
import com.digitalpetri.modbus.requests.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.requests.ReadInputRegistersRequest;
import com.digitalpetri.modbus.responses.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.responses.ReadInputRegistersResponse;
import com.google.common.base.Throwables;
import com.intelligt.modbus.jlibmodbus.Modbus;
import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusProtocolException;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterTCP;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.ReferenceCountUtil;
import lsfusion.base.Pair;
import lsfusion.base.Result;
import lsfusion.base.file.RawFileData;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.action.LA;
import lsfusion.server.logics.BusinessLogicsBootstrap;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.data.file.CSVClass;
import lsfusion.server.logics.classes.data.time.ZDateTimeClass;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import net.solarnetwork.io.modbus.ModbusMessage;
import net.solarnetwork.io.modbus.netty.msg.RegistersModbusMessage;
import net.solarnetwork.io.modbus.tcp.netty.NettyTcpModbusClientConfig;
import net.solarnetwork.io.modbus.tcp.netty.TcpNettyModbusClient;
import net.solarnetwork.io.modbus.ModbusClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.spongycastle.util.Arrays;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import static net.solarnetwork.io.modbus.netty.msg.RegistersModbusMessage.readHoldingsRequest;

public class ReadModbusDataAction extends InternalAction {

    // connecting to the modbus server

    // reading device modbus server + input registers to read

    private final ClassPropertyInterface deviceInfoInterface;

    public ReadModbusDataAction(ScriptingLogicsModule LM, ValueClass... classes) {
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
            LA<?> importAction = findAction("importCSV[DeviceType,Server,CSVFILE]");
            ConcreteCustomClass deviceTypeClass = (ConcreteCustomClass) findClass("DeviceType");

//            exportModbus.execute(context, dataObject);
            deviceData = (JSONObject) readJSON(dataObject);

            long id = deviceData.getLong("id");
            String modbusServer = deviceData.getString("modbusServer");
            int modbusPort = deviceData.getInt("modbusPort");
            int modbusId = deviceData.getInt("modbusId");
            long deviceType = deviceData.getLong("type");
            JSONArray measurements = deviceData.getJSONArray("measurements");

            String resultString = "";

            Pair<String, Integer> params = new Pair<>(modbusServer, modbusPort);
            boolean notRead = true;
            while(notRead) {
                ModbusMaster m = connections.get(params);
                if(m == null) {
                    m = connectModbus(params.first, params.second);
                    connections.put(params, m);
                }

//                NettyTcpModbusClientConfig config = new NettyTcpModbusClientConfig(modbusServer, modbusPort);
//                config.setAutoReconnect(false);
//                ModbusClient client = new TcpNettyModbusClient(config);

//                client.start().get();

//                ModbusTcpMaster master = connections.get(params);
//                if(master == null) {
//                    ModbusTcpMasterConfig.Builder builder = new ModbusTcpMasterConfig.Builder(modbusServer);
////                builder.setTimeout(Duration.ofDays(10));
//                    builder.setPort(modbusPort);
////                    builder.setLazy(false);
////                    builder.setPersistent(false);
//                    ModbusTcpMasterConfig config = builder.build();
//                    master = new ModbusTcpMaster(config);
//
//                    master.connect();
//                }

                try {

                    //                int time = ConvertRegistersToInt(Arrays.copyOfRange(slotValues, 4, 6));
                    resultString += ZDateTimeClass.instance.formatString(Instant.now()) + ";";
                    //                int sid = ConvertRegistersToInt(Arrays.copyOfRange(slotValues, 1, 3));
                    //                resultString += (100 * sid + i) + ";";
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
                        // request holding registers

//                        ModbusMessage req = readHoldingsRequest(modbusId, address, shorts);
//                        RegistersModbusMessage res = client.send(req).unwrap(RegistersModbusMessage.class);

                        // print out the results
//                        int[] slotValues = res.dataDecodeUnsigned();

//                        ReadInputRegistersRequest request = new ReadInputRegistersRequest(address, shorts);
//                        CompletableFuture<ReadInputRegistersResponse> future = master.sendRequest(request, modbusId);
//
//                        ReadInputRegistersResponse response = future.get();
//
//                        ByteBuf registers = response.getRegisters();
//                        int[] slotValues = new int[shorts];
//                        for(int k=0;k<shorts;k++)
//                            slotValues[k] = registers.getUnsignedShort(k*2);
//
//                        ReferenceCountUtil.release(response);

                        long data = ConvertRegisters(slotValues, order);
//
//                switch(mSize) {
//                    case "Modbus_Size.x16":
//                        data = ConvertRegisters(slotValues, order);
//                        break;
//                    case "Modbus_Size.x32":
//                        data = ConvertRegistersToInt(slotValues, order);
//                        break;
//                    case "Modbus_Size.x64":
//                        data = ConvertRegistersToLong(slotValues, order);
//                        break;
//                }

                        resultString += data + ";";

                        notRead = false;
                    }
                } catch (Exception e) {

//                    e.printStackTrace();
                    ServerLoggers.sqlSuppLog(e);
//                    disconnectModbus(m);
//                    master.disconnect();
//                    connections.remove(params);
//                    Thread.sleep(10000);
//                    throw new RuntimeException(e);
                } finally {
//                    client.stop();
//                disconnectModbus(m);
                }
            }

            importAction.execute(context, new DataObject(deviceType, deviceTypeClass), NullValue.instance, new DataObject(new RawFileData(resultString.getBytes()), CSVClass.get()));
            context.apply();
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
        m.setResponseTimeout(50000);

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
