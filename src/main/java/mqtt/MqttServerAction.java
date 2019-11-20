package mqtt;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;

public class MqttServerAction extends InternalAction {
    private final ClassPropertyInterface pMqtt;
    private final ClassPropertyInterface pFlag;

    public MqttServerAction (ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM,classes);
        pMqtt = getOrderInterfaces().get(0);
        pFlag = getOrderInterfaces().get(1);
    }


    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context)
                throws SQLException, SQLHandledException {
        try {
            DataObject o1 = context.getDataKeyValue(pMqtt);
            String  topic = (String) findProperty("topic[MqttServer]").read(context, o1);
            String  url   = (String) findProperty("url[MqttServer]").read(context, o1);
            url = "tcp://" + url;
            Integer port  = (Integer) findProperty("port[MqttServer]").read(context, o1);
            Integer flag  = (Integer) context.getKeyObject(pFlag);
            RSmqtt ob = new RSmqtt();
            if (flag == 1) {
                if (ob.receiveData(url, topic, port))
                    findProperty("isRun[MqttServer]").change(true,context.getSession(),o1);
            } else {
                if (ob.close(url, topic))
                    findProperty("isRun[MqttServer]").change(false,context.getSession(),o1);
            }
            context.apply();
        } catch (Throwable e) {
            print(e.getMessage());
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), "Error"));
            throw Throwables.propagate(e);
        }

    }

    private void print(String msg) {
        ServerLoggers.systemLogger.info(msg);
    }
}
