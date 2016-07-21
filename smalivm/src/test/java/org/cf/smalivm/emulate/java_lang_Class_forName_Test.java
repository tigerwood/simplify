package org.cf.smalivm.emulate;

import org.cf.smalivm.SideEffect;
import org.cf.smalivm.VirtualException;
import org.cf.smalivm.VirtualMachine;
import org.cf.smalivm.configuration.Configuration;
import org.cf.smalivm.context.ExecutionContext;
import org.cf.smalivm.context.HeapItem;
import org.cf.smalivm.context.MethodState;
import org.cf.smalivm.dex.CommonTypes;
import org.cf.smalivm.dex.SmaliClassLoader;
import org.cf.smalivm.type.ClassManager;
import org.cf.smalivm.type.UnknownValue;
import org.cf.smalivm.type.VirtualClass;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class java_lang_Class_forName_Test {

    private static final Class<?> OBJECT_CLASS = Object.class;
    private static final Class<?> STRING_CLASS = String.class;
    private VirtualMachine vm;
    private ClassManager classManager;
    private SmaliClassLoader classLoader;
    private ExecutionContextMethod method;
    private MethodState mState;
    private ExecutionContext context;
    private Configuration configuration;

    @Before
    public void setUp() throws ClassNotFoundException {
        vm = mock(VirtualMachine.class);
        classManager = mock(ClassManager.class);
        when(vm.getClassManager()).thenReturn(classManager);

        classLoader = mock(SmaliClassLoader.class);
        when(vm.getClassLoader()).thenReturn(classLoader);

        configuration = mock(Configuration.class);
        when(vm.getConfiguration()).thenReturn(configuration);

        mState = mock(MethodState.class);
        context = mock(ExecutionContext.class);
        when(context.getMethodState()).thenReturn(mState);

        method = new java_lang_Class_forName();
    }

    @Test
    public void existentSafeClassNameReturnsRealClassAndHasNoSideEffects() throws Exception {
        String className = "Ljava/lang/String;"; // genuinely safe to load
        SideEffect.Level level = SideEffect.Level.NONE;
        setupClass(className, true, level);

        String binaryClassName = "java.lang.String";
        HeapItem item = new HeapItem(binaryClassName, CommonTypes.STRING);
        when(mState.peekParameter(0)).thenReturn(item);

        method.execute(vm, context);

        verify(mState, times(1)).assignReturnRegister(eq(STRING_CLASS), eq(CommonTypes.CLASS));
        assertEquals(level, method.getSideEffectLevel());
    }

    @Test
    public void unsafeClassNameReturnsClassAndHasNoSideEffects() throws Exception {
        String className = "Lunsafe/Class;";
        SideEffect.Level level = SideEffect.Level.NONE;
        VirtualClass virtualClass = setupClass(className, false, level);

        String binaryClassName = "unsafe.Class";
        doReturn(OBJECT_CLASS).when(classLoader).loadClass(binaryClassName);
        HeapItem item = new HeapItem(binaryClassName, CommonTypes.STRING);
        when(mState.peekParameter(0)).thenReturn(item);

        method.execute(vm, context);

        verify(mState, times(1)).assignReturnRegister(eq(OBJECT_CLASS), eq(CommonTypes.CLASS));
        verify(context, times(1)).staticallyInitializeClassIfNecessary(virtualClass);
        assertEquals(level, method.getSideEffectLevel());
    }

    @Test
    public void testStrongSideEffectsClassNameReturnsClassAndHasStrongSideEffects() throws Exception {
        String className = "Lstrong/Class;";
        SideEffect.Level level = SideEffect.Level.STRONG;
        VirtualClass virtualClass = setupClass(className, false, level);

        String binaryClassName = "strong.Class";
        doReturn(OBJECT_CLASS).when(classLoader).loadClass(binaryClassName);
        HeapItem item = new HeapItem(binaryClassName, CommonTypes.STRING);
        when(mState.peekParameter(0)).thenReturn(item);

        method.execute(vm, context);

        verify(mState, times(1)).assignReturnRegister(eq(OBJECT_CLASS), eq(CommonTypes.CLASS));
        verify(context, times(1)).staticallyInitializeClassIfNecessary(virtualClass);
        assertEquals(level, method.getSideEffectLevel());
    }

    @Test
    public void testUnknownClassNameThrowsExceptionAndAssignsNothing() throws Exception {
        String className = "Lunknown/Class;";
        SideEffect.Level level = SideEffect.Level.NONE;
        VirtualClass virtualClass = setupClass(className, false, level);

        String binaryClassName = "unknown.Class";
        HeapItem item = new HeapItem(binaryClassName, CommonTypes.STRING);
        when(mState.peekParameter(0)).thenReturn(item);

        when(classManager.getVirtualClass(className)).thenThrow(new RuntimeException());

        method.execute(vm, context);

        Set<VirtualException> expectedExceptions = new HashSet<VirtualException>();
        expectedExceptions.add(new VirtualException(ClassNotFoundException.class, binaryClassName));
        assertEquals(expectedExceptions, method.getExceptions());
        verify(mState, times(0)).assignReturnRegister(any(UnknownValue.class), eq(CommonTypes.CLASS));
        assertEquals(SideEffect.Level.NONE, method.getSideEffectLevel());
    }

    private VirtualClass setupClass(String className, boolean isSafe, SideEffect.Level level) {
        VirtualClass virtualClass = mock(VirtualClass.class);
        when(classManager.getVirtualClass(className)).thenReturn(virtualClass);
        when(virtualClass.getName()).thenReturn(className);
        when(configuration.isSafe(className)).thenReturn(isSafe);
        when(context.getClassSideEffectLevel(virtualClass)).thenReturn(level);

        return virtualClass;
    }

}
