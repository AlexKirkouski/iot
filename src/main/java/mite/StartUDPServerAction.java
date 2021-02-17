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

import java.net.SocketException;
import java.sql.SQLException;

public class StartUDPServerAction extends InternalAction {

    private final ClassPropertyInterface serverInterface;
    private final ClassPropertyInterface portInterface;
    private final ClassPropertyInterface actionInterface;

    public StartUDPServerAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        serverInterface = getOrderInterfaces().get(0);
        portInterface = getOrderInterfaces().get(1);
        actionInterface = getOrderInterfaces().get(2);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> executionContext) throws SQLException, SQLHandledException {

        DataObject serverObject = executionContext.getDataKeyValue(serverInterface);
        Integer port = (Integer)executionContext.getKeyObject(portInterface);
        String actionName = (String)executionContext.getKeyObject(actionInterface);

        try {
            ConcreteCustomClass deviceType = (ConcreteCustomClass) findClass("DeviceType");
            UDPServer server = new UDPServer(executionContext.getLogicsInstance(), port, findAction(actionName), findProperty("type[LONG]"), findAction("writeSimID[LONG,STRING]"), deviceType.getDataObject("unknown"), serverObject);
            server.qps = (Integer) findProperty("qps[Server]").read(executionContext,serverObject);
            if (server.qps == null) server.qps = 1;
            server.prnConsole = (Integer) findProperty("udpPrnConsole[]").read(executionContext);
            if (server.prnConsole == null) server.prnConsole = 0;
            server.start();
            UDPServer.runningServers.put(serverObject, server);
        } catch (Throwable e) {
            executionContext.requestUserInteraction(new MessageClientAction(e.getMessage(), "Error"));
            throw Throwables.propagate(e);
        }
    }
}
