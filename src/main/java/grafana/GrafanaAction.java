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


// Класс для работы с сервером Grafana
class GrafanaCreate {
            String  url             = "";           // URL для доступа к серверу Grafana
            String  aut             = "";           // Значение базовой аутификации
            Integer cmd             = 0;            // Номер команды для выполнения
            String  txt             = "";           // Текст дашборда для импорта в Grafana
            String  eMessage        = "";           // Текст ошибки
    private String  cValue          = "";           // Значение из getValue()
    private String  cResult         = "";           // Буффер построенного JSON выражения или тело ответа от WEB
    private Integer nStatus         = 0;            // WEB статус выполнения HTTP запроса
    // свойства, Организация
            Integer orgId           = 0;            // ID организации
            String  name            = "";           // Название организации
            Integer dsId            = 0;            // ID источника данных
            String  dsUrl           = "";           // URL источника данных
            String  dsName          = "";           // Рабочее название источника данных
            String  dsNameDB        = "";           // Имя БД источника данных
            String  dsLogin         = "";           // Логин к БД
            String  dsPassword      = "";           // Пароль к БД
            String  dsSSL           = "";           // SSL mode
            Integer adminId         = 0;            // ID пользователя типа admin
            String  admin           = "";           // Логин пользователя типа admin
            String  adminPassword   = "";           // Пароль пользователя типа admin
            Integer userId          = 0;            // ID пользователя типа user
            String  user            = "";           // Логин пользователя типа user
            String  userPassword    = "";           // Пароль пользователя типа user
    // устройства
            Long    idDevice        ;               // ID устройства
            Integer dbId            = 0;            // ID DashBoard

    // * Создание или обновление организации
    boolean editOrg() {
        boolean lRet;
        this.cResult = "";
        addKeyValue("{","name",this.name,"}","");
        if (this.orgId > 0) {
            // обновление названия организации
            if (!send("PUT","api/orgs/" + this.orgId.toString(),this.cResult,0)) return false;
            if (this.nStatus != 200)
                return errBox(getExtErr("Ошибка обновления организации\nВозможно, неверный ID организации"));
            lRet = editDS();
            if (lRet) lRet = editUser(this.admin,this.adminPassword,"Admin",true);
            if (lRet) lRet = editUser(this.user,this.userPassword,"Viewer",false);
            return lRet;
        }
        // новая организация
        if (!send("POST","api/orgs",this.cResult,0)) return false;
        switch (this.nStatus) {
            case 200:
                if (!getValue(this.cResult,"orgId")) return false;
                this.orgId = Integer.parseInt(this.cValue);
                lRet = editDS();
                if (lRet) lRet = editUser(this.admin,this.adminPassword,"Admin",true);
                if (lRet) lRet = editUser(this.user,this.userPassword,"Viewer",false);
                return lRet;
            case 409:
                String cname;
                try {
                    cname = URLEncoder.encode(this.name,"UTF-8");
                } catch (UnsupportedEncodingException e) {
                    cname = "";
                    e.printStackTrace();
                }
                if (!send("GET","api/orgs/name/" + cname,"",0)) return false;
                if (this.nStatus != 200) return errBox(getExtErr("Ошибка создания организации"));
                if (!getValue(this.cResult,"id")) return false;
                this.orgId = Integer.parseInt(this.cValue);
                lRet = editDS();
                if (lRet) lRet = editUser(this.admin,this.adminPassword,"Admin",true);
                if (lRet) lRet = editUser(this.user,this.userPassword,"Viewer",false);
                return lRet;
        }
        return errBox(getExtErr("Ошибка создания организации."));
    }

    // * Делаем организацию активной
    private boolean setOrg() {
        String cid = this.orgId.toString();
        if (this.orgId == 0) return errBox("ID организации не определен");
        if (!send("POST","api/user/using/" + cid,"",0)) return false;
        if (this.nStatus != 200) return errBox(getExtErr("Организация " + cid + " не активна."));
        return true;
    }

    // * Удаление организации
    boolean delOrg() {
        String cid = this.orgId.toString();
        if (this.orgId == 0)
            return errBox("ID организации не определен");
        if (!send("DELETE","api/orgs/" + cid,"",0))
            return false;
        if (this.nStatus != 200)
            return errBox(getExtErr("Организация " + cid + " не удалена."));
        return true;
    }

