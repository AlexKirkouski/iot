package mite;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UpdateDeviceSettingsAction extends InternalAction {

    private final ClassPropertyInterface deviceInterface;

    public static class Values {
        public final BigDecimal measurementPeriod;
        public final BigDecimal transmissionPeriod;
        public final Double minTemperature;
        public final Double maxTemperature;
        public final Double minHumidity;
        public final Double maxHumidity;

        public Values(BigDecimal measurementPeriod, BigDecimal transmissionPeriod, Double minTemperature, Double maxTemperature, Double minHumidity, Double maxHumidity) {
            this.measurementPeriod = measurementPeriod;
            this.transmissionPeriod = transmissionPeriod;
            this.minTemperature = minTemperature;
            this.maxTemperature = maxTemperature;
            this.minHumidity = minHumidity;
            this.maxHumidity = maxHumidity;
        }
    }

    public static Map<Long, Values> devices = new ConcurrentHashMap<>();

    public UpdateDeviceSettingsAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        deviceInterface = getOrderInterfaces().get(0);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> executionContext) throws SQLException, SQLHandledException {
        DataObject deviceObject = executionContext.getDataKeyValue(deviceInterface);

        try {
            devices.put((Long) findProperty("id[Device]").read(executionContext, deviceObject), new Values(
                    (BigDecimal) findProperty("measurementPeriod[Device]").read(executionContext, deviceObject),
                    (BigDecimal) findProperty("transmissionPeriod[Device]").read(executionContext, deviceObject),
                    (Double) findProperty("minTemperature[Device]").read(executionContext, deviceObject),
                    (Double) findProperty("maxTemperature[Device]").read(executionContext, deviceObject),
                    (Double) findProperty("minHumidity[Device]").read(executionContext, deviceObject),
                    (Double) findProperty("maxHumidity[Device]").read(executionContext, deviceObject)
            ));
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            executionContext.requestUserInteraction(new MessageClientAction(e.getMessage(), "Error"));
            throw Throwables.propagate(e);
        }
    }
}
