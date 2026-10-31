package org.maxicp.search.blackbox;

import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.xcsp3.XCSP3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class GroupCoherentLnsTest {

    @Test
    public void testModelDispatcherRegisterGroups() {
        ModelDispatcher model = Factory.makeModelDispatcher();
        IntVar x = model.intVar(0, 10);
        IntVar y = model.intVar(0, 10);
        IntVar z = model.intVar(0, 10);

        List<IntExpression> group1 = List.of(x, y);
        List<IntExpression> group2 = List.of(z);

        model.addVariableGroup(group1);
        model.addVariableGroup(group2);

        List<List<IntExpression>> registered = model.getModel().getVariableGroups();
        assertEquals(2, registered.size());
        assertEquals(group1, registered.get(0));
        assertEquals(group2, registered.get(1));
    }

    @Test
    public void testSelectorFreezesCorrectVariables() {
        Random random = new Random(42);
        List<Integer> decisionVars = List.of(0, 1, 2, 3, 4);
        
        List<List<Integer>> groups = new ArrayList<>();
        groups.add(List.of(0, 1));
        groups.add(List.of(2, 3, 4));

        GroupCoherentFragmentSelector selector = new GroupCoherentFragmentSelector(random, groups);
        FragmentSelector.FragmentSelectionContext context = 
                new FragmentSelector.FragmentSelectionContext(decisionVars, 90, 1);

        List<Integer> frozen = selector.selectFrozenIndices(context);
        
        // Either group 0 or group 1 was chosen. 
        // Only variables within the chosen group can be frozen (the other group's variables are completely relaxed).
        boolean group0Chosen = !frozen.contains(2) && !frozen.contains(3) && !frozen.contains(4);
        boolean group1Chosen = !frozen.contains(0) && !frozen.contains(1);

        assertTrue(group0Chosen || group1Chosen);
    }

    @Test
    public void testAdaptiveWeightsUpdate() {
        Random random = new Random(42);
        List<Integer> decisionVars = List.of(0, 1, 2);
        
        List<List<Integer>> groups = new ArrayList<>();
        groups.add(List.of(0));
        groups.add(List.of(1));
        groups.add(List.of(2));

        GroupCoherentFragmentSelector selector = new GroupCoherentFragmentSelector(random, groups);
        FragmentSelector.FragmentSelectionContext context = 
                new FragmentSelector.FragmentSelectionContext(decisionVars, 90, 1);

        // Force selection and then feedback
        selector.selectFrozenIndices(context);
        
        // Simulate restart failure (weight should decay)
        selector.onRestartCompleted(new FragmentSelector.FragmentSelectionFeedback(
                decisionVars, List.of(), java.util.Set.of(), false, false, false
        ));
        
        // Simulate restart improvement (weight should reward)
        selector.selectFrozenIndices(context);
        selector.onRestartCompleted(new FragmentSelector.FragmentSelectionFeedback(
                decisionVars, List.of(), java.util.Set.of(), true, false, false
        ));
    }

    @Test
    public void testConcreteModelVariableGroupsDelegation() {
        ModelDispatcher model = Factory.makeModelDispatcher();
        IntVar x = model.intVar(0, 10);
        IntVar y = model.intVar(0, 10);
        
        List<IntExpression> group = List.of(x, y);
        model.addVariableGroup(group);
        
        model.runCP(() -> {
            org.maxicp.modeling.Model current = model.getModel();
            assertTrue(current instanceof org.maxicp.cp.modeling.ConcreteCPModel);
            List<List<IntExpression>> registered = current.getVariableGroups();
            assertEquals(1, registered.size());
            assertEquals(group, registered.get(0));
        });
    }

    @Test
    public void testXCSP3SmallArrayGroups() throws Exception {
        String path = "src/test/resources/XCSP3/VARIOUS/small_array.xml";
        XCSP3.XCSP3LoadedInstance instance = XCSP3.load(path);
        assertNotNull(instance);
        
        List<List<IntExpression>> groups = instance.md().getModel().getVariableGroups();
        
        IntExpression[] dvars = instance.decisionVars();
        
        java.util.IdentityHashMap<IntExpression, Boolean> dvarMap = new java.util.IdentityHashMap<>();
        for (IntExpression dvar : dvars) {
            dvarMap.put(dvar, true);
        }
        
        for (int i = 0; i < groups.size(); i++) {
            List<IntExpression> group = groups.get(i);
            int mappedCount = 0;
            for (IntExpression expr : group) {
                if (dvarMap.containsKey(expr)) {
                    mappedCount++;
                }
            }
            assertEquals(group.size(), mappedCount);
        }
        
        // Now check under runCP
        instance.md().runCP(() -> {
            List<List<IntExpression>> cpGroups = instance.md().getModel().getVariableGroups();
            assertEquals(groups.size(), cpGroups.size());
            
            // Build the BlackBoxSearch manually to verify its internal selector
            BlackBoxSearch search = new BlackBoxSearch(instance.md(), instance.decisionVars(), instance.objective())
                    .withDefaultPhasePlan();
            
            // Find the LNS runnable search among registered searches
            LNSRunnableSearch lns = null;
            for (RunnableSearch s : search.getRunnableSearches()) {
                if (s instanceof LNSRunnableSearch) {
                    lns = (LNSRunnableSearch) s;
                    break;
                }
            }
            
            assertNotNull(lns);
            FragmentSelector selector = lns.getFragmentSelector();
            assertTrue(selector instanceof GroupCoherentFragmentSelector);
            GroupCoherentFragmentSelector groupSelector = (GroupCoherentFragmentSelector) selector;
            
            // Assert that the groups retrieved in the LNS are exactly the parsed groups (size 2) and not empty!
            assertEquals(2, groupSelector.getGroups().size());
            assertEquals(5, groupSelector.getGroups().get(0).size());
            assertEquals(3, groupSelector.getGroups().get(1).size());
        });
    }
}
