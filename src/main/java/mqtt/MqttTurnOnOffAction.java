package mqtt;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;

public class MqttTurnOnOffAction extends InternalAction {

    private final ClassPropertyInterface pCtrl;
    private final ClassPropertyInterface pFlag;

    public MqttTurnOnOffAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM,classes);
        pCtrl = getOrderInterfaces().get(0);
        pFlag = getOrderInterfaces().get(1);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context)
            throws SQLException, SQLHandledException {
        try {
            DataObject o1 = context.getDataKeyValue(pCtrl);
            Long id = (Long) findProperty("id[Controller]").read(context,o1);
            String flag  = (String) context.getKeyObject(pFlag);
            String topic = "power" + id.toString();
            RSmqtt ob = new RSmqtt();
            ob.sendData("tcp://116.203.78.48:1883",topic,flag);
        } catch (Throwable e) {
            print(e.getMessage());
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), "Error"));
            throw Throwables.propagate(e);
        }
    }

    private void print(String msg) {
        System.out.println(msg);
    }
}
