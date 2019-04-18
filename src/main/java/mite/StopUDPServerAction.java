package mite;

import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;

public class StopUDPServerAction extends InternalAction {

    private final ClassPropertyInterface serverInterface;

    public StopUDPServerAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        serverInterface = getOrderInterfaces().get(0);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        DataObject serverObject = context.getDataKeyValue(serverInterface);

        UDPServer udpServer = UDPServer.runningServers.get(serverObject);
        if(udpServer != null)
            udpServer.stop();
    }
}
