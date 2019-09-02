package sclass;

import com.google.common.base.Throwables;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;

public class ConPrintAction extends InternalAction {
    private final ClassPropertyInterface p1;

    public ConPrintAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM,classes);
        p1 = getOrderInterfaces().get(0);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context)
            throws SQLException, SQLHandledException {
        try {
            ConPrint ob = new ConPrint();
            ob.print((String) context.getKeyObject(p1),"");
        } catch (Throwable e) {
            throw Throwables.propagate(e);
        }
    }

}
