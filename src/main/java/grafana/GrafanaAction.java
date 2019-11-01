package grafana;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import javax.xml.bind.DatatypeConverter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;


public class GrafanaAction extends InternalAction {
    private final ClassPropertyInterface porg;
    private final ClassPropertyInterface pdev;

    public GrafanaAction (ScriptingLogicsModule LM,ValueClass... classes) {
        super(LM, classes);
        porg = getOrderInterfaces().get(0);     // Класс свойств Organization
        pdev = getOrderInterfaces().get(1);     // Класс свойств Device
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        DataObject o1,o2; boolean lRet;
        try {
            GrafanaCreate ob = new GrafanaCreate();
            o1 = context.getDataKeyValue(porg);
            // общие параметры
            ob.cmd  = (Integer) findProperty("cmd[]").read(context);
            ob.url  = (String)  findProperty("grurl[]").read(context);
            ob.aut  = (String)  findProperty("aut[]").read(context);
            ob.txt  = (String)  findProperty("txtDashBoard[]").read(context);
            switch (ob.cmd) {
                case 1:
                // пакетное создание (обновление) организации, DataSource, пользователей (admin и пользователь)
                    ob.orgId            = (Integer) findProperty("orgId[Organization]").read(context,o1);
                        if (ob.orgId == null) ob.orgId = 0;
                    ob.name             = (String)  findProperty("grName[Organization]").read(context,o1);
                        ob.name = ob.nvl(ob.name);
                    ob.dsId             = (Integer) findProperty("dsId[Organization]").read(context,o1);
                        if (ob.dsId == null) ob.dsId = 0;
                    ob.dsUrl            = (String)  findProperty("dsUrl[Organization]").read(context,o1);
                        ob.dsUrl = ob.nvl(ob.dsUrl);
                    ob.dsName           = (String)  findProperty("dsName[Organization]").read(context,o1);
                        ob.dsName = ob.nvl(ob.dsName);
                        if (ob.dsName.length() == 0) ob.dsName = ob.getUnique(2);
                    ob.dsNameDB         = (String)  findProperty("dsNameDB[Organization]").read(context,o1);
                        ob.dsNameDB = ob.nvl(ob.dsNameDB);
                    ob.dsLogin          = (String)  findProperty("dsLogin[Organization]").read(context,o1);
                        ob.dsLogin = ob.nvl(ob.dsLogin);
                    ob.dsPassword       = (String)  findProperty("dsPassword[Organization]").read(context,o1);
                        ob.dsPassword = ob.nvl(ob.dsPassword);
                    ob.dsSSL            = (String)  findProperty("dsSSL[Organization]").read(context,o1);
                        ob.dsSSL = ob.nvl(ob.dsSSL);
                    ob.adminId          = (Integer) findProperty("adminId[Organization]").read(context,o1);
                        if (ob.adminId == null) ob.adminId = 0;
                    ob.admin            = (String)  findProperty("admin[Organization]").read(context,o1);
                        ob.admin = ob.nvl(ob.admin);
                    ob.adminPassword    = (String)  findProperty("adminPassword[Organization]").read(context,o1);
                        ob.adminPassword = ob.nvl(ob.adminPassword);
                    ob.userId           = (Integer) findProperty("userId[Organization]").read(context,o1);
                        if (ob.userId == null) ob.userId = 0;
                    ob.user             = (String)  findProperty("user[Organization]").read(context,o1);
                        ob.user = ob.nvl(ob.user);
                    ob.userPassword     = (String)  findProperty("userPassword[Organization]").read(context,o1);
                        ob.userPassword = ob.nvl(ob.userPassword);
                    lRet = ob.editOrg();
                    if (ob.orgId > 0) {
                        findProperty("orgId[Organization]").change(ob.orgId, context.getSession(), o1);
                        findProperty("dsId[Organization]").change(ob.dsId, context.getSession(), o1);
                        findProperty("adminId[Organization]").change(ob.adminId, context.getSession(), o1);
                        findProperty("userId[Organization]").change(ob.userId, context.getSession(), o1);
                        findProperty("dsName[Organization]").change(ob.dsName, context.getSession(), o1);
                        context.apply();
                        findProperty("eCode[]").change(0,context.getSession());
                    }
                    if (!lRet) {
                        if (ob.orgId == 0) {
                            // Критическая ошибка: организация не создалась, все остальное не имеет смысла
                            saveError(context,9,ob.eMessage);
                        } else {
                            // если что-то: обновление организации, создание пользователей, обновление DataSource, предупреждаем и это можно повторить
                            saveError(context,1,ob.eMessage);
                        }
                    }
                    break;
                case 3:
                    o2 = context.getDataKeyValue(pdev);
                    ob.orgId    = (Integer) findProperty("orgId[Organization]").read(context,o1);
                    ob.name     = (String)  findProperty("grName[Organization]").read(context,o1);
                    ob.dbId     = (Integer) findProperty("dbId[Device]").read(context,o2);
                    ob.idDevice = (Long) findProperty("id[Device]").read(context,o2);
                    if (ob.orgId == null) ob.orgId = 0;
                    if (ob.dbId == null) ob.dbId = 0;
                    if (ob.orgId > 0) {
                        if (ob.importDB()) {
                            findProperty("dbId[Device]").change(ob.dbId, context.getSession(), o2);
                            findProperty("eCode[]").change(0,context.getSession());
                        } else {
                            saveError(context, 1, ob.eMessage);
                        }
                    } else {
                        saveError(context,1,"Неизвестный код организации для Grafana");
                    }
                    break;
                case 10:
                    String aa = (String) findProperty("grurl[]").read(context);
                    saveError(context,1,"Привет коллега, " + aa);
                    break;
            }
        } catch (Throwable e) {
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), "Error"));
            throw Throwables.propagate(e);
        }
    }
    private void saveError(ExecutionContext<ClassPropertyInterface> context,Integer nCode,String cmsg) {
        try {
            findProperty("eCode[]").change(nCode,context.getSession());
            findProperty("eMessage[]").change(cmsg, context.getSession());
        } catch (Throwable e) {
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), "Error"));
            throw Throwables.propagate(e);
        }
    }
}


