package mite;

import com.google.common.base.Throwables;
import lsfusion.base.file.RawFileData;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.action.LA;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.data.file.CSVClass;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;

public class FlushModbusDataAction extends InternalAction {

    private final ClassPropertyInterface deviceTypeInterface;

    public FlushModbusDataAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        deviceTypeInterface = getOrderInterfaces().get(0);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            DataObject deviceType = context.getDataKeyValue(deviceTypeInterface);

            LA<?> importAction = findAction("importCSV[DeviceType,Server,CSVFILE]");

            String data = AddModbusDataAction.flushData((Long) deviceType.object);

            importAction.execute(context, deviceType, NullValue.instance, new DataObject(new RawFileData(data.getBytes()), CSVClass.get()));
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

    }
}
