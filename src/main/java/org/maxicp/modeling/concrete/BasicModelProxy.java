package org.maxicp.modeling.concrete;

import org.maxicp.modeling.Model;
import org.maxicp.modeling.ModelProxy;

/**
 * A very simple ModelProxy which is not thread-safe.
 * Use this class only when you explicitly want to avoid a ModelDispatcher, which is thread-safe.
 */
public class BasicModelProxy implements ModelProxy {

    private Model model;

    public BasicModelProxy() {
        this(null);
    }

    public BasicModelProxy(Model model) {
        this.model = model;
    }

    @Override
    public Model getModel() {
        return model;
    }

    @Override
    public <T extends Model> T setModel(T m) {
        model = m;
        return m;
    }
}
