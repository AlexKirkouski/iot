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

import mite.UDPServer;

public class MqttRunContent extends InternalAction {

    private final ClassPropertyInterface pSrv;          // Объект сервера
    private final ClassPropertyInterface pCtrl;         // Объект контроллера
    private final ClassPropertyInterface pCnt;          // Контент выполнения, если пусто, то работаем по календарю
    private String url;                                 // URL mqtt сервера
    private String topic;                               // топик управления контроллером
    private String eMessage;                            // сообщение об ошибке
    private Integer prnConsole = 0;                     // печать отладочной информации в консоль

    public MqttRunContent(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM,classes);
        pSrv  = getOrderInterfaces().get(0);
        pCtrl = getOrderInterfaces().get(1);
        pCnt  = getOrderInterfaces().get(2);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context)
            throws SQLException, SQLHandledException {
        try {
            boolean lRet = true;
            DataObject o1   = context.getDataKeyValue(pSrv);
            DataObject o2   = context.getDataKeyValue(pCtrl);
            String cnt      = (String) context.getKeyObject(pCnt);
            prnConsole      = (Integer) findProperty("mqttPrnConsole[]").read(context);
            if (prnConsole == null) prnConsole = 0;
            url = "tcp://" + (String) findProperty("url[MqttServer]").read(context,o1);
            topic = (String) findProperty("topic[Controller]").read(context,o2);
            if (!runContent(cnt)) {
                findProperty("cntECode[]").change(1,context.getSession());
                findProperty("cntEMessage[]").change(eMessage, context.getSession());
                throw Throwables.propagate(new RuntimeException(eMessage));
            };
        } catch (Throwable e) {
            print(e.getMessage());
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), "Error"));
            throw Throwables.propagate(e);
        }
    }

    // выполнение одиночной команды управления
    private boolean runContent(String content) {
        boolean lRet;
        if (content.isEmpty()) return true;
        RSmqtt ob = new RSmqtt();
        print("RunContent " + content);
        if (!ob.sendData(this.url,this.topic,content)) {
            eMessage = ob.eMessage;
            return false;
        }
        return true;
    }

    // печать строки
    private void print(String cMsg) {
        if (prnConsole > 0) {
            UDPServer.print("TCP", cMsg);
        }
    }

}