    // * Создание или обновление DataSource
    private boolean editDS() {
        if (!setOrg()) return false;
        if (this.dsId == 0) {
            this.cResult = "";
            addKeyValue("{","name",this.dsName,"","");
            addKeyValue(",","type","postgres","","");
            addKeyValue(",","access","proxy","","");
            addKeyValue(",","isDefault","true","}","*");
            if (!send("POST","api/datasources",this.cResult,1)) return false;
            switch (this.nStatus) {
                case 200:
                    getValue(this.cResult,"datasource.id");
                    this.dsId = Integer.parseInt(this.cValue);
                    break;
                case 409:
                    if (!send("GET","api/datasources/name/" + this.dsName,"",0)) return false;
                    if (this.nStatus != 200) return errBox(getExtErr("Ошибка создания DataSource."));
                    getValue(this.cResult,"id");
                    this.dsId = Integer.parseInt(this.cValue);
                    break;
                default:
                    return errBox(getExtErr("Ошибка создания DataSource."));
            }
        }
        // обновляем источник данных или продолжаем создание
        this.cResult = "";
        // проверки на поля
        if (this.dsNameDB.length() == 0) return errBox("Не определено имя БД");
        if (this.dsUrl.length() == 0) return errBox("Не внесен URL адрес БД");
        if (this.dsLogin.length() == 0) return errBox("Не внесен пользователь БД");
        if (this.dsPassword.length() == 0) return errBox("Не внесен пароль пользователя БД");
        if (this.dsSSL.length() == 0) return errBox("Не внесено значение режима SSL");
        addKeyValue("{","id",this.dsId.toString(),"","*");
        addKeyValue(",","orgId",this.orgId.toString(),"","*");
        addKeyValue(",","name",this.dsName,"","");
        addKeyValue(",","type","postgres","","");
        addKeyValue(",","typeLogoUrl"," ","","");
        addKeyValue(",","access","proxy","","");
        addKeyValue(",","url",this.dsUrl,"","");
        addKeyValue(",","password"," ","","");
        addKeyValue(",","user",this.dsLogin,"","");
        addKeyValue(",","database",this.dsNameDB,"","");
        addKeyValue(",","basicAuth","false","","*");
        addKeyValue(",","basicAuthUser"," ","","");
        addKeyValue(",","basicAuthPassword"," ","","");
        addKeyValue(",","withCredentials","false","","*");
        addKeyValue(",","isDefault","true","","*");
        addKeyValue(",","jsonData","","","");
        addKeyValue("{","postgresVersion","903","","*");
        addKeyValue(",","sslmode",this.dsSSL,"}","");
        addKeyValue(",","secureJsonFields","","","");
        addKeyValue("{","password","true","}","*");
        addKeyValue(",","version",getUnique(1),"","*");
        addKeyValue(",","readOnly","false","","*");
        addKeyValue(",","secureJsonData","","","");
        addKeyValue("{","password",this.dsPassword,"}}","");
        return send("PUT","api/datasources/" + this.dsId.toString(),this.cResult,1);
    }

    // * Создание пользователя
    private boolean editUser(String login,String pwd,String role,boolean admin) {
        Integer nid; String c1 = "пользователя";
        if (admin) c1 = "администратора";
        // проверки
        if (login.length() == 0) return errBox("Не определено имя " + c1);
        if (pwd.length() == 0) return errBox("Не определено пароль " + c1);
        if (admin) nid = this.adminId; else nid = userId;
        if (nid > 0) {
            if (!delUser(nid)) return errBox(getExtErr("Ошибка пересоздания пользователя " + login));
        }
        if (admin) this.adminId = 0; else userId = 0;
        if (!setOrg()) return false;
        this.cResult = "";
        addKeyValue("{", "name", login, "", "");
        addKeyValue(",", "email", login + "@localhost", "", "");
        addKeyValue(",", "login", login, "", "");
        addKeyValue(",", "password", pwd, "}", "");
        if (!send("POST","api/admin/users",this.cResult,0)) return false;
        if (this.nStatus !=200 ) return errBox(getExtErr("Пользователь " + login + " не создан"));
        if (!getValue(this.cResult,"id")) return false;
        nid = Integer.parseInt(this.cValue);
        if (admin) this.adminId = nid; else userId = nid;
        // связываем пользователя с организацией
        this.cResult = "";
        addKeyValue("{","name",login,"","");
        addKeyValue(",","role",role,"","");
        addKeyValue(",","loginOrEmail",login + "@localhost","}","");
        if (!send("POST","api/orgs/" + this.orgId.toString() + "/users",this.cResult,0)) return false;
        if (this.nStatus != 200) return errBox(getExtErr("Ошибка создания пользователя"));
        // удаление из базовой организации (1) пользователя nid созданной организации orgId
        return send("DELETE","api/orgs/1/users/" + nid.toString(),"",1);
    }

