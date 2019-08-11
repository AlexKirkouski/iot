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

public class MqttServerAction extends InternalAction {
    private final ClassPropertyInterface pMqtt;

    public MqttServerAction (ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM,classes);
        pMqtt = getOrderInterfaces().get(0);
    }


        @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context)
                throws SQLException, SQLHandledException {
        try {
            DataObject o1 = context.getDataKeyValue(pMqtt);
            String  topic = (String) findProperty("topic[ServerMqtt]").read(context, o1);
            String  url   = "tcp://" + (String) findProperty("url[ServerMqtt]").read(context, o1);
            Integer port  = (Integer) findProperty("port[ServerMqtt]").read(context, o1);
            RSmqtt ob = new RSmqtt();
            ob.receiveData(url, topic, port);
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
