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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

import sclass.ConPrint;

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
            prnConsole      = (Integer) findProperty("mqttPrnConsole[]").read(context);
            if (prnConsole == null) prnConsole = 0;
            String cnt      = (String) context.getKeyObject(pCnt);
            url = "tcp://" + (String) findProperty("url[MqttServer]").read(context,o1);
            topic = (String) findProperty("topic[Controller]").read(context,o2);
            if (cnt.length() > 0) {
                if (!runContent(cnt)) {
                    findProperty("cntECode[]").change(1,context.getSession());
                    findProperty("cntEMessage[]").change(eMessage, context.getSession());
                };
            } else {
                Date date = new Date();
                String[] cdt  = new SimpleDateFormat("dd-HH.mm").format(date).split("-");
                String cDay   = "d"  + new SimpleDateFormat("u").format(date) + "[Controller]";
                String cmdStr = (String) findProperty(cDay).read(context,o2);
                if (cmdStr == null) cmdStr = "";
                if (cmdStr.length() > 0) {
                    // обработка, если задание назначено
                    String curDay = (String) findProperty("curDay[Controller]").read(context,o2);
                    String expCmp = (String) findProperty("expCmp[Controller]").read(context,o2);
                    if (curDay == null)   curDay = "";
                    if (expCmp == null)   expCmp = "";
                    if (!cdt[0].equals(curDay))
                        expCmp = "";                            // Обязательно надо выполнить команду, разные дни
                    Integer curTime = getInteger(cdt[1]);       // Текущее время
                    if (!cmdStr.endsWith(";")) cmdStr += ";";
                    cmdStr += "99= ";                           // Добавляем несуществующее конечное время
                    String[] fld = cmdStr.split(";");
                    Integer time1 = getInteger("-1");     // Несуществующее начальное время
                    String cntText = "";                        // Несуществующий начальный контент
                    String c1 ="";
                    for(int i = 0; i < fld.length; i++) {
                        String[] cPart = fld[i].split("=");
                        Integer time2 = getInteger(cPart[0]);
                        if (time1 <= curTime && curTime < time2) {
                            // нашли нужный диапазон, проверяем команда уже выполнялась?
                            if (i > 0) c1 = fld[i-1];
                            print(time1.toString() + " <= " + curTime.toString() + " < " + time2.toString());
                            print("RunContent ВРЕМЯ" + ", expCmp: " + expCmp + ", fld[i-1]: " + c1);
                            if (!expCmp.equals(c1)) {
                                if (runContent(cntText)) {
                                    findProperty("curDay[Controller]").change(cdt[0],context.getSession(),o2);
                                    findProperty("expCmp[Controller]").change(c1,context.getSession(),o2);
                                    context.apply();
                                }
                            }
                            break;
                        }
                        time1   = time2;
                        cntText = cPart[1];
                    }
                }
            }
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

    // возвращает значение Integer от символьного выражения типа Numeric
    private Integer getInteger(String cDec) {
        BigDecimal nRet = new BigDecimal(cDec).setScale(2,RoundingMode.HALF_UP);
        return nRet.multiply(new BigDecimal("100")).intValue();
    }

    // печать строки
    private void print(String cMsg) {
        if (prnConsole > 0) {
            ConPrint ob = new ConPrint();
            ob.print("UDP", cMsg);
        }
    }

}
