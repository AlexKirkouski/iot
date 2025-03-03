package mite;

import com.google.common.base.Throwables;
import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.base.task.TaskRunner;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.AkimaSplineInterpolator;
import org.apache.commons.math3.analysis.interpolation.LoessInterpolator;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunctionLagrangeForm;

import java.sql.SQLException;

public class LagrangeAction extends InternalAction {

    private final ClassPropertyInterface xInterface;
    private final ClassPropertyInterface typeInterface;

    public LagrangeAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        xInterface = getOrderInterfaces().get(0);
        typeInterface = getOrderInterfaces().get(1);

    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> executionContext) throws SQLException, SQLHandledException {
        Double x = (Double)executionContext.getKeyObject(xInterface);
        int type = (Integer)executionContext.getKeyObject(typeInterface);

        try {
            ImMap<ImList<Object>, Object> points = findProperty("lagrangePoints[DOUBLE]").readAll(executionContext);

            int size = points.size();
            double[] t = new double[size];
            double[] y = new double[size];
            for(int i = 0; i < size; i++) {
                t[i] = (Double) points.getKey(i).single();
                y[i] = (Double) points.getValue(i);
            }

            // Строим полином Лагранжа:
            UnivariateFunction f = null;
            switch (type) {
                case 0:
                    f = new PolynomialFunctionLagrangeForm(t, y);
                    break;
                case 1:
                    SplineInterpolator interpolator = new SplineInterpolator();
                    f = interpolator.interpolate(t, y);
                    break;
                case 2:
                    AkimaSplineInterpolator akimaInterpolator = new AkimaSplineInterpolator();
                    f = akimaInterpolator.interpolate(t, y);
                    break;
                case 3:
                    LoessInterpolator loessInterpolator = new LoessInterpolator();
                    f = loessInterpolator.interpolate(t, y);
                    break;
            }

            findProperty("lagrangeResult[]").change(f.value(x), executionContext);
        } catch (Throwable e) {
            executionContext.requestUserInteraction(new MessageClientAction(e.getMessage(), "Error"));
            throw Throwables.propagate(e);
        }
    }

}