    // * Удаление пользователя
    private boolean delUser(Integer id) {
        return send("DELETE","api/admin/users/" + id.toString(),"",1);
    }

    // * Import DashBoard
    boolean importDB() {
        if (!getValue(this.txt,"dashboard.title"))
            return errBox("Ошибка получения имени дашборда");
        String dbName = this.cValue;
        if (!setOrg()) return false;
        if (!send("POST","api/dashboards/import",this.txt,1)) return false;
        if (!send("GET","api/search?query=" + dbName + "&starred=false","",1)) return false;
        if (this.nStatus != 200) return errBox(getExtErr("Ошибка создания дашборда"));
        getValue("{\"p\":" + cResult + "}","p[0].id");
        this.dbId = Integer.parseInt(this.cValue);
        return true;
    }

    // возвращает значение по ключу из структуры json
    private boolean getValue(String cbuf,String cpath) {
        this.cValue = "";
        if (cbuf.length() == 0) return true;
        JsonReadProcess ojs2 = new JsonReadProcess();
        if (!ojs2.load(cbuf)) return errBox(ojs2.eMessage);
        if (!ojs2.getPathValue(cpath)) return errBox(ojs2.eMessage);
        this.cValue = ojs2.cResult;
        return true;
    }

    // Выполнение запроса
    private boolean send(String cMethod,String cUrl,String cData, Integer nkey) {
        this.nStatus = 0;
        HttpQueryProcessor oh = new HttpQueryProcessor();
        oh.addHeader("Content-Type", "application/json");
        oh.addHeader("Accept","application/json");
        oh.mimeType = "application/json";
        oh.addHeader("Authorization","Basic " + DatatypeConverter.printBase64Binary(this.aut.getBytes()));
        if (nkey > 0) oh.addHeader("X-Grafana-Org-Id",this.orgId.toString());
        if (!oh.send(cMethod,this.url + cUrl,cData)) return errBox(oh.eMessage);
        this.nStatus = oh.nStatus;
        this.cResult = oh.cResult;
        return true;
    }

    // Обработка null и trim
    String nvl(String c2) {
        if (c2 == null) c2 = "";
        return c2.trim();
    }

    //  Конструктур JSON выражений
    private void addKeyValue(String ch1, String key, String value, String ch2, String tip) {
        cResult += ch1;
        if (key.length() > 0) {
            cResult += "\"" + key + "\":";
        }
        if (value.length() > 0) {
            if (tip.equals("*")) cResult += value;
            else {
                value = value.replace("\"", "'");
                cResult += "\"" + value.trim() + "\"";
            }
        }
        cResult += ch2;
    }

    // Получить уникальное имя: 1 как цифры, иначе 4 буквы + 4 цифры
    String getUnique(Integer tip) {
        String cDate;
        Date date = new Date();
        if (tip == 1) {
            SimpleDateFormat fDate = new SimpleDateFormat("yyMMddHHmmss");
            return fDate.format(date);
        }
        SimpleDateFormat fDate = new SimpleDateFormat("dd.MM.yy.HH.mmss");
        cDate = fDate.format(date);
        String cRet=""; int n1;
        n1 = Integer.parseInt(cDate.substring(0,2)); // день
        if (n1 < 26) n1 = 64 + n1; else n1 = 48 + (31 - n1);
        cRet += (char) n1;
        n1 = Integer.parseInt(cDate.substring(3,5)); // месяц
        cRet += (char) (64 + n1);
        n1 = Integer.parseInt(cDate.substring(6,8)); // год
        cRet += (char) (65 + n1 - 19);
        n1 = Integer.parseInt(cDate.substring(9,11)); // час
        cRet += (char) (65 + n1);
        n1 = Integer.parseInt(cDate.substring(12)); // минуты секунды
        cRet += Integer.toString(n1);
        return cRet;
    }

    // Обработка ошибок
    private boolean errBox(String eMsg) {
        eMsg = "GrafanaCreate: " + eMsg;
        eMessage = eMsg;
        return false;
    }

    // Дополнительное сообщение об ошибке
    private String getExtErr(String cmsg) {
        String cRet=cmsg + ":\n";
        getValue(this.cResult,"message");
        if (this.cValue.length() > 0) cRet = this.cValue + "\n";
        cRet += "WEB статус " + this.nStatus.toString();
        return cRet;
    }
}
