package edu.jhu.autodiff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Topographically ordered list of modules, combined into a single module. This module will call
 * foward() in topo-order and backward() in reverse topo-order on all the modules.
 * 
 * @author mgormley
 */
public class TopoOrder extends AbstractTensorModule implements Module<Tensor> {

    private List<Module<? extends Object>> topoOrder = new ArrayList<Module<? extends Object>>();
    protected Tensor y;
    protected Tensor yAdj;

    public TopoOrder() { }
    
    public void add(Module<? extends Object> m) {
        topoOrder.add(m);
    }
    
    @Override
    public Tensor forward() {
        for (Module<? extends Object> m : topoOrder) {
            m.forward();
        }
        return getLast().getOutput();
    }

    @Override
    public void backward() {
        List<Module<? extends Object>> revTopo = new ArrayList<Module<? extends Object>>(topoOrder);        
        Collections.reverse(revTopo);
        for (Module<? extends Object> m : revTopo) {
            m.backward();
        }
    }

    @Override
    public List<? extends Object> getInputs() {
        List inputs = new ArrayList();
        for (Module m : topoOrder) {
            inputs.add(m);
        }
        return inputs;
    }

    @Override
    public Tensor getOutput() {
        return getLast().getOutput();
    }

    @Override
    public Tensor getOutputAdj() {
        return getLast().getOutputAdj();
    }
    
    @Override
    public void zeroOutputAdj() {
        for (Module<? extends Object> m : topoOrder) {
            m.zeroOutputAdj();
        }
    }
    
    public Module<Tensor> getLast() {
        return (Module<Tensor>)topoOrder.get(topoOrder.size()-1);
    }
    
}