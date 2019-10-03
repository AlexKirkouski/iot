package sclass;

import com.google.common.base.Throwables;
import grafana.JsonReadProcess;
import grafana.HttpQueryProcessor;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;


public class GetUnnAction extends InternalAction {

    public GetUnnAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        try {
            String unn = (String) findProperty("p_unn[]").read(context);
            findProperty("p_name").change("",context.getSession());
            findProperty("p_address").change("",context.getSession());
            if (unn == null) unn = "";
            if (unn.length() == 0) {
                findProperty("eCode").change(1, context.getSession());
                findProperty("eMessage").change("Значение УНН не определено", context.getSession());
                return;
            }
            HttpQueryProcessor oh = new HttpQueryProcessor();
            String url = "http://www.portal.nalog.gov.by/grp/getData?unp=" + unn + "&charset=UTF-8&type=json";
            if (!oh.send("GET",url,"")) {
                findProperty("eCode").change(1, context.getSession());
                findProperty("eMessage").change("Ошибка выполнения HTTP запроса\n" + oh.eMessage, context.getSession());
                return;
            }
            if (oh.nStatus != 200) {
                findProperty("eCode").change(1, context.getSession());
                String cmsg = "Ошибка выполнения HTTP запроса\n" + oh.cStatus;
                if (oh.nStatus == 404) cmsg += "\n" + "УНП не найден";
                findProperty("eMessage").change(cmsg, context.getSession());
                return;
            }
            findProperty("p_name").change("",context.getSession());
            findProperty("p_address").change("",context.getSession());
            JsonReadProcess ojs = new JsonReadProcess();
            ojs.load(oh.cResult);
            ojs.getPathValue("ROW.VNAIMP");
                if (ojs.cResult == null) ojs.cResult = "";
                findProperty("p_name").change(ojs.cResult,context.getSession());
            ojs.getPathValue("ROW.VPADRES");
                if (ojs.cResult == null) ojs.cResult = "";
                findProperty("p_address").change(ojs.cResult,context.getSession());
            ojs.getPathValue("ROW.VKODS");
                if (ojs.cResult == null) ojs.cResult = "";
                findProperty("p_vkods").change(ojs.cResult,context.getSession());
            if (ojs.cResult.equals("Действующий"))
                findProperty("eCode").change(0,context.getSession());
            else {
                findProperty("eMessage").change("Значение статуса: " + ojs.cResult, context.getSession());
                findProperty("eCode").change(2, context.getSession());
            }

        } catch (Throwable e) {
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), "Error"));
            throw Throwables.propagate(e);
        }
    }

}
