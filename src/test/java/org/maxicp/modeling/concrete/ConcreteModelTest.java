package org.maxicp.modeling.concrete;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.modeling.CustomConstraint;
import org.maxicp.modeling.NotAChildModelException;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.symbolic.SymbolicModel;
import org.maxicp.state.*;
import org.maxicp.state.trail.Trailer;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public abstract class ConcreteModelTest<C extends ConcreteModel, P extends ConcreteConstraint<C>> extends StateManagerTest {

    public abstract class MockConstraint implements CustomConstraint<C, P> {

        @Override
        public Collection<? extends Expression> scope() {
            return List.of();
        }

        @Override
        public String toString() {
            return "MockedConstraint";
        }
    }

    /**
     * Returns a concrete model with a given StateManager
     * @param stateManager state manager used in the concrete model
     * @return instantiated concrete model
     */
    public abstract C modelSupplier(StateManager stateManager);

    /**
     * Returns a small constraint used to mock the behavior of a real constraint.
     * @return instantiable constraint
     */
    public abstract CustomConstraint<C, P> mockConstraint();

    /**
     * A StateManager that can locked, throwing {@link IllegalStateException} on save and restore operations if locked
     */
    private class LockableStateManager extends Trailer {

        private boolean locked = false;

        public LockableStateManager() {
            super();
        }

        @Override
        public void saveState() {
            if (locked)
                throw new IllegalStateException("Cannot use saveState at this point");
            super.saveState();
        }

        @Override
        public void restoreState() {
            if (locked)
                throw new IllegalStateException("Cannot use restoreState at this point");
            super.restoreState();
        }

        public void setLocked(boolean locked) {
            this.locked = locked;
        }
    }

    /**
     * Test that calling jumpTo twice on the same position does not call saveState nor restoreState
     */
    @Test
    public void testDoubleJumpToToSamePosition() {
        LockableStateManager lockableStateManager = new LockableStateManager();
        C model = modelSupplier(lockableStateManager);
        lockableStateManager.saveState();
        SymbolicModel rootModel = model.symbolicCopy();
        CustomConstraint<C, P> constraint = mockConstraint();
        model.add(constraint);
        SymbolicModel childModel = model.symbolicCopy();
        lockableStateManager.setLocked(true);
        // cannot jump to root model if the state manager is locked
        assertThrowsExactly(IllegalStateException.class, () -> model.jumpTo(rootModel));
        // unlock the state manager and jump back to the root
        lockableStateManager.setLocked(false);
        assertDoesNotThrow(() -> model.jumpTo(rootModel));
        // jump back to the child
        model.jumpTo(childModel);
        lockableStateManager.setLocked(true);
        // jumping to the same node (here the child) should not throw any exception
        assertDoesNotThrow(() -> model.jumpTo(childModel));
    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testJumpToSimpleChild(StateManager sm) {
        C model = modelSupplier(sm);
        SymbolicModel rootModel = model.symbolicCopy();
        CustomConstraint<C, P> constraint = mockConstraint();
        model.add(constraint);
        SymbolicModel childModel = model.symbolicCopy();
        assertDoesNotThrow(() -> model.jumpTo(rootModel));
        // jump back to the child
        model.jumpTo(childModel);
        // jump back to the root
        assertDoesNotThrow(() -> model.jumpTo(rootModel));
    }

    /**
     * Jumps to another child of the parent node
     */
    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testJumpToBrother(StateManager sm) {
        C model = modelSupplier(sm);
        SymbolicModel rootModel = model.symbolicCopy();
        model.add(mockConstraint());
        SymbolicModel child1 = model.symbolicCopy();

        model.jumpTo(rootModel);
        assertEquals(rootModel, model.symbolicCopy());

        model.add(mockConstraint());
        SymbolicModel child2 = model.symbolicCopy();

        // jump back to child1
        model.jumpTo(child1);
        assertEquals(child1, model.symbolicCopy());

        // jump back to child2
        model.jumpTo(child2);
        assertEquals(child2, model.symbolicCopy());
    }

    /**
     * Jumps to arbitrary child and parents on a chain of models having one child each
     */
    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testLongJump(StateManager sm) {
        int level = 100;
        C model = modelSupplier(sm);
        SymbolicModel rootModel = model.symbolicCopy();
        SymbolicModel[] modelChain = new SymbolicModel[level];
        modelChain[0] = rootModel;
        Random random = new Random(42);
        for (int i = 1 ; i < level ; i++) {
            model.add(mockConstraint());
            modelChain[i] = model.symbolicCopy();
            assertNotEquals(modelChain[i-1], modelChain[i]);
        }
        for (int i = 0 ; i < level ; i++) {
            int jumpTo = random.nextInt(level);
            model.jumpTo(modelChain[jumpTo]);
            assertEquals(modelChain[jumpTo], model.symbolicCopy());
        }
    }

    @Test
    public void testArbitraryJumpTo() {
        LockableStateManager sm = new LockableStateManager();
        C model = modelSupplier(sm);
        SymbolicModel rootModel = model.symbolicCopy();
        Map<SymbolicModel, List<SymbolicModel>> children = new HashMap<>(); // the children of each model
        children.put(rootModel, new ArrayList<>());
        Random random = new Random(42);
        SymbolicModel currentModel = model.symbolicCopy();
        for (int i = 0 ; i < 100 ; i++) {
            model.add(mockConstraint());
            SymbolicModel child = model.symbolicCopy();
            children.get(currentModel).add(child);
            if (!children.containsKey(child)) {
                children.put(child, new ArrayList<>());
            }
            // pick randomly an existing child
            SymbolicModel nextModel = (children.keySet()).stream().toList().get(random.nextInt(children.size()));
            if (nextModel == child) { // jumpTo should do nothing
                sm.setLocked(true);
                model.jumpTo(nextModel);
                sm.setLocked(false);
            } else {
                model.jumpTo(nextModel);
            }
            assertEquals(nextModel, model.symbolicCopy());
            currentModel = nextModel;
        }
    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testJumpToChild(StateManager sm) {
        C model = modelSupplier(sm);
        SymbolicModel rootModel = model.symbolicCopy();
        model.add(mockConstraint());
        SymbolicModel child1 = model.symbolicCopy();
        model.add(mockConstraint());
        SymbolicModel child2 = model.symbolicCopy();

        assertDoesNotThrow(() -> model.jumpToChild(child2));
        assertThrowsExactly(NotAChildModelException.class, () -> model.jumpToChild(child1));
        assertThrowsExactly(NotAChildModelException.class, () -> model.jumpToChild(rootModel));
        assertDoesNotThrow(() -> model.jumpToChild(child2));

        model.jumpTo(rootModel);
        assertDoesNotThrow(() -> model.jumpToChild(child1));
        model.jumpTo(rootModel);
        assertDoesNotThrow(() -> model.jumpToChild(child2));
    }

}
